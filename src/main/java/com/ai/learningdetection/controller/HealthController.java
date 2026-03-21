package com.ai.learningdetection.controller;

import com.ai.learningdetection.dto.ApiResponse;
import com.ai.learningdetection.dto.HealthDTO;
import com.ai.learningdetection.service.AiIntegrationService;
import com.google.cloud.firestore.Firestore;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final AiIntegrationService aiIntegrationService;
    private final Firestore firestore;

    @Value("${feature.consent.enabled:false}")
    private boolean consentEnabled;

    @Value("${feature.risk.explanation.enabled:false}")
    private boolean riskExplanationEnabled;

    @Value("${feature.teacher.recommendations.enabled:false}")
    private boolean teacherRecommendationsEnabled;

    @Value("${feature.export.auditlog.enabled:false}")
    private boolean exportAuditLogEnabled;

    @Value("${feature.offline.mode.enabled:false}")
    private boolean offlineModeEnabled;

    @Value("${feature.admin.panel.enabled:false}")
    private boolean adminPanelEnabled;

    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @GetMapping("/api/v1/health")
    public ResponseEntity<ApiResponse<HealthDTO>> getHealth() {
        String dbStatus = "UNKNOWN";

        try {
            // lightweight Firestore check
            firestore.collection("verification_tokens").limit(1).get().get();
            dbStatus = "UP";
        } catch (Exception ex) {
            dbStatus = "DOWN";
        }

        String aiStatus = aiIntegrationService.isServiceReachable() ? "UP" : "DOWN";

        Map<String, String> subsystems = new HashMap<>();
        subsystems.put("firestore", dbStatus);
        subsystems.put("aiService", aiStatus);

        Map<String, Boolean> features = new HashMap<>();
        features.put("consent", consentEnabled);
        features.put("riskExplanation", riskExplanationEnabled);
        features.put("teacherRecommendations", teacherRecommendationsEnabled);
        features.put("exportAuditLog", exportAuditLogEnabled);
        features.put("offlineMode", offlineModeEnabled);
        features.put("adminPanel", adminPanelEnabled);

        HealthDTO healthDTO = HealthDTO.builder()
                .status("UP")
                .subsystem(subsystems)
                .features(features)
                .build();

        if ("DOWN".equals(dbStatus) || "DOWN".equals(aiStatus)) {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("One or more subsystems are DOWN"));
        }

        return ResponseEntity.ok(ApiResponse.success(healthDTO, "Application health OK"));
    }
}
