package com.ai.learningdetection.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;

public class ClassDTOs {

    @Data
    public static class ClassCreateRequest {
        @NotBlank(message = "Class name is required")
        private String className;

        @NotBlank(message = "Section is required")
        private String section;

        @NotBlank(message = "Academic year is required")
        private String academicYear;

        @NotBlank(message = "Subject is required")
        private String subject;

        private String schoolId;
        private String teacherId;
        private List<String> studentIds;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ClassResponse {
        private String id;
        private String className;
        private String section;
        private String academicYear;
        private String subject;
        private String schoolId;
        private String teacherId;
        private List<String> studentIds;
        private boolean isActive;
        private String createdAt;
        private String updatedAt;
        private int studentCount;
    }
}
