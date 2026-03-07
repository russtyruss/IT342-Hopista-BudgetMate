package edu.cit.delacruz.campusclinic.service;

import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.cit.delacruz.campusclinic.dto.request.ForgotPasswordRequest;
import edu.cit.delacruz.campusclinic.dto.request.LoginRequest;
import edu.cit.delacruz.campusclinic.dto.request.RegisterRequest;
import edu.cit.delacruz.campusclinic.dto.request.ResetPasswordRequest;
import edu.cit.delacruz.campusclinic.dto.response.AuthResponse;
import edu.cit.delacruz.campusclinic.entity.Role;
import edu.cit.delacruz.campusclinic.entity.User;
import edu.cit.delacruz.campusclinic.exception.BadRequestException;
import edu.cit.delacruz.campusclinic.exception.ResourceNotFoundException;
import edu.cit.delacruz.campusclinic.repository.UserRepository;
import edu.cit.delacruz.campusclinic.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final EmailService emailService;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email address is already in use.");
        }

        Set<Role> roles = new HashSet<>();
        roles.add(Role.USER);

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .provider(User.AuthProvider.LOCAL)
                .emailVerified(false)
                .roles(roles)
                .build();

        user = userRepository.save(user);

        // Send welcome email asynchronously
        emailService.sendWelcomeEmail(user.getEmail(), user.getName());

        String token = tokenProvider.generateTokenFromUserId(user.getId(), user.getEmail());
        return AuthResponse.builder()
                .accessToken(token)
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(Role.USER.name())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String token = tokenProvider.generateToken(authentication);
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_USER")
                .replace("ROLE_", "");

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("User not found"));

        return AuthResponse.builder()
                .accessToken(token)
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(role)
                .build();
    }

    /**
     * AC: Forgot Password – sends a password-reset link via email.
     * Always returns success to prevent user-enumeration attacks.
     */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            String resetToken = tokenProvider.generatePasswordResetToken(user.getId(), user.getEmail());
            String resetLink = frontendUrl + "/reset-password?token=" + resetToken;
            emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
        });
    }

    /**
     * AC: Reset Password – validates the reset token and updates the password.
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!tokenProvider.validateToken(request.getToken())) {
            throw new BadRequestException("Invalid or expired reset token.");
        }
        if (!tokenProvider.isPasswordResetToken(request.getToken())) {
            throw new BadRequestException("Invalid reset token type.");
        }
        Long userId = tokenProvider.getUserIdFromToken(request.getToken());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }
}
