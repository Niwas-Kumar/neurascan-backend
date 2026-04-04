package com.ai.learningdetection.entity;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IgnoreExtraProperties
public class Teacher {

    private String id;
    private String name;
    private String email;
    private String password;
    private String schoolId;  // ✅ Single source of truth (removed 'school' field)
    private String picture;
    private String createdAt;
    private String updatedAt;
    
    @Builder.Default
    private boolean emailVerified = false;  // Default value for new teachers

    // Legacy Firestore compatibility: some old documents still use `school`.
    public void setSchool(String school) {
        if ((this.schoolId == null || this.schoolId.isBlank()) && school != null && !school.isBlank()) {
            this.schoolId = school;
        }
    }
}

