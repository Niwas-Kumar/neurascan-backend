package com.ai.learningdetection.controller;

import com.ai.learningdetection.dto.ApiResponse;
import com.ai.learningdetection.dto.ParentStudentDTOs.*;
import com.ai.learningdetection.service.ParentStudentService;
import com.ai.learningdetection.security.IdentifiablePrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

/**
 * REST Controller for parent-student connection management.
 *
 * Provides endpoints for:
 * - Validating student IDs before connection
 * - Initiating student connections with OTP verification
 * - Verifying connections with OTP
 * - Listing connected students
 * - Disconnecting from students
 * - Managing primary student selection
 */
@RestController
@RequestMapping("/api/parent/students")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('PARENT')")
public class ParentStudentController {

    private final ParentStudentService parentStudentService;

    // ════════════════════════════════════════════════════════════════
    // VALIDATE STUDENT ID
    // ════════════════════════════════════════════════════════════════

    /**
     * Validate a student ID before initiating connection.
     * Returns student details if valid.
     */
    @GetMapping("/validate/{studentId}")
    public ResponseEntity<ApiResponse<ValidateStudentResponse>> validateStudent(
            @PathVariable String studentId,
            Authentication auth
    ) {
        String parentId = ((IdentifiablePrincipal) auth.getPrincipal()).getId();
        log.info("Parent {} validating student ID: {}", parentId, studentId);

        ValidateStudentResponse response = parentStudentService.validateStudent(studentId, parentId);

        return ResponseEntity.ok(ApiResponse.<ValidateStudentResponse>builder()
                .success(response.isValid())
                .message(response.getMessage())
                .data(response)
                .build());
    }

    // ════════════════════════════════════════════════════════════════
    // INITIATE CONNECTION
    // ════════════════════════════════════════════════════════════════

