package com.ai.learningdetection.entity;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Teacher {

    private String id;
    private String name;
    private String email;
    private String password;
    private String schoolId;
    private String school;
    private String picture;
    private String createdAt;
    private String updatedAt;
    
    @Builder.Default
    private boolean emailVerified = false;
}

