package com.ai.learningdetection.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

public class StudentDTOs {

    @Data
    public static class StudentRequest {
        @NotBlank(message = "Student name is required")
        private String name;

        @NotBlank(message = "Class name is required")
        private String className;

        @NotNull(message = "Age is required")
        @Min(value = 1, message = "Age must be at least 1")
        private Integer age;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StudentResponse {
        private String id;
        private String name;
        private String className;
        private Integer age;
        private String teacherId;
        private String teacherName;
        private int totalPapers;
    }
}

