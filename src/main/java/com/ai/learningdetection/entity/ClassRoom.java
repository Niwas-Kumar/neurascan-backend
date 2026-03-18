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
    private boolean isActive = true;  // Default to true
    private String createdAt;
    private String updatedAt;
}
