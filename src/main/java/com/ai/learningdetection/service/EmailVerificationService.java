package com.ai.learningdetection.service;

import com.ai.learningdetection.entity.EmailVerificationToken;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Random;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private final Firestore firestore;
    private final JavaMailSender mailSender;

    private static final String TOKENS_COLLECTION = "verification_tokens";

    // ── Send 6-digit OTP ──────────────────────────────
    public void sendOtp(String email) {
        try {
            log.info("Initiating OTP send process for {}", email);

            // Delete old tokens for this email
            QuerySnapshot oldTokens = firestore.collection(TOKENS_COLLECTION)
                    .whereEqualTo("email", email)
                    .get().get();
            
            for (DocumentSnapshot doc : oldTokens.getDocuments()) {
                doc.getReference().delete();
            }

            // Generate 6-digit code
            String otp = String.format("%06d", new Random().nextInt(999999));
            EmailVerificationToken token = EmailVerificationToken.builder()
                    .token(otp)
                    .email(email)
                    .expiresAt(new Date(System.currentTimeMillis() + 15 * 60 * 1000)) // 15 mins expiry
                    .used(false)
                    .build();
            
            firestore.collection(TOKENS_COLLECTION).add(token).get();

            // Try to send email; do not break flow if SMTP is missing/unavailable
            try {
                sendEmail(email, otp);
                log.info("OTP sent to email {} and saved in Firestore", email);
            } catch (RuntimeException mailEx) {
                // If email delivery fails (e.g., SMTP not configured), keep token in Firestore and return success
                log.warn("Email sending failed for {}: {}. OTP will still be valid for verification.", email, mailEx.getMessage());
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firestore error during OTP setup", e);
        }
    }

    // ── Check if OTP is valid (for frontend step 2) ──
    public boolean verifyOtp(String email, String otp) {
        try {
            QuerySnapshot query = firestore.collection(TOKENS_COLLECTION)
                    .whereEqualTo("email", email)
                    .whereEqualTo("token", otp)
                    .limit(1)
                    .get().get();

            if (query.isEmpty()) return false;

            DocumentSnapshot tokenSnap = query.getDocuments().get(0);
            EmailVerificationToken token = tokenSnap.toObject(EmailVerificationToken.class);

            if (token == null || token.isExpired() || token.isUsed()) {
                return false;
            }

            return true;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firestore error during OTP verification", e);
        }
    }

    // ── Consume OTP (for Registration step 3) ──
    public boolean consumeOtp(String email, String otp) {
        try {
            QuerySnapshot query = firestore.collection(TOKENS_COLLECTION)
                    .whereEqualTo("email", email)
                    .whereEqualTo("token", otp)
                    .limit(1)
                    .get().get();

            if (query.isEmpty()) return false;

            DocumentSnapshot tokenSnap = query.getDocuments().get(0);
            EmailVerificationToken token = tokenSnap.toObject(EmailVerificationToken.class);

            if (token == null || token.isExpired() || token.isUsed()) {
                return false;
            }

            // Mark as used
            tokenSnap.getReference().update("used", true).get();
            return true;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firestore error during OTP consumption", e);
        }
    }

    // ── Send email with console fallback ─────────────────────
    private void sendEmail(String toEmail, String otp) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(toEmail);
            msg.setSubject("NeuraScan — Your Verification Code");
            msg.setText(
                "Welcome to NeuraScan!\n\n" +
                "Here is your 6-digit verification code:\n\n" +
                otp + "\n\n" +
                "This code will expire in 15 minutes.\n\n" +
                "If you did not request this, you can safely ignore this email.\n\n" +
                "— The NeuraScan Team"
            );
            mailSender.send(msg);
            log.info("Verification email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", toEmail, e.getMessage());
            log.warn("\n=======================================================\n" +
                     "  LOCAL DEV: Email sending failed (SMTP may not be configured).\n" +
                     "  Use this OTP to verify email for {}:\n" +
                     "  {}\n" +
                     "=======================================================", toEmail, otp);
            // Do not throw — allow user flow to proceed with OTP stored in Firestore
            // so verify and registration can still complete against the generated code.
        }
    }
}

