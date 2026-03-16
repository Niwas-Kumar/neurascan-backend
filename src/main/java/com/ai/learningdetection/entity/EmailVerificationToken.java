package com.ai.learningdetection.entity;

import lombok.*;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailVerificationToken {

    private String id;
    private String token;
    private String email;
    private Date expiresAt;
    
    @Builder.Default
    private boolean used = false;

    public boolean isExpired() {
        return expiresAt != null && new Date().after(expiresAt);
    }
}

