package com.ai.learningdetection.service;

import com.ai.learningdetection.entity.PasswordResetToken;
import com.ai.learningdetection.entity.Parent;
import com.ai.learningdetection.entity.Teacher;
import com.ai.learningdetection.exception.ResourceNotFoundException;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final Firestore firestore;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    private static final String TOKENS_COLLECTION = "reset_tokens";
    private static final String TEACHERS_COLLECTION = "teachers";
    private static final String PARENTS_COLLECTION = "parents";

    // ── Request reset link ────────────────────────────────────
    public void requestPasswordReset(String email) {
        try {
            QuerySnapshot teacherQuery = firestore.collection(TEACHERS_COLLECTION).whereEqualTo("email", email).limit(1).get().get();
            QuerySnapshot parentQuery = firestore.collection(PARENTS_COLLECTION).whereEqualTo("email", email).limit(1).get().get();

            if (teacherQuery.isEmpty() && parentQuery.isEmpty()) {
                log.warn("Password reset requested for unknown email: {}", email);
                return;
            }

            // Delete old tokens
            QuerySnapshot oldTokens = firestore.collection(TOKENS_COLLECTION).whereEqualTo("email", email).get().get();
            for (DocumentSnapshot doc : oldTokens.getDocuments()) {
                doc.getReference().delete();
            }

            // Create new token
            String tokenValue = UUID.randomUUID().toString();
            PasswordResetToken token = PasswordResetToken.builder()
                    .token(tokenValue)
                    .email(email)
                    .expiresAt(new Date(System.currentTimeMillis() + 3600000))
                    .used(false)
                    .build();
            firestore.collection(TOKENS_COLLECTION).add(token).get();

            sendResetEmail(email, tokenValue);
            log.info("Password reset token issued for: {} (Firestore)", email);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firestore error during password reset request", e);
        }
    }

    // ── Verify token validity ─────────────────────────────────
    public boolean verifyToken(String tokenValue) {
        try {
            QuerySnapshot query = firestore.collection(TOKENS_COLLECTION)
                    .whereEqualTo("token", tokenValue)
                    .limit(1).get().get();

            if (query.isEmpty()) return false;

            DocumentSnapshot doc = query.getDocuments().get(0);
            PasswordResetToken token = doc.toObject(PasswordResetToken.class);
            return token != null && !token.isExpired() && !token.isUsed();
        } catch (InterruptedException | ExecutionException e) {
            return false;
        }
    }

    // ── Execute reset ─────────────────────────────────────────
    public void resetPassword(String tokenValue, String newPassword) {
        try {
            QuerySnapshot query = firestore.collection(TOKENS_COLLECTION)
                    .whereEqualTo("token", tokenValue)
                    .limit(1).get().get();

            if (query.isEmpty()) throw new ResourceNotFoundException("Reset token not found");

            DocumentSnapshot tokenSnap = query.getDocuments().get(0);
            PasswordResetToken token = tokenSnap.toObject(PasswordResetToken.class);

            if (token == null || token.isExpired()) {
                throw new IllegalArgumentException("Reset link has expired.");
            }
            if (token.isUsed()) {
                throw new IllegalArgumentException("Reset link has already been used.");
            }

            String encoded = passwordEncoder.encode(newPassword);
            String email = token.getEmail();

            // Update Teacher
            QuerySnapshot teacherQuery = firestore.collection(TEACHERS_COLLECTION).whereEqualTo("email", email).limit(1).get().get();
            if (!teacherQuery.isEmpty()) {
                teacherQuery.getDocuments().get(0).getReference().update("password", encoded).get();
                log.info("Password reset for teacher in Firestore: {}", email);
            }

            // Update Parent
            QuerySnapshot parentQuery = firestore.collection(PARENTS_COLLECTION).whereEqualTo("email", email).limit(1).get().get();
            if (!parentQuery.isEmpty()) {
                parentQuery.getDocuments().get(0).getReference().update("password", encoded).get();
                log.info("Password reset for parent in Firestore: {}", email);
            }

            // Mark token as used
            tokenSnap.getReference().update("used", true).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firestore error during password reset", e);
        }
    }

    // ── Send email ────────────────────────────────────────────
    private void sendResetEmail(String toEmail, String tokenValue) {
        String link = frontendUrl + "/reset-password?token=" + tokenValue;
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(toEmail);
            msg.setSubject("NeuraScan — Reset Your Password");
            msg.setText(
                "Hello,\n\n" +
                "You requested a password reset for your NeuraScan account.\n\n" +
                "Click the link below (valid for 1 hour):\n\n" +
                link + "\n\n" +
                "If you did not request this, you can safely ignore this email.\n\n" +
                "— The NeuraScan Team"
            );
            mailSender.send(msg);
            log.info("Reset email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send reset email to {}: {}", toEmail, e.getMessage());
            log.warn("\n=======================================================\n" +
                     "  LOCAL DEV: Email sending failed (SMTP not configured).\n" +
                     "  Use this link to reset password for {}:\n" +
                     "  {}\n" +
                     "=======================================================", toEmail, link);
        }
    }
}