    /**
     * Initiate a connection request with a student.
     * Sends OTP verification to parent's email.
     */
    @PostMapping("/connect")
    public ResponseEntity<ApiResponse<ConnectionInitiatedResponse>> connectStudent(
            @RequestBody ConnectStudentRequest request,
            Authentication auth,
            HttpServletRequest httpRequest
    ) {
        IdentifiablePrincipal principal = (IdentifiablePrincipal) auth.getPrincipal();
        String parentId = principal.getId();
        String parentEmail = principal.getUsername(); // email is stored as username

        String ipAddress = getClientIp(httpRequest);
        log.info("Parent {} initiating connection to student {} from IP {}", parentId, request.getStudentId(), ipAddress);

        try {
            ConnectionInitiatedResponse response = parentStudentService.initiateConnection(
                    parentId, parentEmail, request, ipAddress
            );

            return ResponseEntity.ok(ApiResponse.<ConnectionInitiatedResponse>builder()
                    .success(true)
                    .message(response.getMessage())
                    .data(response)
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.<ConnectionInitiatedResponse>builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    // ════════════════════════════════════════════════════════════════
    // VERIFY CONNECTION
    // ════════════════════════════════════════════════════════════════

    /**
     * Verify a pending connection with OTP.
     */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<ConnectionResultResponse>> verifyConnection(
            @RequestBody VerifyConnectionRequest request,
            Authentication auth
    ) {
        IdentifiablePrincipal principal = (IdentifiablePrincipal) auth.getPrincipal();
        String parentId = principal.getId();
        String parentEmail = principal.getUsername();

        log.info("Parent {} verifying connection {}", parentId, request.getRelationshipId());

        ConnectionResultResponse response = parentStudentService.verifyConnection(
                parentId, parentEmail, request
        );

        return ResponseEntity.ok(ApiResponse.<ConnectionResultResponse>builder()
                .success(response.isSuccess())
                .message(response.getMessage())
                .data(response)
                .build());
    }

    // ════════════════════════════════════════════════════════════════
    // GET CONNECTED STUDENTS
    // ════════════════════════════════════════════════════════════════

    /**
     * Get all connected students for the authenticated parent.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<ConnectedStudentsListResponse>> getConnectedStudents(
            Authentication auth
    ) {
        String parentId = ((IdentifiablePrincipal) auth.getPrincipal()).getId();
        log.info("Getting connected students for parent {}", parentId);

        ConnectedStudentsListResponse response = parentStudentService.getConnectedStudents(parentId);

        return ResponseEntity.ok(ApiResponse.<ConnectedStudentsListResponse>builder()
                .success(true)
                .message("Connected students retrieved successfully")
                .data(response)
                .build());
    }

    /**
     * Get the primary connected student.
     */
    @GetMapping("/primary")
    public ResponseEntity<ApiResponse<ConnectedStudentResponse>> getPrimaryStudent(
            Authentication auth
    ) {
        String parentId = ((IdentifiablePrincipal) auth.getPrincipal()).getId();

        ConnectedStudentResponse response = parentStudentService.getPrimaryStudent(parentId);

        if (response == null) {
            return ResponseEntity.ok(ApiResponse.<ConnectedStudentResponse>builder()
                    .success(false)
                    .message("No connected student found. Please connect to your child's account.")
                    .build());
        }

        return ResponseEntity.ok(ApiResponse.<ConnectedStudentResponse>builder()
                .success(true)
                .message("Primary student retrieved")
                .data(response)
                .build());
    }

    // ════════════════════════════════════════════════════════════════
    // DISCONNECT
    // ════════════════════════════════════════════════════════════════

    /**
     * Disconnect from a student (soft delete).
     */
    @DeleteMapping("/{studentId}")
    public ResponseEntity<ApiResponse<ConnectionResultResponse>> disconnectStudent(
            @PathVariable String studentId,
            @RequestBody(required = false) DisconnectRequest request,
            Authentication auth
    ) {
        String parentId = ((IdentifiablePrincipal) auth.getPrincipal()).getId();
        log.info("Parent {} disconnecting from student {}", parentId, studentId);

        DisconnectRequest disconnectRequest = request != null ? request :
                DisconnectRequest.builder().studentId(studentId).build();
        disconnectRequest.setStudentId(studentId);

        ConnectionResultResponse response = parentStudentService.disconnectStudent(parentId, disconnectRequest);

        return ResponseEntity.ok(ApiResponse.<ConnectionResultResponse>builder()
                .success(response.isSuccess())
                .message(response.getMessage())
                .data(response)
                .build());
    }

    // ════════════════════════════════════════════════════════════════
    // SET PRIMARY STUDENT
    // ════════════════════════════════════════════════════════════════

    /**
     * Set a connected student as the primary student.
     */
    @PutMapping("/{studentId}/primary")
    public ResponseEntity<ApiResponse<ConnectionResultResponse>> setPrimaryStudent(
            @PathVariable String studentId,
            Authentication auth
    ) {
        String parentId = ((IdentifiablePrincipal) auth.getPrincipal()).getId();
        log.info("Parent {} setting student {} as primary", parentId, studentId);

        SetPrimaryStudentRequest request = SetPrimaryStudentRequest.builder()
                .studentId(studentId)
                .build();

        ConnectionResultResponse response = parentStudentService.setPrimaryStudent(parentId, request);

        return ResponseEntity.ok(ApiResponse.<ConnectionResultResponse>builder()
                .success(response.isSuccess())
                .message(response.getMessage())
                .data(response)
                .build());
    }

    // ════════════════════════════════════════════════════════════════
    // RESEND OTP
    // ════════════════════════════════════════════════════════════════

    /**
     * Resend verification OTP for a pending connection.
     */
    @PostMapping("/resend-otp/{relationshipId}")
    public ResponseEntity<ApiResponse<ConnectionInitiatedResponse>> resendOTP(
            @PathVariable String relationshipId,
            Authentication auth
    ) {
        IdentifiablePrincipal principal = (IdentifiablePrincipal) auth.getPrincipal();
        String parentId = principal.getId();
        String parentEmail = principal.getUsername();

        log.info("Parent {} resending OTP for relationship {}", parentId, relationshipId);

        try {
            ConnectionInitiatedResponse response = parentStudentService.resendVerificationOTP(
                    parentId, parentEmail, relationshipId
            );

            return ResponseEntity.ok(ApiResponse.<ConnectionInitiatedResponse>builder()
                    .success(true)
                    .message(response.getMessage())
                    .data(response)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.<ConnectionInitiatedResponse>builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    // ════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ════════════════════════════════════════════════════════════════

    /**
     * Extract client IP address from request.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
