package com.ai.learningdetection.entity;

import lombok.*;
import java.util.Date;
import java.util.List;

/**
 * Represents a complete quiz attempt session by a student or parent.
 * Tracks overall attempt metrics and links to individual question responses.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizAttempt {
    private String id;
    private String quizId;
    private String studentId;           // Student attempting the quiz
    private String parentId;            // Parent if parent attempted (optional, null if student)
    private String classId;
    
    // Attempt Metadata
    private String attemptToken;        // Unique secure token for this attempt link
    private Date startedAt;
    private Date completedAt;
    private boolean isCompleted;        // Flag to track if quiz was completed
    
    // Quiz Performance Metrics
    private int totalQuestions;
    private int correctAnswers;
    private double score;               // Percentage (0-100)
    private long totalTimeSpentMs;      // Total time in milliseconds
    
    // Question tracking
    private List<String> questionResponseIds;  // References to QuestionResponse entities
    
    // AI Analysis
    private String aiAnalysisId;        // Reference to AI analysis results
    private boolean sentToAiModel;      // Track if result was sent to AI
    private Date aiAnalysisDate;
    
    // Learning Progress
    private String learningGapSummary;  // Short summary from AI analysis
    private List<String> strongAreas;   // Topics/areas student did well in
    private List<String> weakAreas;     // Topics/areas needing improvement
}
