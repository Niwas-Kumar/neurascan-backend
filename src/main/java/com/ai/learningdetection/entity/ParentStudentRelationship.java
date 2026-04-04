package com.ai.learningdetection.entity;

import lombok.*;
import java.time.Instant;

/**
 * Persistent relationship between a parent and student.
 * Stored in Firestore collection "parent_student_relationships".
 *
 * This replaces the session-dependent studentId field in Parent entity,
 * providing persistent connections that survive logout/login cycles.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParentStudentRelationship {

    private String id;

    private String parentId;

    private String studentId;

    /**
     * Verification status of the connection.
     * PENDING - Connection requested but not verified
     * VERIFIED - OTP or email verification completed
     * REJECTED - Connection was rejected by verification process
     */
    @Builder.Default
    private String verificationStatus = "PENDING";

    /**
     * ISO timestamp when the connection was created.
     */
    private String createdAt;

    /**
     * ISO timestamp when the connection was verified.
     */
    private String verifiedAt;

    /**
     * ISO timestamp when the connection was disconnected (soft delete).
     * If null, the connection is active.
     */
    private String disconnectedAt;

    /**
     * Reason for disconnection if applicable.
     */
    private String disconnectedReason;

    /**
     * IP address used when creating the connection (for audit).
     */
    private String createdFromIp;

    /**
     * Number of verification attempts made.
     */
    @Builder.Default
    private int verificationAttempts = 0;

    /**
     * Whether this is the primary/default student for the parent.
     */
    @Builder.Default
    private boolean isPrimary = false;

    // Denormalized fields for faster queries (avoid joins)
    private String studentName;
    private String studentClassName;
    private String studentRollNumber;

    /**
     * Check if the relationship is currently active.
     */
    public boolean isActive() {
        return disconnectedAt == null && "VERIFIED".equals(verificationStatus);
    }

    /**
     * Check if verification is still pending.
     */
    public boolean isPending() {
        return "PENDING".equals(verificationStatus);
    }
}
