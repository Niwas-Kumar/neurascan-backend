package com.ai.learningdetection.config;

import com.ai.learningdetection.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements CommandLineRunner {

    private final AdminService adminService;

    @Value("${admin.default.email:admin@neurascan.com}")
    private String adminEmail;

    @Value("${admin.default.password:Admin@123}")
    private String adminPassword;

    @Value("${admin.default.name:NeuraScan Admin}")
    private String adminName;

    @Override
    public void run(String... args) {
        try {
            adminService.seedAdminIfEmpty(adminEmail, adminPassword, adminName);
        } catch (Exception e) {
            log.warn("Admin seeding skipped: {}", e.getMessage());
        }
    }
}
