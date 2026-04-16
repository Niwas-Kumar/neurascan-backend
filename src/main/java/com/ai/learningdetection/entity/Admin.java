package com.ai.learningdetection.entity;

import com.google.cloud.firestore.annotation.IgnoreExtraProperties;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IgnoreExtraProperties
public class Admin {

    private String id;
    private String name;
    private String email;
    private String password;
    private String createdAt;

    @Builder.Default
    private boolean emailVerified = true;
}
