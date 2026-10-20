package com.example.islab.controller;

import com.example.islab.dto.LoginRequest;
import com.example.islab.dto.LoginResponse;
import com.example.islab.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")

public class AuthController {

	private final AuthenticationManager authManager;
    private final JwtService jwtService;

    public AuthController(AuthenticationManager authManager, JwtService jwtService) {
        this.authManager = authManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        // Parameterized via JPA; BCrypt compared inside DaoAuthenticationProvider
        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.login(), req.password()));

        String token = jwtService.generateToken(auth.getName());
        return ResponseEntity.ok(new LoginResponse(token, "Bearer", jwtService.getExpirationMs()));
    }
}
