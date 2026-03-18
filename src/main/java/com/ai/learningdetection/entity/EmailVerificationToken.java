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
    private Date expired;  // ✅ Backward compatibility: alternative field name used in older Firestore documents
    
    @Builder.Default
    private boolean used = false;

    public boolean isExpired() {
        // Check both expiresAt and deprecated 'expired' field for backward compatibility
        Date expirationDate = expiresAt != null ? expiresAt : expired;
        return expirationDate != null && new Date().after(expirationDate);
    }
}

