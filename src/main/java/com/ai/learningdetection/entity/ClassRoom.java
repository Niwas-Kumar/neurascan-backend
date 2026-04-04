package com.ai.learningdetection.entity;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassRoom {
    private String id;
    private String className;
    private String section;
    private String academicYear;
    private String subject;
    private String schoolId;
    private String teacherId;
    private List<String> studentIds;
    @Builder.Default
    private boolean active = true;  // Renamed from 'isActive' to avoid Lombok getter collision

    private String createdAt;
    private String updatedAt;

    /**
     * Firestore compatibility: accepts "isActive" field from existing documents.
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
