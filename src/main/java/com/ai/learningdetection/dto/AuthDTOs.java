package com.ai.learningdetection.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

// ============================================================
// AUTH REQUEST DTOs
// ============================================================

public class AuthDTOs {

    @Data
    public static class TeacherRegisterRequest {
        @NotBlank(message = "Name is required")
        private String name;

        @Email(message = "Valid email is required")
        @NotBlank(message = "Email is required")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        private String password;

        @NotBlank(message = "School name is required")
        private String school;

        @NotBlank(message = "OTP verification code is required")
        private String otp;
    }

    @Data
    public static class ParentRegisterRequest {
        @NotBlank(message = "Name is required")
        private String name;

        @Email(message = "Valid email is required")
        @NotBlank(message = "Email is required")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        private String password;

        private String studentId;

        @NotBlank(message = "OTP verification code is required")
        private String otp;
    }


    @Data
    public static class LoginRequest {
        @Email(message = "Valid email is required")
        @NotBlank(message = "Email is required")
        private String email;

        @NotBlank(message = "Password is required")
        private String password;
    }

    @Data
    public static class FirebaseLoginRequest {
        @NotBlank(message = "ID Token is required")
        private String idToken;
        private String role; // optional: "teacher" or "parent"
        private String picture; // fallback picture from frontend
    }

    @Data
    public static class SendOtpRequest {
        @Email(message = "Valid email is required")
        @NotBlank(message = "Email is required")
        private String email;
    }

    @Data
    public static class VerifyOtpRequest {
        @Email(message = "Valid email is required")
        @NotBlank(message = "Email is required")
        private String email;

        @NotBlank(message = "OTP is required")
        private String otp;
    }

    @Data
    public static class ParentLinkRequest {
        @NotBlank(message = "rollNumber is required")
        private String rollNumber;
        @NotBlank(message = "schoolId is required")
        private String schoolId;
    }

    @Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class AuthResponse {
        private String jwtToken;
        private String userRole;
        private String userId;
        private String userName;
        private String userEmail;
        private String picture;
        private String message;
    }
}

