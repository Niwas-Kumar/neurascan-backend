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
    private boolean isActive = true;  // Default to true for new students
    private java.util.List<String> tags;
    private String createdAt;
    private String updatedAt;
}

