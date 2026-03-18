package com.ai.learningdetection.entity;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Parent {

    private String id;
    private String name;
    private String email;
    private String password;
    private String studentId;
    private String schoolId;
    private String picture;

    @Builder.Default
    private boolean emailVerified = false;  // Default value for new parents
}


