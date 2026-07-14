package com.example.demo.auth;

import com.example.demo.auth.dto.*;
import com.example.demo.common.BadRequestException;
import com.example.demo.common.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository,
                       CompanyRepository companyRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public AuthResponse registerCandidate(RegisterCandidateRequest req) {
        if (userRepository.existsByEmail(req.email()) || companyRepository.existsByEmail(req.email())) {
            throw new BadRequestException("Email is already registered");
        }
        User user = new User();
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setFullName(req.fullName());
        user.setPhone(req.phone());
        user = userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId().toString(), user.getEmail(), "CANDIDATE");
        return new AuthResponse(token, user.getId().toString(), user.getEmail(), user.getFullName(), "CANDIDATE");
    }

    public AuthResponse registerCompany(RegisterCompanyRequest req) {
        if (companyRepository.existsByEmail(req.email()) || userRepository.existsByEmail(req.email())) {
            throw new BadRequestException("Email is already registered");
        }
        Company company = new Company();
        company.setEmail(req.email());
        company.setPasswordHash(passwordEncoder.encode(req.password()));
        company.setName(req.companyName());
        company.setIndustry(req.industry());
        company = companyRepository.save(company);

        String token = jwtUtil.generateToken(company.getId().toString(), company.getEmail(), "HR");
        return new AuthResponse(token, company.getId().toString(), company.getEmail(), company.getName(), "HR");
    }

    public AuthResponse login(LoginRequest req) {
        // A candidate and a company can never share an email (enforced at registration),
        // so at most one of these lookups succeeds.
        User user = userRepository.findByEmail(req.email()).orElse(null);
        if (user != null) {
            if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
                throw new BadRequestException("Invalid email or password");
            }
            String token = jwtUtil.generateToken(user.getId().toString(), user.getEmail(), "CANDIDATE");
            return new AuthResponse(token, user.getId().toString(), user.getEmail(), user.getFullName(), "CANDIDATE");
        }

        Company company = companyRepository.findByEmail(req.email()).orElse(null);
        if (company != null) {
            if (!passwordEncoder.matches(req.password(), company.getPasswordHash())) {
                throw new BadRequestException("Invalid email or password");
            }
            String token = jwtUtil.generateToken(company.getId().toString(), company.getEmail(), "HR");
            return new AuthResponse(token, company.getId().toString(), company.getEmail(), company.getName(), "HR");
        }

        throw new BadRequestException("Invalid email or password");
    }
}
