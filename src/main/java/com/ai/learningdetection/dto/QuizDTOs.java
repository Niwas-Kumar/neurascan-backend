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
}
