package com.ai.learningdetection.dto;

import lombok.*;

import java.util.Date;
import java.util.List;

public class AnalysisDTOs {

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AnalysisReportResponse {
        private String reportId;
        private String paperId;
        private String studentId;
        private String studentName;
        private String className;
        private Double dyslexiaScore;
        private Double dysgraphiaScore;
        private String aiComment;
        private String riskLevel;
        private Date createdAt;
        private Date uploadDate;
        private String originalFileName;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DashboardResponse {
        private long totalStudents;
        private long totalPapersUploaded;
        private long studentsAtRisk;
        private long lowRiskStudents;
        private long mediumRiskStudents;
        private long highRiskStudents;
        private double averageDyslexiaScore;
        private double averageDysgraphiaScore;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UploadResponse {
        private String message;
        private String paperId;
        private String reportId;
        private Double dyslexiaScore;
        private Double dysgraphiaScore;
        private String aiComment;
        private String riskLevel;
    }

    // Internal DTO for Python AI service response
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiServiceResponse {
        private Double dyslexia_score;
        private Double dysgraphia_score;
        private String analysis;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ProgressResponse {
        private String studentId;
        private String studentName;
        private List<AnalysisReportResponse> reports;
        private String trend;
    }
}

