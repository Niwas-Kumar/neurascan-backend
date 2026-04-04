package com.ai.learningdetection.entity;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Student {

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
    private String parentUid;
    private String profilePhotoUrl;
    @Builder.Default
    private boolean active = true;  // Renamed from 'isActive' to avoid Lombok getter collision (isIsActive vs isActive)
    private java.util.List<String> tags;
    private String createdAt;
    private String updatedAt;

    /**
     * Firestore compatibility: accepts "isActive" field from existing documents.
     * Maps to the "active" field.
     */
    public void setIsActive(Boolean isActive) {
        this.active = isActive != null && isActive;
    }

    /**
     * Convenience method to check active status.
     */
    public boolean isActive() {
        return active;
    }
}

