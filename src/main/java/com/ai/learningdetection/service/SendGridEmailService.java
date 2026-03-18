package com.ai.learningdetection.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SendGridEmailService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${sendgrid.api.key:}")
    private String sendGridApiKey;

    @Value("${sendgrid.from.email:hello.neurascan@gmail.com}")
    private String fromEmail;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final String SENDGRID_API_URL = "https://api.sendgrid.com/v3/mail/send";

    /**
     * Send HTML email via SendGrid API with retry logic
     * @param toEmail Recipient email address
     * @param subject Email subject
     * @param htmlContent HTML content of the email
     * @return true if email sent successfully, false otherwise
     */
    public boolean sendHtmlEmail(String toEmail, String subject, String htmlContent) {
        return sendEmailWithRetry(toEmail, subject, htmlContent, 1);
    }

    /**
     * Send email with retry logic (exponential backoff)
     */
    private boolean sendEmailWithRetry(String toEmail, String subject, String htmlContent, int attempt) {
        try {
            // Check if SendGrid is configured
            if (sendGridApiKey == null || sendGridApiKey.isEmpty()) {
                log.warn("\n" +
                        "┌─────────────────────────────────────────────────────┐\n" +
                        "│ ⚠️  SENDGRID NOT CONFIGURED (DEV MODE)              │\n" +
                        "├─────────────────────────────────────────────────────┤\n" +
                        "│ For production, set environment variable:      │\n" +
                        "│ SENDGRID_API_KEY=SG.xxxxxxxxxxxxxxxxxxxxx     │\n" +
                        "└─────────────────────────────────────────────────────┘");
                log.info("✓ Email saved to Firestore for manual verification (dev mode)");
                return true; // Dev mode - treat as success since OTP is in Firestore
            }

            log.info("📤 Attempt {} - Sending email via SendGrid to: {}", attempt, toEmail);

            // Build SendGrid API request
            Map<String, Object> mailObject = buildSendGridMailObject(toEmail, subject, htmlContent);
            String jsonPayload = objectMapper.writeValueAsString(mailObject);

            // Prepare HTTP headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + sendGridApiKey);

            // Send request
            HttpEntity<String> request = new HttpEntity<>(jsonPayload, headers);
            try {
                restTemplate.postForObject(SENDGRID_API_URL, request, String.class);
                log.info("✓ Email sent successfully to: {} via SendGrid", toEmail);
                return true;
            } catch (RuntimeException e) {
                throw e;
            }

        } catch (Exception e) {
            log.warn("⚠️  Attempt {} failed to send email: {}", attempt, e.getMessage());
            
            // Parse and log SendGrid-specific errors
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("403")) {
                if (errorMsg.contains("verified Sender Identity")) {
                    log.error("\n╔════════════════════════════════════════════════════════════╗");
                    log.error("║ SendGrid Sender Email NOT Verified                           ║");
                    log.error("╟────────────────────────────────────────────────────────────╢");
                    log.error("║ Error: 'The from address does not match a verified Sender'  ║");
                    log.error("║                                                             ║");
                    log.error("║ SOLUTION:                                                   ║");
                    log.error("║ 1. Go to SendGrid dashboard: https://app.sendgrid.com       ║");
                    log.error("║ 2. Sender Authentication → Verify a Single Sender           ║");
                    log.error("║ 3. Add email: hello.neurascan@gmail.com                     ║");
                    log.error("║ 4. Verify via email sent by SendGrid                        ║");
                    log.error("║ 5. Wait 5 minutes and retry                                 ║");
                    log.error("║                                                             ║");
                    log.error("║ Guide: See SENDGRID_SETUP_GUIDE.md in project root          ║");
                    log.error("╚════════════════════════════════════════════════════════════╝");
                } else if (errorMsg.contains("API KeyPermission")) {
                    log.error("SendGrid API Key has insufficient permissions. Create new API key with Full Access.");
                }
            } else if (errorMsg != null && errorMsg.contains("401")) {
                log.error("\n╔════════════════════════════════════════════════════════════╗");
                log.error("║ SendGrid API Key is INVALID or NOT SET                       ║");
                log.error("╟────────────────────────────────────────────────────────────╢");
                log.error("║ Error: 401 Unauthorized (Invalid API Key)                   ║");
                log.error("║                                                             ║");
                log.error("║ CHECK:                                                       ║");
                log.error("║ 1. Is SENDGRID_API_KEY set in Render?                       ║");
                log.error("║ 2. Does it start with 'SG.'?                                ║");
                log.error("║ 3. Is it copied correctly (no spaces/line breaks)?           ║");
                log.error("║                                                             ║");
                log.error("║ Guide: See SENDGRID_SETUP_GUIDE.md Step 3-4                 ║");
                log.error("╚════════════════════════════════════════════════════════════╝");
            }

            if (attempt < MAX_RETRY_ATTEMPTS) {
                try {
                    // Wait before retry (exponential backoff: 1s, 2s, 3s)
                    long waitMillis = 1000L * attempt;
                    log.info("⏳ Waiting {}ms before retry attempt {}", waitMillis, attempt + 1);
                    Thread.sleep(waitMillis);
                    return sendEmailWithRetry(toEmail, subject, htmlContent, attempt + 1);
                } catch (InterruptedException ie) {
                    log.error("❌ Retry interrupted: {}", ie.getMessage());
                    Thread.currentThread().interrupt();
                    return false;
                }
            } else {
                log.error("❌ Failed to send email after {} attempts: {}", MAX_RETRY_ATTEMPTS, e.getMessage());
                log.warn("⚠️  Email delivery failed but OTP is available in Firestore for manual verification");
                return false;
            }
        }
    }

    /**
     * Build SendGrid Mail object as JSON
     */
    private Map<String, Object> buildSendGridMailObject(String toEmail, String subject, String htmlContent) {
        Map<String, Object> mailObject = new HashMap<>();

        // From address
        Map<String, String> from = new HashMap<>();
        from.put("email", fromEmail);
        mailObject.put("from", from);

        // Subject
        mailObject.put("subject", subject);

        // Content
        Map<String, String> content = new HashMap<>();
        content.put("type", "text/html");
        content.put("value", htmlContent);
        mailObject.put("content", new Object[]{content});

        // Personalization (recipient)
        Map<String, Object> personalization = new HashMap<>();
        Map<String, String> toMap = new HashMap<>();
        toMap.put("email", toEmail);
        personalization.put("to", new Object[]{toMap});
        mailObject.put("personalizations", new Object[]{personalization});

        return mailObject;
    }
}
