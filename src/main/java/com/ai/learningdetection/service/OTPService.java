package com.ai.learningdetection.service;

import com.ai.learningdetection.entity.EmailVerificationToken;
import com.google.cloud.firestore.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Random;
import java.util.concurrent.ExecutionException;

@Service
@Slf4j
public class OTPService {

    private final Firestore firestore;
    private static final String TOKENS_COLLECTION = "verification_tokens";
    private static final int OTP_EXPIRY_MINUTES = 15;

    public OTPService(Firestore firestore) {
        this.firestore = firestore;
    }

    /**
     * Generate and save OTP to Firestore with clean field structure
     */
    public String generateOTP(String email) {
        try {
            // Delete any existing OTPs for this email to avoid conflicts
            deleteExistingOTPs(email);

            // Generate 6-digit OTP
            String otp = String.format("%06d", new Random().nextInt(999999));
            long nowMillis = System.currentTimeMillis();
            long expiresAtMillis = nowMillis + (OTP_EXPIRY_MINUTES * 60 * 1000L);

            // Save to Firestore with clean structure
            EmailVerificationToken token = EmailVerificationToken.builder()
                    .token(otp)
                    .email(email)
                    .createdAt(new java.util.Date(nowMillis))
                    .expiresAtMillis(expiresAtMillis)
                    .used(false)
                    .build();

            firestore.collection(TOKENS_COLLECTION).add(token).get();
            log.info("✅ OTP generated for {}: {}", email, otp);
            return otp;

        } catch (InterruptedException | ExecutionException e) {
            log.error("❌ Error generating OTP for {}: {}", email, e.getMessage());
            throw new RuntimeException("Failed to generate OTP", e);
        }
    }

    /**
     * Verify if OTP is valid (not expired, not used)
     */
    public boolean verifyOTP(String email, String otp) {
        try {
            QuerySnapshot query = firestore.collection(TOKENS_COLLECTION)
                    .whereEqualTo("email", email)
                    .whereEqualTo("token", otp)
                    .limit(1)
                    .get().get();

            if (query.isEmpty()) {
                log.warn("❌ OTP not found for email: {}", email);
                return false;
            }

            EmailVerificationToken token = query.getDocuments().get(0).toObject(EmailVerificationToken.class);

            if (token == null) {
                log.warn("❌ Failed to deserialize OTP token for: {}", email);
                return false;
            }

            // Check if expired
            if (token.hasExpired()) {
                log.warn("❌ OTP expired for email: {}", email);
                return false;
            }

            // Check if already used
            if (token.isUsed()) {
                log.warn("❌ OTP already used for email: {}", email);
                return false;
            }

            log.info("✅ OTP verified successfully for: {}", email);
            return true;

        } catch (InterruptedException | ExecutionException e) {
            log.error("❌ Error verifying OTP for {}: {}", email, e.getMessage());
            return false;
        }
    }

    /**
     * Mark OTP as used after successful registration
     */
    public void consumeOTP(String email, String otp) {
        try {
            QuerySnapshot query = firestore.collection(TOKENS_COLLECTION)
                    .whereEqualTo("email", email)
                    .whereEqualTo("token", otp)
                    .limit(1)
                    .get().get();

            if (!query.isEmpty()) {
                DocumentSnapshot doc = query.getDocuments().get(0);
                doc.getReference().update("used", true).get();
                log.info("✅ OTP consumed/marked as used for: {}", email);
            }

        } catch (InterruptedException | ExecutionException e) {
            log.error("❌ Error consuming OTP for {}: {}", email, e.getMessage());
        }
    }

    /**
     * Delete all existing OTPs for an email (clean slate before generating new one)
     */
    private void deleteExistingOTPs(String email) {
        try {
            QuerySnapshot query = firestore.collection(TOKENS_COLLECTION)
                    .whereEqualTo("email", email)
                    .get().get();

            for (DocumentSnapshot doc : query.getDocuments()) {
                doc.getReference().delete().get();
            }
            
            if (!query.isEmpty()) {
                log.info("✅ Cleaned up {} existing OTP(s) for email: {}", query.size(), email);
            }
        } catch (InterruptedException | ExecutionException e) {
            log.warn("⚠️  Error cleaning up old OTPs for {}: {}", email, e.getMessage());
        }
    }
}
