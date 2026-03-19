package com.ai.learningdetection.dto;

import lombok.*;

import java.util.Date;
import java.util.List;
import java.util.Map;

public class QuizDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuizQuestion {
        private String id;
        private String question;
        private List<String> options;
        private String answer;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuizResponse {
        private String id;
        private String quizId;
        private String studentId;
        private String classId;
        private double score;
        private Map<String, String> answers;
        private Date submittedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuizDetail {
        private String id;
        private String teacherId;
        private String classId;
        private String topic;
        private String createdAt;
        private List<QuizQuestion> questions;
        private int totalAttempts;
        private double averageScore;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuizGenerationRequest {
        private String topic;
        private String text;
        private int questionCount;
        private String classId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuizSubmissionRequest {
        private String quizId;
        private String studentId;
        private String classId;
        private Map<String, String> answers;
    }

    // ============================================================
    // New DTOs for Quiz Attempt and Distribution
    // ============================================================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuizAttemptStartRequest {
        private String quizId;
        private String token;           // Unique token from quiz link
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuizAttemptDetail {
        private String id;              // QuizAttemptId
        private String quizId;
        private String topic;           // Quiz topic (for display)
        private String studentId;
        private String parentId;
        private String attemptToken;
        private Date startedAt;
        private Date completedAt;
        private boolean isCompleted;
        private int totalQuestions;
        private int correctAnswers;
        private double score;           // Percentage (0-100)
        private long totalTimeSpentMs;
        private List<QuestionResponseDetail> questionResponses;
        private String learningGapSummary;
        private List<String> strongAreas;
        private List<String> weakAreas;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionResponseRequest {
        private String quizAttemptId;
        private String questionId;
        private String studentAnswer;
        private long responseTimeMs;    // Time spent on this question
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionResponseDetail {
        private String id;
        private String questionId;
        private String questionText;
        private String correctAnswer;
        private String studentAnswer;
        private boolean isCorrect;
        private long responseTimeMs;
        private String confidenceLevel;
        private String explanationNote;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuizDistributionRequest {
        private String quizId;
        private List<String> studentIds;   // List of students to send to
        private List<String> parentEmails; // List of parent emails to send to
        private String customMessage;      // Optional custom message in email
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuizLinkResponse {
        private String token;
        private String attemptUrl;     // Full URL to attempt quiz
        private String recipientEmail;
        private String recipientType;  // "STUDENT" or "PARENT"
        private Date createdAt;
        private Date expiresAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuizProgressResponse {
        private String quizId;
        private String topic;
        private int totalAttempts;
        private double averageScore;
        private int participationRate;  // Percentage of students who attempted
        private List<StudentQuizProgress> studentProgress;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentQuizProgress {
        private String studentId;
        private String studentName;
        private Date attemptDate;
        private double score;
        private long timeSpentMs;
        private boolean completed;
        private String learningGap;    // Brief summary from AI
    }

    // ============================================================
    // Public Quiz Attempt DTOs (for unauthenticated access)
    // ============================================================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuizQuestionPublic {
        private String id;
        private String question;
        private List<String> options;
        // NOTE: answer is intentionally excluded for public access
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuizAttemptStartResponse {
        private String quizId;
        private String topic;
        private int totalQuestions;
        private List<QuizQuestionPublic> questions;
        private String recipientType;    // STUDENT or PARENT
        private String recipientEmail;
        private String studentId;
        private boolean valid;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuizAttemptResultResponse {
        private String attemptId;
        private String quizId;
        private String topic;
        private int totalQuestions;
        private int correctAnswers;
        private double score;
        private long totalTimeMs;
        private String performanceLevel;   // EXCELLENT, GOOD, NEEDS_IMPROVEMENT
        private List<QuestionResultDetail> questionResults;
        private String aiAnalysis;
        private List<String> strongAreas;
        private List<String> weakAreas;
        private String recommendation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionResultDetail {
        private String questionId;
        private String questionText;
        private String correctAnswer;
        private String studentAnswer;
        private boolean isCorrect;
        private long responseTimeMs;
        private String feedback;
    }
}

