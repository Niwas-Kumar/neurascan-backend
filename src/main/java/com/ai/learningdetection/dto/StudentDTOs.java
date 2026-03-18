package com.ai.learningdetection.dto;

import jakarta.validation.constraints.*;
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
        @Max(value = 100, message = "Age cannot exceed 100")  // ✅ Add upper bound validation
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
        @Builder.Default
        private boolean isActive = true;  // Default to true
        private java.util.List<String> tags;
        private String createdAt;
        private String updatedAt;
        
        @Builder.Default
        private int totalPapers = 0;  // ✅ Default to 0 instead of null
        
        // Provide defaults via a static factory if needed
        public static StudentResponse emptyResponse(String id, String name) {
            return StudentResponse.builder()
                    .id(id)
                    .name(name)
                    .teacherName("Unknown")
                    .totalPapers(0)
                    .build();
        }
    }
}

