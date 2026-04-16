package com.ai.learningdetection.controller;

import com.ai.learningdetection.dto.ApiResponse;
import com.ai.learningdetection.entity.Subscription;
import com.ai.learningdetection.security.IdentifiablePrincipal;
import com.ai.learningdetection.security.TeacherUserDetails;
import com.ai.learningdetection.service.BillingService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/api/billing", "/billing"})
@RequiredArgsConstructor
@Slf4j
public class BillingController {

    private final BillingService billingService;

    @Data
    public static class CheckoutRequest {
        @NotBlank private String plan;
    }

    @GetMapping("/subscription")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<Subscription>> getSubscription(
            @AuthenticationPrincipal IdentifiablePrincipal principal) {

        String schoolId = extractSchoolId(principal);
        if (schoolId == null || schoolId.isBlank()) {
            return ResponseEntity.ok(ApiResponse.error("No school assigned"));
        }

        Subscription sub = billingService.getOrCreateSubscription(schoolId);
        return ResponseEntity.ok(ApiResponse.success(sub));
    }

    @PostMapping("/checkout")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<Map<String, String>>> createCheckout(
            @RequestBody CheckoutRequest request,
            @AuthenticationPrincipal IdentifiablePrincipal principal) {

        String schoolId = extractSchoolId(principal);
        if (schoolId == null || schoolId.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("No school assigned"));
        }

        try {
            String url = billingService.createCheckoutSession(schoolId, request.getPlan(), null);
            return ResponseEntity.ok(ApiResponse.success(Map.of("checkoutUrl", url)));
        } catch (StripeException e) {
            log.error("Stripe checkout error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("Payment error: " + e.getMessage()));
        }
    }

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<Map<String, Map<String, Object>>>> getPlans() {
        return ResponseEntity.ok(ApiResponse.success(BillingService.PLANS));
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        String webhookSecret = billingService.getWebhookSecret();
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("Stripe webhook secret not configured");
            return ResponseEntity.ok("Webhook secret not configured");
        }

        try {
            Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);

            switch (event.getType()) {
                case "checkout.session.completed" -> {
                    var session = (com.stripe.model.checkout.Session) event.getDataObjectDeserializer()
                            .getObject().orElse(null);
                    if (session != null) {
                        String schoolId = session.getMetadata().get("schoolId");
                        String plan = session.getMetadata().get("plan");
                        String subscriptionId = session.getSubscription();
                        billingService.handleSubscriptionUpdate(schoolId, plan, subscriptionId, "ACTIVE", null, null);
                    }
                }
                case "customer.subscription.updated", "customer.subscription.deleted" -> {
                    var subscription = (com.stripe.model.Subscription) event.getDataObjectDeserializer()
                            .getObject().orElse(null);
                    if (subscription != null) {
                        String schoolId = subscription.getMetadata().get("schoolId");
                        String plan = subscription.getMetadata().getOrDefault("plan", "FREE");
                        String status = subscription.getStatus().equalsIgnoreCase("active") ? "ACTIVE" : "CANCELED";
                        billingService.handleSubscriptionUpdate(schoolId, plan, subscription.getId(),
                                status, subscription.getCurrentPeriodStart(), subscription.getCurrentPeriodEnd());
                    }
                }
                default -> log.debug("Unhandled Stripe event: {}", event.getType());
            }

            return ResponseEntity.ok("OK");
        } catch (SignatureVerificationException e) {
            log.error("Invalid Stripe webhook signature");
            return ResponseEntity.badRequest().body("Invalid signature");
        } catch (Exception e) {
            log.error("Webhook processing error: {}", e.getMessage(), e);
            return ResponseEntity.ok("Error processed");
        }
    }

    private String extractSchoolId(IdentifiablePrincipal principal) {
        if (principal instanceof TeacherUserDetails teacherDetails) {
            return teacherDetails.getSchoolId();
        }
        return null;
    }
}
