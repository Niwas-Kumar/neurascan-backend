package com.ai.learningdetection.entity;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IgnoreExtraProperties
public class Parent {

    private String id;
    private String name;
    private String email;
    private String password;
    private String schoolId;
    private String picture;

    @Builder.Default
    private boolean emailVerified = false;  // Default value for new parents

    // Legacy Firestore compatibility: old parent docs may still contain `studentId`.
    public void setStudentId(String ignoredStudentId) {
        // Intentionally no-op: parent-student linkage is now managed by relationship records.
    }
}


