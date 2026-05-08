package edu.cit.hopista.campusclinic.features.auth.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;

import edu.cit.hopista.campusclinic.features.auth.dto.request.ForgotPasswordRequest;
import edu.cit.hopista.campusclinic.features.auth.dto.request.LoginRequest;
import edu.cit.hopista.campusclinic.features.auth.dto.request.RegisterRequest;
import edu.cit.hopista.campusclinic.features.auth.dto.request.ResetPasswordRequest;
import edu.cit.hopista.campusclinic.features.auth.dto.response.AuthResponse;
import edu.cit.hopista.campusclinic.features.user.dto.response.UserResponse;
import edu.cit.hopista.campusclinic.shared.security.UserPrincipal;
import edu.cit.hopista.campusclinic.features.auth.service.AuthService;
import edu.cit.hopista.campusclinic.features.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    /** AC-1: Register with email/password */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /** AC-2: Login with email/password → JWT */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * POST /api/v1/auth/logout
     * JWT is stateless — client must discard the token.
     * This endpoint exists so the mobile app can signal logout and clear server-side state if needed.
     */
    @PostMapping("/logout")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(authorization);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully. Please discard your token."));
    }

    /**
     * GET /api/v1/auth/me
     * Returns the currently authenticated user's profile.
     */
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<UserResponse> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(userService.getUserById(principal.getId()));
    }

    /**
     * POST /api/v1/auth/forgot-password
     * Sends a password-reset link to the given email address.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        // Always return 200 to prevent user enumeration
        return ResponseEntity.ok(Map.of("message",
                "If that email is registered, a reset link has been sent."));
    }

    /**
     * POST /api/v1/auth/reset-password
     * Validates the reset token and updates the user's password.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(Map.of("message", "Password updated successfully. Please log in."));
    }
}

