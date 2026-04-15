package com.ai.learningdetection.entity;

import lombok.*;
import java.util.Date;

/**
 * Represents a single question response within a quiz attempt.
 * Tracks per-question metrics including response time and correctness.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionResponse {
    private String id;
    private String quizAttemptId;       // Reference to parent QuizAttempt
    private String quizId;
    private String questionId;          // ID of the question being answered
    private String studentId;
    
    // Question Details (stored for audit/analysis)
    private String questionText;
    private String correctAnswer;
    
    // Student's Response
    private String studentAnswer;
    private boolean isCorrect;
    
    // Timing Metrics
    private long responseTimeMs;        // How long student took to answer (milliseconds)
    private Date answeredAt;
    
    // Screening category (from quiz question)
    private String category;            // e.g. letter_discrimination, phoneme_awareness
    private String screeningTarget;     // DYSLEXIA or DYSGRAPHIA

    // Analysis
    private String confidenceLevel;     // high, medium, low - inferred from response time
    private String explanationNote;     // Any AI-generated explanation

    // Custom setter for Firestore deserialization compatibility
    public void setCorrect(boolean correct) {
        this.isCorrect = correct;
    }
}
