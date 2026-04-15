package com.ai.learningdetection.entity;

import lombok.*;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Quiz {
    private String id;
    private String teacherId;
    private String classId;
    private String topic;
    private Date createdAt;
    private List<QuizQuestion> questions;
    
    // Distribution Tracking
    private Date distributedAt;         // When quiz was sent to students
    private List<String> distributedToEmail;  // List of email addresses sent to
    private int totalDistributed;       // Count of distribution sends
    private int totalAttempts;          // Count of quiz attempts made
    private double averageScore;        // Average score across all attempts

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuizQuestion {
        private String id;
        private String question;
        private List<String> options;
        private String answer;
        private String category;
        private String screeningTarget;
        private String difficulty;
    }
}
