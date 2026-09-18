package com.example.demo.user;

import com.example.demo.auth.CompanyRepository;
import com.example.demo.auth.User;
import com.example.demo.auth.UserRepository;
import com.example.demo.common.BadRequestException;
import com.example.demo.common.ResourceNotFoundException;
import com.example.demo.user.dto.ChangePasswordRequest;
import com.example.demo.user.dto.MeResponse;
import com.example.demo.user.dto.UpdateProfileRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserProfileService}. The repositories and the password
 * encoder are mocked, so no real database write or hashing happens. The
 * authenticated candidate is simulated via {@link SecurityContextHolder}, matching
 * how {@code JwtAuthFilter} sets the principal (the user id string).
 */
class UserProfileServiceTest {

    private UserRepository userRepository;
    private CompanyRepository companyRepository;
    private PasswordEncoder passwordEncoder;
    private UserProfileService service;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        companyRepository = mock(CompanyRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new UserProfileService(userRepository, companyRepository, passwordEncoder);

        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
        user.setEmail("candidate@example.com");
        user.setFullName("Test Candidate");
        user.setPasswordHash("hashed-old");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId.toString(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_CANDIDATE"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------- read

    @Test
    void currentProfileReturnsIdentityAndNullFilesWhenNoneUploaded() {
        MeResponse me = service.currentProfile();

        assertThat(me.id()).isEqualTo(userId.toString());
        assertThat(me.email()).isEqualTo("candidate@example.com");
        assertThat(me.fullName()).isEqualTo("Test Candidate");
        assertThat(me.role()).isEqualTo("CANDIDATE");
        assertThat(me.profileImage()).isNull();
        assertThat(me.resume()).isNull();
    }

    @Test
    void currentProfileIncludesImageAndResumeMetadataWhenPresent() {
        user.setProfileImagePublicId("img-id");
        user.setProfileImageUrl("https://cdn/img.png");
        user.setProfileImageName("me.png");
        user.setResumePublicId("cv-id");
        user.setResumeName("cv.pdf");
        user.setResumeUpdatedAt(LocalDateTime.now());

        MeResponse me = service.currentProfile();

        assertThat(me.profileImage()).isNotNull();
        assertThat(me.profileImage().url()).isEqualTo("https://cdn/img.png");
        assertThat(me.resume()).isNotNull();
        assertThat(me.resume().fileName()).isEqualTo("cv.pdf");
        // Never leaks the private Cloudinary URL — only the backend download path.
        assertThat(me.resume().downloadUrl()).isEqualTo("/api/v1/resumes/download");
    }

    @Test
    void currentProfileThrowsWhenUserMissing() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.currentProfile()).isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------- update identity

    @Test
    void updateProfileUpdatesFieldsAndTrims() {
        UpdateProfileRequest req = new UpdateProfileRequest(
                "  New Name  ", "candidate@example.com", "  0300-1234567 ", "   ");

        MeResponse me = service.updateProfile(req);

        assertThat(user.getFullName()).isEqualTo("New Name");
        assertThat(user.getPhone()).isEqualTo("0300-1234567");
        assertThat(user.getLinkedinUrl()).isNull(); // blank -> cleared
        assertThat(me.fullName()).isEqualTo("New Name");
        verify(userRepository).save(user);
        // Email unchanged -> no duplicate check against the stores.
        verify(userRepository, never()).existsByEmail(anyString());
        verify(companyRepository, never()).existsByEmail(anyString());
    }

    @Test
    void updateProfileChangingToFreeEmailSucceeds() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(companyRepository.existsByEmail("new@example.com")).thenReturn(false);

        service.updateProfile(new UpdateProfileRequest("Test Candidate", "new@example.com", null, null));

        assertThat(user.getEmail()).isEqualTo("new@example.com");
        verify(userRepository).save(user);
    }

    @Test
    void updateProfileRejectsEmailTakenByAnotherUser() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.updateProfile(
                new UpdateProfileRequest("Test Candidate", "taken@example.com", null, null)))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfileRejectsEmailTakenByCompany() {
        when(userRepository.existsByEmail("hr@corp.com")).thenReturn(false);
        when(companyRepository.existsByEmail("hr@corp.com")).thenReturn(true);

        assertThatThrownBy(() -> service.updateProfile(
                new UpdateProfileRequest("Test Candidate", "hr@corp.com", null, null)))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- change password

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        when(passwordEncoder.matches("wrong", "hashed-old")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(new ChangePasswordRequest("wrong", "newsecret")))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePasswordEncodesAndSavesNewPassword() {
        when(passwordEncoder.matches("current", "hashed-old")).thenReturn(true);
        when(passwordEncoder.encode("newsecret")).thenReturn("hashed-new");

        service.changePassword(new ChangePasswordRequest("current", "newsecret"));

        assertThat(user.getPasswordHash()).isEqualTo("hashed-new");
        verify(userRepository).save(user);
    }
}
