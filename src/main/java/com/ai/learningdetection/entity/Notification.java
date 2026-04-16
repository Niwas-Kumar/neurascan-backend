package com.ai.learningdetection.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    private String id;
    private String userId;         // recipient (teacherId or parentId)
    private String role;           // ROLE_TEACHER, ROLE_PARENT
    private String type;           // ANALYSIS_COMPLETE, TEACHER_APPROVED, QUIZ_SUBMITTED, STUDENT_LINKED, GENERAL
    private String title;
    private String message;
    private String link;           // optional frontend route to navigate to
    private boolean read;
    private String createdAt;
}
