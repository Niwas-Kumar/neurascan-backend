package com.ai.learningdetection.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * Mail Configuration for Gmail SMTP with TLS/SSL support
 * 
 * REQUIRED ENVIRONMENT VARIABLES:
 * - SPRING_MAIL_USERNAME: Gmail address (e.g., neurascan@gmail.com)
 * - SPRING_MAIL_PASSWORD: Gmail App Password (16-char, NOT regular password)
 *
 * Gmail Setup Instructions:
 * 1. Enable 2-Factor Authentication on your Google account
 * 2. Go to https://myaccount.google.com/apppasswords
 * 3. Select "Mail" and "Windows Computer" (or your app)
 * 4. Generate an App Password (16 characters, excluding spaces)
 * 5. Set SPRING_MAIL_USERNAME and SPRING_MAIL_PASSWORD environment variables
 */
@Configuration
@Slf4j
public class MailConfig {

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${spring.mail.host:smtp.gmail.com}")
    private String mailHost;

    @Value("${spring.mail.port:465}")
    private Integer mailPort;

    /**
     * Create and configure JavaMailSender bean for Gmail SMTP
     * Only creates if credentials are provided
     */
    @Bean
    @ConditionalOnProperty(
        name = "spring.mail.username",
        havingValue = ""
    )
    public JavaMailSender noOpMailSender() {
        log.warn("\n" +
                "╔════════════════════════════════════════════════════════════════╗\n" +
                "║ ⚠️  EMAIL VERIFICATION DISABLED                                ║\n" +
                "╠════════════════════════════════════════════════════════════════╣\n" +
                "║ SPRING_MAIL_USERNAME and SPRING_MAIL_PASSWORD not configured  ║\n" +
                "║                                                                ║\n" +
                "║ Email verification will be SKIPPED. For production:            ║\n" +
                "║ 1. Enable 2FA on your Google account                           ║\n" +
                "║ 2. Generate App Password at:                                  ║\n" +
                "║    https://myaccount.google.com/apppasswords                   ║\n" +
                "║ 3. Set environment variables:                                 ║\n" +
                "║    SPRING_MAIL_USERNAME=your-email@gmail.com                  ║\n" +
                "║    SPRING_MAIL_PASSWORD=xxxx-xxxx-xxxx-xxxx                   ║\n" +
                "║                                                                ║\n" +
                "║ OTP codes will still be generated and stored for testing.      ║\n" +
                "╚════════════════════════════════════════════════════════════════╝\n");
        return new JavaMailSenderImpl() {
            @Override
            public void send(org.springframework.mail.SimpleMailMessage msg) {
                log.warn("Email sending disabled (SMTP not configured). " +
                         "In test mode, OTP is available in Firestore verification_tokens collection.");
            }
        };
    }

    /**
     * Real Gmail SMTP configuration (only if credentials provided)
     * 
     * Supports both:
     * - Port 465: Implicit TLS/SSL (stricter but sometimes blocked by hosting)
     * - Port 587: STARTTLS (more compatible, recommended for cloud/Render)
     */
    @Bean
    @Primary
    @ConditionalOnProperty(
        name = "spring.mail.username",
        matchIfMissing = false
    )
    public JavaMailSender javaMailSender() {
        log.info("Configuring Gmail SMTP mail sender for: {}", mailUsername);
        
        // Try to use port 587 if 465 is specified (better cloud compatibility)
        Integer effectivePort = mailPort;
        boolean usePort587 = false;
        
        if (mailPort == 465) {
            // For cloud environments like Render, port 587 is more reliable
            usePort587 = true;
            effectivePort = 587;
            log.info("ℹ️  Using port 587 (STARTTLS) instead of 465 for better cloud compatibility");
        }
        
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(mailHost);
        mailSender.setPort(effectivePort);
        mailSender.setUsername(mailUsername);
        mailSender.setPassword(mailPassword);

        // Configure mail properties
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.smtp.auth", true);
        
        if (usePort587) {
            // Port 587 uses STARTTLS
            props.put("mail.smtp.starttls.enable", true);
            props.put("mail.smtp.starttls.required", true);
            props.put("mail.smtp.ssl.enable", false);
            log.info("📧 Using STARTTLS protocol (port 587)");
        } else {
            // Port 465 uses implicit TLS
            props.put("mail.smtp.starttls.enable", true);
            props.put("mail.smtp.starttls.required", true);
            props.put("mail.smtp.ssl.enable", true);
            props.put("mail.smtp.socketFactory.port", effectivePort);
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.fallback", false);
            log.info("🔒 Using implicit TLS protocol (port 465)");
        }
        
        // Improved timeout handling (increased for reliability)
        props.put("mail.smtp.connectiontimeout", 30000);   // 30 seconds (increased)
        props.put("mail.smtp.timeout", 30000);             // 30 seconds (increased)
        props.put("mail.smtp.writetimeout", 30000);        // 30 seconds (increased)
        props.put("mail.smtp.ehlo", true);
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");

        log.info("✓ Gmail SMTP configured successfully (host: {}, port: {})", mailHost, effectivePort);
        return mailSender;
    }
}
