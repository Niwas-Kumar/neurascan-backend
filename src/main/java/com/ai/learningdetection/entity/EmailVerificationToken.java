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
    private Date createdAt;
    private Long expiresAtMillis;  // Timestamp in milliseconds for simplicity
    
    @Builder.Default
    private boolean used = false;

    // Check if token has expired (NOT a getter - use method with clear name)
    public boolean hasExpired() {
        if (expiresAtMillis == null) return true;
        return System.currentTimeMillis() > expiresAtMillis;
    }
}

