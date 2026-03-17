package com.ai.learningdetection.controller;

import com.ai.learningdetection.dto.ApiResponse;
import com.ai.learningdetection.dto.AuthDTOs;
import com.ai.learningdetection.security.IdentifiablePrincipal;
import com.ai.learningdetection.service.AuthService;
import com.ai.learningdetection.service.FirebaseLoginService;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/auth", "/auth"})
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final FirebaseLoginService firebaseLoginService;
    private final com.ai.learningdetection.service.EmailVerificationService emailVerificationService;
    private final Firestore firestore;

    // ============================================================
    // TEACHER
    // ============================================================

    /**
     * POST /api/auth/teacher/register
     * Register a new teacher account.
     */
    @PostMapping("/teacher/register")
    public ResponseEntity<ApiResponse<AuthDTOs.AuthResponse>> registerTeacher(
            @Valid @RequestBody AuthDTOs.TeacherRegisterRequest request) {

        AuthDTOs.AuthResponse response = authService.registerTeacher(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Teacher registered successfully"));
    }

    /**
     * POST /api/auth/teacher/login
     * Authenticate a teacher and return JWT.
     */
    @PostMapping("/teacher/login")
    public ResponseEntity<ApiResponse<AuthDTOs.AuthResponse>> loginTeacher(
            @Valid @RequestBody AuthDTOs.LoginRequest request) {

        AuthDTOs.AuthResponse response = authService.loginTeacher(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    // ============================================================
    // PARENT
    // ============================================================

    /**
     * POST /api/auth/parent/register
     * Register a new parent account.
     */
    @PostMapping("/parent/register")
    public ResponseEntity<ApiResponse<AuthDTOs.AuthResponse>> registerParent(
            @Valid @RequestBody AuthDTOs.ParentRegisterRequest request) {

        AuthDTOs.AuthResponse response = authService.registerParent(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Parent registered successfully"));
    }

    /**
     * POST /api/auth/parent/login
     * Authenticate a parent and return JWT.
     */
    @PostMapping("/parent/login")
    public ResponseEntity<ApiResponse<AuthDTOs.AuthResponse>> loginParent(
            @Valid @RequestBody AuthDTOs.LoginRequest request) {

        AuthDTOs.AuthResponse response = authService.loginParent(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    /**
     * POST /api/auth/firebase-login
     * Authenticate via Firebase Google ID Token.
     */
    @PostMapping("/firebase-login")
    public ResponseEntity<ApiResponse<AuthDTOs.AuthResponse>> loginWithFirebase(
            @Valid @RequestBody AuthDTOs.FirebaseLoginRequest request) {

        AuthDTOs.AuthResponse response = firebaseLoginService.loginWithGoogle(request.getIdToken(), request.getRole(), request.getPicture());
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful via Google"));
    }

    // ============================================================
    // OTP VERIFICATION (PRE-REGISTRATION)
    // ============================================================

    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<String>> sendOtp(
            @Valid @RequestBody AuthDTOs.SendOtpRequest request) {
        emailVerificationService.sendOtp(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success(null, "6-digit OTP sent successfully"));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<Boolean>> verifyOtp(
            @Valid @RequestBody AuthDTOs.VerifyOtpRequest request) {
        boolean isValid = emailVerificationService.verifyOtp(request.getEmail(), request.getOtp());
        if (isValid) {
            return ResponseEntity.ok(ApiResponse.success(true, "OTP verified successfully"));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid or expired OTP"));
        }
    }

    @PostMapping("/parent/link-child")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ApiResponse<String>> linkParentToStudent(
            @Valid @RequestBody AuthDTOs.ParentLinkRequest request,
            @AuthenticationPrincipal IdentifiablePrincipal principal) {
        // Find student by roll number and schoolId
        try {
            QuerySnapshot query = firestore.collection("students")
                    .whereEqualTo("rollNumber", request.getRollNumber())
                    .whereEqualTo("schoolId", request.getSchoolId())
                    .whereEqualTo("isActive", true)
                    .limit(1)
                    .get().get();

            if (query.isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Student roll number not found"));
            }

            DocumentSnapshot studentDoc = query.getDocuments().get(0);
            String studentId = studentDoc.getId();

            // assign parent/student connection
            firestore.collection("parents").document(principal.getId()).update("studentId", studentId).get();
            firestore.collection("students").document(studentId).update("parentUid", principal.getId()).get();

            return ResponseEntity.ok(ApiResponse.success(studentId, "Parent linked to student"));
        } catch (Exception ex) {
            throw new RuntimeException("Failed to link parent with student", ex);
        }
    }
}

