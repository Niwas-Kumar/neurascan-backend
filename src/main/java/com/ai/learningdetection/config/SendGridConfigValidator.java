package com.ai.learningdetection.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SendGridConfigValidator implements ApplicationRunner {

    @Value("${sendgrid.api.key:}")
    private String sendGridApiKey;

    @Value("${sendgrid.from.email:}")
    private String fromEmail;

    @Override
    public void run(ApplicationArguments args) {
        log.info("\n");
        log.info("════════════════════════════════════════════════════════════════");
        log.info("🔍 SendGrid Configuration Check");
        log.info("════════════════════════════════════════════════════════════════");

        // Check API Key
        if (sendGridApiKey == null || sendGridApiKey.isEmpty()) {
            log.warn("❌ SENDGRID_API_KEY not set");
            log.warn("   Email delivery DISABLED (dev mode)");
            log.warn("   All OTP codes will be saved to Firestore only");
        } else if (!sendGridApiKey.startsWith("SG.")) {
            log.error("❌ SENDGRID_API_KEY invalid format");
            log.error("   Expected format: SG.xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
            log.error("   Current format: {}", sendGridApiKey.substring(0, Math.min(10, sendGridApiKey.length())) + "...");
        } else {
            log.info("✅ SENDGRID_API_KEY configured");
            int keyLength = sendGridApiKey.length();
            log.info("   Key length: {} chars", keyLength);
        }

        // Check From Email
        if (fromEmail == null || fromEmail.isEmpty()) {
            log.warn("⚠️  SENDGRID_FROM_EMAIL not set, using default: hello.neurascan@gmail.com");
        } else {
            log.info("✅ SENDGRID_FROM_EMAIL configured: {}", fromEmail);
        }

        // Summary
        if ((sendGridApiKey == null || sendGridApiKey.isEmpty()) || 
            (fromEmail == null || fromEmail.isEmpty())) {
            log.warn("\n📋 To enable email delivery:");
            log.warn("   1. Create SendGrid account: https://sendgrid.com/free");
            log.warn("   2. Verify sender: hello.neurascan@gmail.com in SendGrid dashboard");
            log.warn("   3. Generate API key (must start with SG.)");
            log.warn("   4. Set environment variables:");
            log.warn("      - SENDGRID_API_KEY=SG.xxxxxxxxxxxxx");
            log.warn("      - SENDGRID_FROM_EMAIL=hello.neurascan@gmail.com");
            log.warn("   5. Restart application");
            log.warn("   📖 See: SENDGRID_SETUP_GUIDE.md");
        } else if (sendGridApiKey.startsWith("SG.")) {
            log.info("\n✅ Email delivery ENABLED via SendGrid");
            log.info("   OTP codes will be sent to user email addresses");
        }

        log.info("════════════════════════════════════════════════════════════════");
        log.info("\n");
    }
}
