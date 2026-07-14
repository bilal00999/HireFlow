package com.example.demo.auth;

import com.example.demo.auth.dto.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register/candidate")
    public AuthResponse registerCandidate(@Valid @RequestBody RegisterCandidateRequest req) {
        return authService.registerCandidate(req);
    }

    @PostMapping("/register/company")
    public AuthResponse registerCompany(@Valid @RequestBody RegisterCompanyRequest req) {
        return authService.registerCompany(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }
}
