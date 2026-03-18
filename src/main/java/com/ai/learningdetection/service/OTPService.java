package com.ai.learningdetection.service;

import com.ai.learningdetection.entity.EmailVerificationToken;
import com.google.cloud.firestore.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Date;
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
     * Generate and save OTP to Firestore
     */
    public String generateOTP(String email) {
        try {
            // Clean old OTPs
            deleteOldOTPs(email);

            // Generate 6-digit OTP
            String otp = String.format("%06d", new Random().nextInt(999999));
            long expiryTime = System.currentTimeMillis() + (OTP_EXPIRY_MINUTES * 60 * 1000);

            // Save to Firestore
            EmailVerificationToken token = EmailVerificationToken.builder()
                    .token(otp)
                    .email(email)
                    .expiresAt(new Date(expiryTime))
                    .used(false)
                    .build();

            firestore.collection(TOKENS_COLLECTION).add(token).get();
            log.info("✅ OTP generated for {}: {}", email, otp);
            return otp;

        } catch (InterruptedException | ExecutionException e) {
            log.error("❌ Firestore error generating OTP: {}", e.getMessage());
            throw new RuntimeException("Failed to generate OTP", e);
        }
    }

    /**
     * Verify if OTP is valid
     */
    public boolean verifyOTP(String email, String otp) {
        try {
            QuerySnapshot query = firestore.collection(TOKENS_COLLECTION)
                    .whereEqualTo("email", email)
                    .whereEqualTo("token", otp)
                    .limit(1)
                    .get().get();

            if (query.isEmpty()) {
                log.warn("❌ OTP not found for: {}", email);
                return false;
            }

            DocumentSnapshot doc = query.getDocuments().get(0);
            EmailVerificationToken token = doc.toObject(EmailVerificationToken.class);

            if (token == null) {
                log.warn("❌ OTP token is null for: {}", email);
                return false;
            }

            if (token.isExpired()) {
                log.warn("❌ OTP expired for: {}", email);
                return false;
            }

            if (token.isUsed()) {
                log.warn("❌ OTP already used for: {}", email);
                return false;
            }

            log.info("✅ OTP verified for: {}", email);
            return true;

        } catch (InterruptedException | ExecutionException e) {
            log.error("❌ Firestore error verifying OTP: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Mark OTP as used and delete it
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
                log.info("✅ OTP consumed for: {}", email);
            }

        } catch (InterruptedException | ExecutionException e) {
            log.error("❌ Error consuming OTP: {}", e.getMessage());
        }
    }

    /**
     * Delete old OTPs for cleanup
     */
    private void deleteOldOTPs(String email) {
        try {
            QuerySnapshot query = firestore.collection(TOKENS_COLLECTION)
                    .whereEqualTo("email", email)
                    .get().get();

            for (DocumentSnapshot doc : query.getDocuments()) {
                doc.getReference().delete().get();
            }
        } catch (InterruptedException | ExecutionException e) {
            log.warn("⚠️  Error deleting old OTPs: {}", e.getMessage());
        }
    }
}
