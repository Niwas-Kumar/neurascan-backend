package com.ai.learningdetection.entity;

import com.google.cloud.firestore.annotation.Exclude;
import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import lombok.*;

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
@IgnoreExtraProperties
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
     * Field name in Firestore is "isPrimary", but Java Beans convention
     * expects setter to be setPrimary() for boolean fields starting with "is".
     */
    @Builder.Default
    private boolean primary = false;

    // Denormalized fields for faster queries (avoid joins)
    private String studentName;
    private String studentClassName;
    private String studentRollNumber;

    // ═══════════════════════════════════════════════════════════════════
    // FIRESTORE COMPATIBILITY
    // These handle "active", "pending", "isPrimary" fields from Firestore
    // documents without creating conflicting getters.
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Firestore compatibility: accepts "isPrimary" field from document.
     * Maps to the "primary" field.
     */
    public void setIsPrimary(Boolean isPrimary) {
        this.primary = isPrimary != null && isPrimary;
    }

    public void setIsPrimary(boolean isPrimary) {
        this.primary = isPrimary;
    }

    // Legacy compatibility: older docs may contain boolean helpers `pending`/`active`.
    public void setPending(Boolean ignoredPending) {
        // Intentionally ignored. Canonical source is verificationStatus.
    }

    public void setActive(Boolean ignoredActive) {
        // Intentionally ignored. Canonical source is verificationStatus + disconnectedAt.
    }

    // ═══════════════════════════════════════════════════════════════════
    // COMPUTED PROPERTIES
    // These are excluded from Firestore serialization to avoid conflicts.
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Check if the relationship is currently active (verified and not disconnected).
     * Named checkActive() to avoid conflicting with Firestore "active" property.
     */
    @Exclude
    public boolean checkActive() {
        return disconnectedAt == null && "VERIFIED".equals(verificationStatus);
    }

    /**
     * Check if verification is still pending.
     * Named checkPending() to avoid conflicting with Firestore "pending" property.
     */
    @Exclude
    public boolean checkPending() {
        return "PENDING".equals(verificationStatus);
    }

    /**
     * Alias for isPrimary() to match field naming convention.
     */
    public boolean isPrimary() {
        return primary;
    }
}
