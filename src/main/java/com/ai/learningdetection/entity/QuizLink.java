package com.ai.learningdetection.entity;

import lombok.*;
import java.util.Date;

/**
 * Represents a unique quiz attempt link sent to students/parents.
 * Used for secure, trackable quiz distribution and access control.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizLink {
    private String id;
    private String quizId;
    private String token;               // Unique secure token for this link
    
    // Recipient Information
    private String recipientId;         // studentId or parentId
    private String recipientEmail;
    private String recipientType;       // "STUDENT" or "PARENT"
    
    // Link Status
    private Date createdAt;
    private Date sentAt;                // When email was sent
    private Date firstAccessAt;         // When link was first clicked
    private Date expiredAt;             // Optional: expiration date
    private boolean isExpired;
    
    // Tracking
    private int accessCount;            // How many times link was accessed
    private String quizAttemptId;       // Linked to the actual attempt if started
    
    // Admin Fields
    private String teacherId;           // Who sent the link
    private String classId;
}
