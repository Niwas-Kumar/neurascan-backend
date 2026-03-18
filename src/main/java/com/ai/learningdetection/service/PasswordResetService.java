package com.ai.learningdetection.service;

import com.ai.learningdetection.entity.PasswordResetToken;
import com.ai.learningdetection.entity.Parent;
import com.ai.learningdetection.entity.Teacher;
import com.ai.learningdetection.exception.ResourceNotFoundException;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final SendGridEmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.frontend.url:https://neurascan-frontend-blond.vercel.app}")
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
            String subject = "🧠 NeuraScan — Reset Your Password";
            String htmlContent = generateResetEmailHtml(link, toEmail);
            
            boolean emailSent = emailService.sendHtmlEmail(toEmail, subject, htmlContent);
            
            if (emailSent) {
                log.info("Reset email sent to: {}", toEmail);
            } else {
                log.warn("Reset email failed for: {} (but token saved, user can retry)", toEmail);
            }
        } catch (Exception e) {
            log.error("Error in password reset email process: {}", e.getMessage());
            log.warn("Password reset email could not be delivered via SendGrid for: {}", toEmail);
        }
    }

    // ── Generate HTML email content ────────────────────────
    private String generateResetEmailHtml(String resetLink, String email) {
        return "" +
            "<!DOCTYPE html>\n" +
            "<html>\n" +
            "<head>\n" +
            "    <meta charset=\"UTF-8\">\n" +
            "    <style>\n" +
            "        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f5f5f5; }\n" +
            "        .container { max-width: 600px; margin: 40px auto; background: white; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); overflow: hidden; }\n" +
            "        .header { background: linear-gradient(135deg, #d93025 0%, #f57c00 100%); color: white; padding: 40px 20px; text-align: center; }\n" +
            "        .header h1 { margin: 0; font-size: 28px; font-weight: 700; }\n" +
            "        .header p { margin: 8px 0 0 0; opacity: 0.9; font-size: 14px; }\n" +
            "        .content { padding: 40px 30px; }\n" +
            "        .greeting { font-size: 16px; color: #202124; margin-bottom: 20px; }\n" +
            "        .button-box { text-align: center; margin: 30px 0; }\n" +
            "        .reset-button { background: linear-gradient(135deg, #d93025 0%, #f57c00 100%); color: white; padding: 14px 40px; text-decoration: none; border-radius: 6px; font-weight: 600; display: inline-block; }\n" +
            "        .reset-button:hover { opacity: 0.9; }\n" +
            "        .info-box { background: #e8f0fe; border-left: 4px solid #d93025; padding: 16px; margin: 20px 0; border-radius: 4px; }\n" +
            "        .info-box p { margin: 0; font-size: 13px; color: #d93025; }\n" +
            "        .warning { background: #fce5e5; border-left: 4px solid #d93025; padding: 16px; margin: 20px 0; border-radius: 4px; }\n" +
            "        .warning p { margin: 0; font-size: 13px; color: #d93025; }\n" +
            "        .footer { background: #f8f9fa; padding: 20px; text-align: center; border-top: 1px solid #e8eaed; font-size: 12px; color: #80868b; }\n" +
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <div class=\"container\">\n" +
            "        <div class=\"header\">\n" +
            "            <h1>🧠 NeuraScan</h1>\n" +
            "            <p>Password Reset Request</p>\n" +
            "        </div>\n" +
            "\n" +
            "        <div class=\"content\">\n" +
            "            <p class=\"greeting\">Hello,</p>\n" +
            "\n" +
            "            <p>We received a request to reset the password for your NeuraScan account associated with this email address.</p>\n" +
            "\n" +
            "            <div class=\"button-box\">\n" +
            "                <a href=\"" + resetLink + "\" class=\"reset-button\">Reset Password</a>\n" +
            "            </div>\n" +
            "\n" +
            "            <p style=\"color: #80868b; font-size: 13px; text-align: center;\">This link expires in 1 hour</p>\n" +
            "\n" +
            "            <div class=\"warning\">\n" +
            "                <p><strong>🔒 Security:</strong> If you didn't request this, ignore this email and your account will remain secure.</p>\n" +
            "            </div>\n" +
            "        </div>\n" +
            "\n" +
            "        <div class=\"footer\">\n" +
            "            <p>© 2026 NeuraScan. All rights reserved.</p>\n" +
            "        </div>\n" +
            "    </div>\n" +
            "</body>\n" +
            "</html>";
    }
}
