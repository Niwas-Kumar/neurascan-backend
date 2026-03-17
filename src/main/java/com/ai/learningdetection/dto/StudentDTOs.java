package com.ai.learningdetection.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

public class StudentDTOs {

    @Data
    public static class StudentRequest {
        @NotBlank(message = "Roll number is required")
        private String rollNumber;

        @NotBlank(message = "Student name is required")
        private String name;

        @NotBlank(message = "Class name is required")
        private String className;

        private String section;

        @NotNull(message = "Age is required")
        @Min(value = 1, message = "Age must be at least 1")
        private Integer age;

        private String dateOfBirth;
        private String gender;
        private String profilePhotoUrl;
        private String schoolId;
        private String teacherId;
        private String parentUid;
        private java.util.List<String> tags;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StudentResponse {
        private String id;
        private String rollNumber;
        private String name;
        private String className;
        private String section;
        private Integer age;
        private String dateOfBirth;
        private String gender;
        private String schoolId;
        private String teacherId;
        private String teacherName;
        private String parentUid;
        private String profilePhotoUrl;
        private boolean isActive;
        private java.util.List<String> tags;
        private String createdAt;
        private String updatedAt;
        private int totalPapers;
    }
}

