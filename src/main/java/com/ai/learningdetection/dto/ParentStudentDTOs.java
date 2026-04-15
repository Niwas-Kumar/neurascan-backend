package com.ai.learningdetection.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.util.List;

/**
 * DTOs for Parent-Student connection management.
 */
public class ParentStudentDTOs {

    // ════════════════════════════════════════════════════════════════
    // REQUEST DTOs
    // ════════════════════════════════════════════════════════════════

    /**
     * Request to initiate a connection with a student.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConnectStudentRequest {
        private String studentId;
        private String verificationMethod; // "OTP" or "EMAIL"
    }

    /**
     * Request to verify a pending connection with OTP.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VerifyConnectionRequest {
        private String relationshipId;
        private String otp;
    }

    /**
     * Request to disconnect from a student.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DisconnectRequest {
        private String studentId;
        private String reason;
    }

    /**
     * Request to set a primary student.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SetPrimaryStudentRequest {
        private String studentId;
    }

    // ════════════════════════════════════════════════════════════════
    // RESPONSE DTOs
    // ════════════════════════════════════════════════════════════════

    /**
     * Response for a single connected student.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConnectedStudentResponse {
        private String relationshipId;
        private String studentId;
        private String studentName;
        private String studentClassName;
        private String studentRollNumber;
        private String teacherName;
        private String verificationStatus;
        private String connectedAt;
        @JsonProperty("isPrimary")
        private boolean isPrimary;
        private boolean canAccessData;
    }

    /**
     * Response listing all connected students for a parent.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConnectedStudentsListResponse {
        private List<ConnectedStudentResponse> students;
        private int totalConnections;
        private int activeConnections;
        private int pendingConnections;
    }

    /**
     * Response when initiating a connection.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConnectionInitiatedResponse {
        private String relationshipId;
        private String studentId;
        private String studentName;
        private String verificationMethod;
        private String message;
        private boolean requiresVerification;
    }

    /**
     * Response validating a student ID before connection.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ValidateStudentResponse {
        private boolean valid;
        private String studentId;
        private String studentName;
        private String className;
        private String teacherName;
        private String schoolName;
        private String message;
        private boolean alreadyConnected;
    }

    /**
     * Response for connection/verification result.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConnectionResultResponse {
        private boolean success;
        private String message;
        private ConnectedStudentResponse student;
    }
}
