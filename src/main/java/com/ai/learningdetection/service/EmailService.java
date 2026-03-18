package com.ai.learningdetection.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

@Service
@Slf4j
public class EmailService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${sendgrid.api.key}")
    private String apiKey;

    @Value("${sendgrid.from.email}")
    private String fromEmail;

    public EmailService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Send OTP email via SendGrid
     */
    public boolean sendOtpEmail(String toEmail, String otp) {
        String subject = "🧠 NeuraScan — Your Email Verification Code";
        String htmlContent = generateOtpHtml(otp);
        return sendEmail(toEmail, subject, htmlContent);
    }

    /**
     * Send password reset email via SendGrid
     */
    public boolean sendPasswordResetEmail(String toEmail, String resetLink) {
        String subject = "🧠 NeuraScan — Reset Your Password";
        String htmlContent = generatePasswordResetHtml(resetLink);
        return sendEmail(toEmail, subject, htmlContent);
    }

    /**
     * Send quiz invitation email via SendGrid
     */
    public boolean sendQuizInvitationEmail(String toEmail, String studentName, String quizTopic, String attemptUrl, String teacherName, String customMessage) {
        String subject = "🧠 NeuraScan — New Quiz for " + studentName;
        String htmlContent = generateQuizInvitationHtml(studentName, quizTopic, attemptUrl, teacherName, customMessage);
        return sendEmail(toEmail, subject, htmlContent);
    }

    /**
     * Generic email send method
     */
    private boolean sendEmail(String toEmail, String subject, String htmlContent) {
        try {
            log.info("📧 Sending email to: {} | From: {}", toEmail, fromEmail);

            // Build SendGrid request
            Map<String, Object> request = buildSendGridRequest(toEmail, subject, htmlContent);
            String payload = objectMapper.writeValueAsString(request);

            // Setup headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            // Send request
            HttpEntity<String> entity = new HttpEntity<>(payload, headers);
            restTemplate.postForObject("https://api.sendgrid.com/v3/mail/send", entity, String.class);

            log.info("✅ Email sent successfully to: {}", toEmail);
            return true;

        } catch (Exception e) {
            String errorMsg = e.getMessage();
            log.error("❌ Email send failed: {}", errorMsg);
            log.error("   → Sender email used: {}", fromEmail);
            
            if (errorMsg != null) {
                if (errorMsg.contains("403") || errorMsg.contains("verified Sender Identity")) {
                    log.error("╔════════════════════════════════════════════════════════════════════╗");
                    log.error("║ ❌ SENDGRID SENDER EMAIL NOT VERIFIED                             ║");
                    log.error("╠════════════════════════════════════════════════════════════════════╣");
                    log.error("║ Issue: Email '{}' is not verified in SendGrid                    ║", fromEmail);
                    log.error("║                                                                    ║");
                    log.error("║ Solution:                                                          ║");
                    log.error("║ 1. Go to: https://app.sendgrid.com/settings/sender_auth            ║");
                    log.error("║ 2. Click 'Verify a Single Sender'                                  ║");
                    log.error("║ 3. Enter email: {} and verify                                     ║", fromEmail);
                    log.error("║                                                                    ║");
                    log.error("║ OR: Set SENDGRID_FROM_EMAIL env var to a verified email          ║");
                    log.error("║ Example: export SENDGRID_FROM_EMAIL=your-verified@domain.com        ║");
                    log.error("╚════════════════════════════════════════════════════════════════════╝");
                } else if (errorMsg.contains("401")) {
                    log.error("╔════════════════════════════════════════════════════════════════════╗");
                    log.error("║ ❌ INVALID SENDGRID API KEY                                        ║");
                    log.error("╠════════════════════════════════════════════════════════════════════╣");
                    log.error("║ Issue: SENDGRID_API_KEY is missing or invalid                      ║");
                    log.error("║                                                                    ║");
                    log.error("║ Solution: Set environment variable:                                ║");
                    log.error("║ export SENDGRID_API_KEY=SG.your_actual_api_key_here               ║");
                    log.error("║ Get your API key: https://app.sendgrid.com/settings/api_keys       ║");
                    log.error("╚════════════════════════════════════════════════════════════════════╝");
                }
            }
            return false;
        }
    }

    /**
     * Build SendGrid API request
     */
    private Map<String, Object> buildSendGridRequest(String toEmail, String subject, String htmlContent) {
        Map<String, Object> mail = new HashMap<>();

        // From
        Map<String, String> from = new HashMap<>();
        from.put("email", fromEmail);
        mail.put("from", from);

        // Subject
        mail.put("subject", subject);

        // Content
        Map<String, String> content = new HashMap<>();
        content.put("type", "text/html");
        content.put("value", htmlContent);
        mail.put("content", new Object[]{content});

        // To
        Map<String, Object> personalization = new HashMap<>();
        Map<String, String> to = new HashMap<>();
        to.put("email", toEmail);
        personalization.put("to", new Object[]{to});
        mail.put("personalizations", new Object[]{personalization});

        return mail;
    }

    /**
     * Generate OTP email HTML
     */
    private String generateOtpHtml(String otp) {
        return "<!DOCTYPE html>\n" +
            "<html><head><meta charset='UTF-8'><style>\n" +
            "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f5f5f5; }\n" +
            ".container { max-width: 600px; margin: 40px auto; background: white; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); overflow: hidden; }\n" +
            ".header { background: linear-gradient(135deg, #1a73e8 0%, #8b5cf6 100%); color: white; padding: 40px 20px; text-align: center; }\n" +
            ".header h1 { margin: 0; font-size: 28px; font-weight: 700; }\n" +
            ".content { padding: 40px 30px; }\n" +
            ".otp-box { background: #f8f9fa; border: 2px solid #e8eaed; border-radius: 8px; padding: 24px; text-align: center; margin: 30px 0; }\n" +
            ".otp-code { font-size: 36px; font-weight: 700; color: #1a73e8; letter-spacing: 4px; font-family: monospace; }\n" +
            ".info-box { background: #e8f0fe; border-left: 4px solid #1a73e8; padding: 16px; margin: 20px 0; border-radius: 4px; }\n" +
            ".info-box p { margin: 0; font-size: 13px; color: #1a73e8; }\n" +
            ".footer { background: #f8f9fa; padding: 20px; text-align: center; border-top: 1px solid #e8eaed; font-size: 12px; color: #80868b; }\n" +
            "</style></head><body>\n" +
            "<div class='container'>\n" +
            "  <div class='header'><h1>🧠 NeuraScan</h1><p>Email Verification</p></div>\n" +
            "  <div class='content'>\n" +
            "    <p>Enter this code to verify your email:</p>\n" +
            "    <div class='otp-box'>\n" +
            "      <div class='otp-code'>" + otp + "</div>\n" +
            "      <div style='font-size: 12px; color: #80868b; margin-top: 12px;'>Valid for 15 minutes</div>\n" +
            "    </div>\n" +
            "    <div class='info-box'>\n" +
            "      <p><strong>Never share this code</strong> with anyone</p>\n" +
            "    </div>\n" +
            "  </div>\n" +
            "  <div class='footer'><p>© 2026 NeuraScan. All rights reserved.</p></div>\n" +
            "</div>\n" +
            "</body></html>";
    }

    /**
     * Generate password reset email HTML
     */
    private String generatePasswordResetHtml(String resetLink) {
        return "<!DOCTYPE html>\n" +
            "<html><head><meta charset='UTF-8'><style>\n" +
            "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f5f5f5; }\n" +
            ".container { max-width: 600px; margin: 40px auto; background: white; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); overflow: hidden; }\n" +
            ".header { background: linear-gradient(135deg, #d93025 0%, #f57c00 100%); color: white; padding: 40px 20px; text-align: center; }\n" +
            ".header h1 { margin: 0; font-size: 28px; font-weight: 700; }\n" +
            ".content { padding: 40px 30px; }\n" +
            ".button-box { text-align: center; margin: 30px 0; }\n" +
            ".reset-button { background: linear-gradient(135deg, #d93025 0%, #f57c00 100%); color: white; padding: 14px 40px; text-decoration: none; border-radius: 6px; font-weight: 600; display: inline-block; }\n" +
            ".info-box { background: #fce5e5; border-left: 4px solid #d93025; padding: 16px; margin: 20px 0; border-radius: 4px; }\n" +
            ".info-box p { margin: 0; font-size: 13px; color: #d93025; }\n" +
            ".footer { background: #f8f9fa; padding: 20px; text-align: center; border-top: 1px solid #e8eaed; font-size: 12px; color: #80868b; }\n" +
            "</style></head><body>\n" +
            "<div class='container'>\n" +
            "  <div class='header'><h1>🧠 NeuraScan</h1><p>Password Reset</p></div>\n" +
            "  <div class='content'>\n" +
            "    <p>Click the button below to reset your password:</p>\n" +
            "    <div class='button-box'>\n" +
            "      <a href='" + resetLink + "' class='reset-button'>Reset Password</a>\n" +
            "    </div>\n" +
            "    <p style='color: #80868b; font-size: 13px; text-align: center;'>Link expires in 1 hour</p>\n" +
            "    <div class='info-box'>\n" +
            "      <p>If you didn't request this, ignore this email</p>\n" +
            "    </div>\n" +
            "  </div>\n" +
            "  <div class='footer'><p>© 2026 NeuraScan. All rights reserved.</p></div>\n" +
            "</div>\n" +
            "</body></html>";
    }

    /**
     * Generate quiz invitation email HTML
     */
    private String generateQuizInvitationHtml(String studentName, String quizTopic, String attemptUrl, String teacherName, String customMessage) {
        String messageSection = (customMessage != null && !customMessage.isEmpty())
            ? "    <div class='message-box'>\n" +
              "      <p><strong>Message from teacher:</strong></p>\n" +
              "      <p>" + customMessage + "</p>\n" +
              "    </div>\n"
            : "";

        return "<!DOCTYPE html>\n" +
            "<html><head><meta charset='UTF-8'><style>\n" +
            "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f5f5f5; margin: 0; padding: 0; }\n" +
            ".container { max-width: 600px; margin: 40px auto; background: white; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); overflow: hidden; }\n" +
            ".header { background: linear-gradient(135deg, #1a73e8 0%, #8b5cf6 100%); color: white; padding: 40px 20px; text-align: center; }\n" +
            ".header h1 { margin: 0; font-size: 28px; font-weight: 700; }\n" +
            ".header p { margin: 10px 0 0; opacity: 0.9; }\n" +
            ".content { padding: 40px 30px; }\n" +
            ".quiz-box { background: linear-gradient(135deg, #e8f0fe 0%, #f3e8ff 100%); border-radius: 10px; padding: 24px; text-align: center; margin: 24px 0; }\n" +
            ".quiz-topic { font-size: 24px; font-weight: 700; color: #1a73e8; margin-bottom: 8px; }\n" +
            ".quiz-student { font-size: 14px; color: #5f6368; }\n" +
            ".button-box { text-align: center; margin: 30px 0; }\n" +
            ".start-button { background: linear-gradient(135deg, #1a73e8 0%, #8b5cf6 100%); color: white; padding: 16px 48px; text-decoration: none; border-radius: 8px; font-weight: 600; font-size: 16px; display: inline-block; }\n" +
            ".message-box { background: #f8f9fa; border-left: 4px solid #8b5cf6; padding: 16px; margin: 20px 0; border-radius: 4px; }\n" +
            ".message-box p { margin: 0 0 8px; font-size: 14px; color: #5f6368; }\n" +
            ".info-box { background: #e8f0fe; border-radius: 8px; padding: 16px; margin: 20px 0; }\n" +
            ".info-box ul { margin: 0; padding-left: 20px; font-size: 13px; color: #1a73e8; }\n" +
            ".info-box li { margin: 8px 0; }\n" +
            ".footer { background: #f8f9fa; padding: 20px; text-align: center; border-top: 1px solid #e8eaed; font-size: 12px; color: #80868b; }\n" +
            "</style></head><body>\n" +
            "<div class='container'>\n" +
            "  <div class='header'>\n" +
            "    <h1>🧠 NeuraScan</h1>\n" +
            "    <p>New Quiz Assignment</p>\n" +
            "  </div>\n" +
            "  <div class='content'>\n" +
            "    <p>Hello,</p>\n" +
            "    <p>A new quiz has been assigned for <strong>" + studentName + "</strong> by " + teacherName + ".</p>\n" +
            "    <div class='quiz-box'>\n" +
            "      <div class='quiz-topic'>📝 " + quizTopic + "</div>\n" +
            "      <div class='quiz-student'>Quiz for: " + studentName + "</div>\n" +
            "    </div>\n" +
            messageSection +
            "    <div class='button-box'>\n" +
            "      <a href='" + attemptUrl + "' class='start-button'>Start Quiz Now</a>\n" +
            "    </div>\n" +
            "    <div class='info-box'>\n" +
            "      <p style='margin: 0 0 12px; font-weight: 600; color: #1a73e8;'>📋 Important Information:</p>\n" +
            "      <ul>\n" +
            "        <li>This link is unique and should only be used by " + studentName + "</li>\n" +
            "        <li>The quiz can only be taken once</li>\n" +
            "        <li>Time spent on each question is tracked</li>\n" +
            "        <li>Results will be shared with the teacher</li>\n" +
            "      </ul>\n" +
            "    </div>\n" +
            "    <p style='color: #80868b; font-size: 13px;'>If the button doesn't work, copy and paste this link into your browser:</p>\n" +
            "    <p style='color: #1a73e8; font-size: 12px; word-break: break-all;'>" + attemptUrl + "</p>\n" +
            "  </div>\n" +
            "  <div class='footer'>\n" +
            "    <p>© 2026 NeuraScan. AI-Powered Learning Disability Detection.</p>\n" +
            "    <p>This is an automated message. Please do not reply.</p>\n" +
            "  </div>\n" +
            "</div>\n" +
            "</body></html>";
    }
}
