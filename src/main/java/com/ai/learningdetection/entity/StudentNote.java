package com.ai.learningdetection.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentNote {
    private String id;
    private String studentId;
    private String authorId;       // teacherId who wrote the note
    private String authorName;
    private String content;
    private boolean visibleToParent;  // teacher controls visibility
    private String createdAt;
    private String updatedAt;
}
