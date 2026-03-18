package com.ai.learningdetection.service;

import com.ai.learningdetection.entity.EmailVerificationToken;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Random;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private final Firestore firestore;
    private final SendGridEmailService emailService;

    @Value("${sendgrid.from.email:hello.neurascan@gmail.com}")
    private String fromEmail;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    private static final String TOKENS_COLLECTION = "verification_tokens";
    private static final int OTP_EXPIRY_MINUTES = 15;
    private static final int MAX_RETRY_ATTEMPTS = 3;

    // Result wrapper for email sending
    public static class OtpSendResult {
        public final boolean otpGenerated;
        public final boolean emailSent;
        public final String message;
        
        public OtpSendResult(boolean otpGenerated, boolean emailSent, String message) {
            this.otpGenerated = otpGenerated;
            this.emailSent = emailSent;
            this.message = message;
        }
    }

    // ── Send 6-digit OTP ──────────────────────────────
    public OtpSendResult sendOtp(String email) {
        try {
            log.info("📧 Initiating OTP send process for: {}", email);

            // Delete old tokens for this email
            QuerySnapshot oldTokens = firestore.collection(TOKENS_COLLECTION)
                    .whereEqualTo("email", email)
                    .get().get();
            
            for (DocumentSnapshot doc : oldTokens.getDocuments()) {
                doc.getReference().delete().get();
                log.debug("Deleted expired OTP token for: {}", email);
            }

            // Generate 6-digit code
            String otp = String.format("%06d", new Random().nextInt(999999));
            long expiryTime = System.currentTimeMillis() + (OTP_EXPIRY_MINUTES * 60 * 1000);
            
            EmailVerificationToken token = EmailVerificationToken.builder()
                    .token(otp)
                    .email(email)
                    .expiresAt(new Date(expiryTime))
                    .used(false)
                    .build();
            
            // Save to Firestore
            firestore.collection(TOKENS_COLLECTION).add(token).get();
            log.info("✓ OTP token saved to Firestore for: {} (Expires in {} minutes)", email, OTP_EXPIRY_MINUTES);

            // Attempt to send email with retry logic
            boolean emailSent = sendEmailWithRetry(email, otp, 1);
            
            if (emailSent) {
                return new OtpSendResult(true, true, "OTP sent successfully");
            } else {
                return new OtpSendResult(true, false, "OTP generated but email delivery failed. Check Firestore verification_tokens collection");
            }
            
        } catch (InterruptedException | ExecutionException e) {
            log.error("❌ Firestore error during OTP setup: {}", e.getMessage(), e);
            throw new RuntimeException("Firestore error during OTP setup", e);
        }
    }

    // ── Send email with retry logic ────────────────────
    private boolean sendEmailWithRetry(String email, String otp, int attempt) {
        try {
            log.info("📤 Attempt {} - Sending verification email to: {}", attempt, email);
            
            // Generate HTML email content
            String subject = "🧠 NeuraScan — Your Email Verification Code";
            String htmlContent = generateOtpEmailHtml(otp);
            
            // Send via SendGrid
            return emailService.sendHtmlEmail(email, subject, htmlContent);
            
        } catch (RuntimeException e) {
            log.error("❌ Error sending email: {}", e.getMessage());
            log.warn("⚠️  Email delivery failed but OTP is available in Firestore for manual verification");
            return false;
        }
    }

    // ── Generate HTML email content ────────────────────────
    private String generateOtpEmailHtml(String otp) {
        return "" +
            "<!DOCTYPE html>\n" +
            "<html>\n" +
            "<head>\n" +
            "    <meta charset=\"UTF-8\">\n" +
            "    <style>\n" +
            "        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f5f5f5; }\n" +
            "        .container { max-width: 600px; margin: 40px auto; background: white; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); overflow: hidden; }\n" +
            "        .header { background: linear-gradient(135deg, #1a73e8 0%, #8b5cf6 100%); color: white; padding: 40px 20px; text-align: center; }\n" +
            "        .header h1 { margin: 0; font-size: 28px; font-weight: 700; }\n" +
            "        .header p { margin: 8px 0 0 0; opacity: 0.9; font-size: 14px; }\n" +
            "        .content { padding: 40px 30px; }\n" +
            "        .greeting { font-size: 16px; color: #202124; margin-bottom: 20px; }\n" +
            "        .otp-box { background: #f8f9fa; border: 2px solid #e8eaed; border-radius: 8px; padding: 24px; text-align: center; margin: 30px 0; }\n" +
            "        .otp-code { font-size: 36px; font-weight: 700; color: #1a73e8; letter-spacing: 4px; font-family: monospace; }\n" +
            "        .otp-note { font-size: 12px; color: #80868b; margin-top: 12px; }\n" +
            "        .info-box { background: #e8f0fe; border-left: 4px solid #1a73e8; padding: 16px; margin: 20px 0; border-radius: 4px; }\n" +
            "        .info-box p { margin: 0; font-size: 13px; color: #1a73e8; }\n" +
            "        .warning { background: #fce5e5; border-left: 4px solid #d93025; padding: 16px; margin: 20px 0; border-radius: 4px; }\n" +
            "        .warning p { margin: 0; font-size: 13px; color: #d93025; }\n" +
            "        .footer { background: #f8f9fa; padding: 20px; text-align: center; border-top: 1px solid #e8eaed; font-size: 12px; color: #80868b; }\n" +
            "        .footer a { color: #1a73e8; text-decoration: none; }\n" +
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <div class=\"container\">\n" +
            "        <div class=\"header\">\n" +
            "            <h1>🧠 NeuraScan</h1>\n" +
            "            <p>Email Verification Required</p>\n" +
            "        </div>\n" +
            "\n" +
            "        <div class=\"content\">\n" +
            "            <p class=\"greeting\">Hello,</p>\n" +
            "\n" +
            "            <p>Welcome to NeuraScan! To complete your registration and secure your account, please verify your email address using the code below:</p>\n" +
            "\n" +
            "            <div class=\"otp-box\">\n" +
            "                <div class=\"otp-code\">" + otp + "</div>\n" +
            "                <div class=\"otp-note\">Valid for 15 minutes</div>\n" +
            "            </div>\n" +
            "\n" +
            "            <div class=\"info-box\">\n" +
            "                <p><strong>💡 How to use:</strong></p>\n" +
            "                <p>Enter this 6-digit code in the NeuraScan mobile or web app to verify your email.</p>\n" +
            "            </div>\n" +
            "\n" +
            "            <div class=\"warning\">\n" +
            "                <p><strong>🔒 Security Notice:</strong> Never share this code with anyone. NeuraScan staff will never ask for your verification code.</p>\n" +
            "            </div>\n" +
            "\n" +
            "            <p style=\"color: #80868b; font-size: 13px; margin-top: 30px;\">\n" +
            "                If you did not request this verification code, you can safely ignore this email. Your account will not be created unless you complete the registration.\n" +
            "            </p>\n" +
            "        </div>\n" +
            "\n" +
            "        <div class=\"footer\">\n" +
            "            <p>© 2026 NeuraScan. All rights reserved.</p>\n" +
            "            <p><a href=\"" + frontendUrl + "\">Visit NeuraScan</a> | <a href=\"" + frontendUrl + "/support\">Support</a></p>\n" +
            "        </div>\n" +
            "    </div>\n" +
            "</body>\n" +
            "</html>";
    }

    // ── Check if OTP is valid (for frontend step 2) ──
    public boolean verifyOtp(String email, String otp) {
        try {
            log.info("🔍 Verifying OTP for email: {}", email);
            
            QuerySnapshot query = firestore.collection(TOKENS_COLLECTION)
                    .whereEqualTo("email", email)
                    .whereEqualTo("token", otp)
                    .limit(1)
                    .get().get();

            if (query.isEmpty()) {
                log.warn("❌ No OTP token found for email: {}", email);
                return false;
            }

            DocumentSnapshot tokenSnap = query.getDocuments().get(0);
            EmailVerificationToken token = tokenSnap.toObject(EmailVerificationToken.class);

            if (token == null) {
                log.warn("❌ OTP token is null for email: {}", email);
                return false;
            }

            if (token.isExpired()) {
                log.warn("❌ OTP token expired for email: {} (expired at: {})", email, token.getExpiresAt());
                return false;
            }

            if (token.isUsed()) {
                log.warn("❌ OTP token already used for email: {}", email);
                return false;
            }

            log.info("✓ OTP verified successfully for email: {}", email);
            return true;
        } catch (InterruptedException | ExecutionException e) {
            log.error("❌ Firestore error during OTP verification: {}", e.getMessage(), e);
            throw new RuntimeException("Firestore error during OTP verification", e);
        }
    }

    // ── Consume OTP (for Registration step 3) ──
    public boolean consumeOtp(String email, String otp) {
        try {
            log.info("💿 Consuming OTP for email: {}", email);
            
            QuerySnapshot query = firestore.collection(TOKENS_COLLECTION)
                    .whereEqualTo("email", email)
                    .whereEqualTo("token", otp)
                    .limit(1)
                    .get().get();

            if (query.isEmpty()) {
                log.warn("❌ No OTP token found to consume for email: {}", email);
                return false;
            }

            DocumentSnapshot tokenSnap = query.getDocuments().get(0);
            EmailVerificationToken token = tokenSnap.toObject(EmailVerificationToken.class);

            if (token == null) {
                log.warn("❌ OTP token is null for email: {}", email);
                return false;
            }

            if (token.isExpired()) {
                log.warn("❌ Cannot consume - OTP token expired for email: {} (expired at: {})", email, token.getExpiresAt());
                return false;
            }

            if (token.isUsed()) {
                log.warn("❌ Cannot consume - OTP token already used for email: {}", email);
                return false;
            }

            // Mark as used and delete
            tokenSnap.getReference().update("used", true).get();
            log.info("✓ OTP consumed successfully for email: {}", email);
            return true;
        } catch (InterruptedException | ExecutionException e) {
            log.error("❌ Firestore error during OTP consumption: {}", e.getMessage(), e);
            throw new RuntimeException("Firestore error during OTP consumption", e);
        }
    }
}
