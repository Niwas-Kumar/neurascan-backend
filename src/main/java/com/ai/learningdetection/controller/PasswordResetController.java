package com.ai.learningdetection.controller;

import com.ai.learningdetection.dto.ApiResponse;
import com.ai.learningdetection.service.PasswordResetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public endpoints for password reset flow.
 * All 3 endpoints are under /api/auth/** which is already public
 * in SecurityConfig — no changes to SecurityConfig needed.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    // ── Inner DTOs ────────────────────────────────────────────

    @Data
    static class ForgotPasswordRequest {
        @Email(message = "Valid email is required")
        @NotBlank(message = "Email is required")
        private String email;
    }

    @Data
    static class ResetPasswordRequest {
        @NotBlank(message = "Token is required")
        private String token;

        @NotBlank(message = "New password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        private String newPassword;
    }

    // ── Endpoints ────────────────────────────────────────────

    /**
     * POST /api/auth/forgot-password
     * Sends a reset link to the email if account exists.
     * Always returns 200 for security (don't reveal if email exists).
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {

        passwordResetService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(null,
                "If an account with that email exists, a reset link has been sent."));
    }

    /**
     * GET /api/auth/verify-reset-token?token=UUID
     * Returns 200 if valid, 400 if expired/invalid.
     * Called by the frontend before showing the reset form.
     */
    @GetMapping("/verify-reset-token")
    public ResponseEntity<ApiResponse<Void>> verifyToken(@RequestParam String token) {
        boolean valid = passwordResetService.verifyToken(token);
        if (!valid) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("This reset link is invalid or has expired."));
        }
        return ResponseEntity.ok(ApiResponse.success(null, "Token is valid"));
    }

    /**
     * POST /api/auth/reset-password
     * Resets the password using a valid token.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success(null, "Password reset successfully."));
    }
}
