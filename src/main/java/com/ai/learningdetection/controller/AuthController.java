package com.ai.learningdetection.controller;

import com.ai.learningdetection.dto.ApiResponse;
import com.ai.learningdetection.dto.AuthDTOs;
import com.ai.learningdetection.security.IdentifiablePrincipal;
import com.ai.learningdetection.service.AuthService;
import com.ai.learningdetection.service.EmailService;
import com.ai.learningdetection.service.OTPService;
import com.ai.learningdetection.service.FirebaseLoginService;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({ "/api/auth", "/auth" })
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final FirebaseLoginService firebaseLoginService;
    private final EmailService emailService;
    private final OTPService otpService;
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

        AuthDTOs.AuthResponse response = firebaseLoginService.loginWithGoogle(request.getIdToken(), request.getRole(),
                request.getPicture());
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful via Google"));
    }

    // ============================================================
    // OTP VERIFICATION (PRE-REGISTRATION)
    // ============================================================

    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<String>> sendOtp(
            @Valid @RequestBody AuthDTOs.SendOtpRequest request) {
        try {
            // Generate OTP
            String otp = otpService.generateOTP(request.getEmail());
            
            // Send email
            boolean emailSent = emailService.sendOtpEmail(request.getEmail(), otp);
            
            if (emailSent) {
                log.info("✅ OTP sent to: {}", request.getEmail());
                return ResponseEntity.ok(ApiResponse.success(null, "OTP sent successfully to your email"));
            } else {
                log.warn("⚠️  OTP generated but email failed for: {}", request.getEmail());
                return ResponseEntity.ok(ApiResponse.success(null, 
                    "OTP generated. Check email or use it from Firestore verification_tokens collection."));
            }
        } catch (Exception e) {
            log.error("❌ OTP generation failed: {}", e.getMessage());
            return ResponseEntity.status(500).body(ApiResponse.error("Failed to generate OTP"));
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<Boolean>> verifyOtp(
            @Valid @RequestBody AuthDTOs.VerifyOtpRequest request) {
        try {
            boolean isValid = otpService.verifyOTP(request.getEmail(), request.getOtp());
            if (isValid) {
                // ✅ Do NOT consume OTP here - it will be consumed after successful registration
                // This allows the registration endpoint to verify the same OTP without conflicts
                return ResponseEntity.ok(ApiResponse.success(true, "OTP verified successfully"));
            } else {
                return ResponseEntity.badRequest().body(ApiResponse.error("Invalid or expired OTP"));
            }
        } catch (Exception e) {
            log.error("❌ OTP verification failed: {}", e.getMessage());
            return ResponseEntity.status(500).body(ApiResponse.error("OTP verification failed"));
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
