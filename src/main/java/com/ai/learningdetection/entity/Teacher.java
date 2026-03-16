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
    private String school;
    private String picture;
    
    @Builder.Default
    private boolean emailVerified = false;
}

