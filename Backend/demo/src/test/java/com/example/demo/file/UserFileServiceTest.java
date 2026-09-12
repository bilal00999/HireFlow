package com.example.demo.file;

import com.example.demo.auth.User;
import com.example.demo.auth.UserRepository;
import com.example.demo.common.BadRequestException;
import com.example.demo.common.FileStorageException;
import com.example.demo.common.ResourceNotFoundException;
import com.example.demo.file.CloudinaryStorageService.StoredFile;
import com.example.demo.file.dto.ProfileImageResponse;
import com.example.demo.file.dto.ResumeResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserFileService}. Cloudinary and the repository are
 * mocked, so no real upload, delete, network call, or database write happens.
 */
class UserFileServiceTest {

    private CloudinaryStorageService storage;
    private UserRepository userRepository;
    private UserFileService service;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        storage = mock(CloudinaryStorageService.class);
        userRepository = mock(UserRepository.class);
        service = new UserFileService(storage, userRepository);

        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
        user.setEmail("candidate@example.com");
        user.setFullName("Test Candidate");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // Authenticate as this candidate (principal = user id string, as the JWT filter sets it).
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId.toString(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_CANDIDATE"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockMultipartFile file(String name, String contentType, int bytes) {
        return new MockMultipartFile("file", name, contentType, new byte[bytes]);
    }

    // ---------------------------------------------------------------- validation

    @Test
    void rejectsEmptyProfileImage() {
        assertThatThrownBy(() -> service.uploadProfileImage(
                new MockMultipartFile("file", "a.png", "image/png", new byte[0])))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsOversizedProfileImage() {
        assertThatThrownBy(() -> service.uploadProfileImage(file("big.png", "image/png", 3 * 1024 * 1024)))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsUnsupportedImageType() {
        assertThatThrownBy(() -> service.uploadProfileImage(file("a.gif", "image/gif", 1024)))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsOversizedResume() {
        assertThatThrownBy(() -> service.uploadResume(file("cv.pdf", "application/pdf", 6 * 1024 * 1024)))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void rejectsUnsupportedResumeType() {
        assertThatThrownBy(() -> service.uploadResume(file("cv.txt", "text/plain", 1024)))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(storage);
    }

    // ---------------------------------------------------------------- happy paths

    @Test
    void uploadsProfileImageAndPersistsMetadata() {
        when(storage.uploadProfileImage(eq(userId), any()))
                .thenReturn(new StoredFile("https://cdn/img.png", "hireflow/profile-images/" + userId + "/abc"));

        ProfileImageResponse resp = service.uploadProfileImage(file("me.png", "image/png", 1024));

        assertThat(resp.url()).isEqualTo("https://cdn/img.png");
        assertThat(resp.fileName()).isEqualTo("me.png");
        assertThat(resp.contentType()).isEqualTo("image/png");
        assertThat(user.getProfileImagePublicId()).isEqualTo("hireflow/profile-images/" + userId + "/abc");
        assertThat(user.getProfileImageUrl()).isEqualTo("https://cdn/img.png");
        verify(userRepository).save(user);
        verify(storage, never()).delete(any(), any(), any());
    }

    @Test
    void uploadsResumeAndReturnsBackendDownloadUrl() {
        when(storage.uploadResume(eq(userId), any()))
                .thenReturn(new StoredFile("https://cdn/cv", "hireflow/resumes/" + userId + "/xyz"));

        ResumeResponse resp = service.uploadResume(file("cv.pdf", "application/pdf", 2048));

        assertThat(resp.fileName()).isEqualTo("cv.pdf");
        assertThat(resp.downloadUrl()).isEqualTo("/api/v1/resumes/download");
        assertThat(user.getResumePublicId()).isEqualTo("hireflow/resumes/" + userId + "/xyz");
        verify(userRepository).save(user);
        verify(storage, never()).delete(any(), any(), any());
    }

    // ---------------------------------------------------------------- replace ordering

    @Test
    void replacingResumeUploadsAndSavesBeforeDeletingOld() {
        user.setResumePublicId("old-resume-id");
        when(storage.uploadResume(eq(userId), any()))
                .thenReturn(new StoredFile("https://cdn/new", "new-resume-id"));

        service.uploadResume(file("new.pdf", "application/pdf", 2048));

        // Order matters: never delete the old file before the new one is uploaded AND saved.
        InOrder order = inOrder(storage, userRepository);
        order.verify(storage).uploadResume(eq(userId), any());
        order.verify(userRepository).save(user);
        order.verify(storage).delete("old-resume-id", "raw", "authenticated");
    }

    @Test
    void replacedFileDeletionFailureDoesNotFailUpload() {
        user.setProfileImagePublicId("old-image-id");
        when(storage.uploadProfileImage(eq(userId), any()))
                .thenReturn(new StoredFile("https://cdn/new.png", "new-image-id"));
        doThrow(new FileStorageException("delete boom"))
                .when(storage).delete("old-image-id", "image", "upload");

        // The replace still succeeds even though deleting the old file failed.
        ProfileImageResponse resp = service.uploadProfileImage(file("new.png", "image/png", 1024));
        assertThat(resp.url()).isEqualTo("https://cdn/new.png");
    }

    // ---------------------------------------------------------------- orphan cleanup

    @Test
    void cleansUpUploadedFileWhenDbSaveFails() {
        when(storage.uploadResume(eq(userId), any()))
                .thenReturn(new StoredFile("https://cdn/new", "new-resume-id"));
        when(userRepository.save(any(User.class))).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> service.uploadResume(file("cv.pdf", "application/pdf", 2048)))
                .isInstanceOf(FileStorageException.class);

        // The just-uploaded file must be removed so it isn't orphaned.
        verify(storage).delete("new-resume-id", "raw", "authenticated");
    }

    // ---------------------------------------------------------------- read / delete

    @Test
    void currentResumeThrowsWhenNone() {
        assertThatThrownBy(() -> service.currentResume()).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteResumeRemovesFileThenClearsMetadata() {
        user.setResumePublicId("resume-id");
        user.setResumeUrl("https://cdn/cv");
        user.setResumeName("cv.pdf");

        service.deleteResume();

        InOrder order = inOrder(storage, userRepository);
        order.verify(storage).delete("resume-id", "raw", "authenticated");
        order.verify(userRepository).save(user);
        assertThat(user.getResumePublicId()).isNull();
        assertThat(user.getResumeUrl()).isNull();
        assertThat(user.getResumeName()).isNull();
    }

    @Test
    void deleteProfileImageThrowsWhenNone() {
        assertThatThrownBy(() -> service.deleteProfileImage()).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(storage);
    }
}
