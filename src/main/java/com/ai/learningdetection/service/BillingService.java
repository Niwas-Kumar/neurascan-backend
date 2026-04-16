package com.ai.learningdetection.service;

import com.ai.learningdetection.entity.Subscription;
import com.google.cloud.firestore.*;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {

    private final Firestore firestore;
    private static final String SUBSCRIPTIONS_COLLECTION = "subscriptions";

    @Value("${stripe.secret-key:}")
    private String stripeSecretKey;

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    // Price IDs from Stripe Dashboard
    @Value("${stripe.price.basic:}")
    private String basicPriceId;

    @Value("${stripe.price.pro:}")
    private String proPriceId;

    @Value("${stripe.price.enterprise:}")
    private String enterprisePriceId;

    @PostConstruct
    public void init() {
        if (stripeSecretKey != null && !stripeSecretKey.isBlank()) {
            Stripe.apiKey = stripeSecretKey;
            log.info("Stripe API initialized");
        } else {
            log.warn("Stripe secret key not configured. Billing features disabled.");
        }
    }

    public static final Map<String, Map<String, Object>> PLANS = Map.of(
            "FREE", Map.of(
                    "maxTeachers", 2, "maxStudents", 25, "maxAnalysesPerMonth", 50,
                    "pdfReports", false, "csvImport", false, "advancedAnalytics", false
            ),
            "BASIC", Map.of(
                    "maxTeachers", 10, "maxStudents", 200, "maxAnalysesPerMonth", 500,
                    "pdfReports", true, "csvImport", true, "advancedAnalytics", false
            ),
            "PRO", Map.of(
                    "maxTeachers", 50, "maxStudents", 1000, "maxAnalysesPerMonth", 5000,
                    "pdfReports", true, "csvImport", true, "advancedAnalytics", true
            ),
            "ENTERPRISE", Map.of(
                    "maxTeachers", -1, "maxStudents", -1, "maxAnalysesPerMonth", -1,
                    "pdfReports", true, "csvImport", true, "advancedAnalytics", true
            )
    );

    public Subscription getOrCreateSubscription(String schoolId) {
        try {
            QuerySnapshot query = firestore.collection(SUBSCRIPTIONS_COLLECTION)
                    .whereEqualTo("schoolId", schoolId)
                    .limit(1)
                    .get().get();

            if (!query.isEmpty()) {
                return query.getDocuments().get(0).toObject(Subscription.class);
            }

            // Create free plan subscription
            String now = Instant.now().toString();
            DocumentReference docRef = firestore.collection(SUBSCRIPTIONS_COLLECTION).document();
            Subscription sub = Subscription.builder()
                    .id(docRef.getId())
                    .schoolId(schoolId)
                    .plan("FREE")
                    .status("ACTIVE")
                    .maxTeachers(2)
                    .maxStudents(25)
                    .maxAnalysesPerMonth(50)
                    .pdfReports(false)
                    .csvImport(false)
                    .advancedAnalytics(false)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            docRef.set(sub).get();
            log.info("Created FREE subscription for school: {}", schoolId);
            return sub;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error managing subscription", e);
        }
    }

    public String createCheckoutSession(String schoolId, String plan, String email) throws StripeException {
        if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
            throw new RuntimeException("Stripe is not configured");
        }

        String priceId = switch (plan.toUpperCase()) {
            case "BASIC" -> basicPriceId;
            case "PRO" -> proPriceId;
            case "ENTERPRISE" -> enterprisePriceId;
            default -> throw new IllegalArgumentException("Invalid plan: " + plan);
        };

        if (priceId == null || priceId.isBlank()) {
            throw new RuntimeException("Price ID not configured for plan: " + plan);
        }

        // Get or create Stripe customer
        Subscription sub = getOrCreateSubscription(schoolId);
        String customerId = sub.getStripeCustomerId();
        if (customerId == null || customerId.isBlank()) {
            Customer customer = Customer.create(
                    CustomerCreateParams.builder()
                            .setEmail(email)
                            .putMetadata("schoolId", schoolId)
                            .build()
            );
            customerId = customer.getId();

            // Update subscription with customer ID
            try {
                QuerySnapshot query = firestore.collection(SUBSCRIPTIONS_COLLECTION)
                        .whereEqualTo("schoolId", schoolId).limit(1).get().get();
                if (!query.isEmpty()) {
                    query.getDocuments().get(0).getReference().update("stripeCustomerId", customerId);
                }
            } catch (InterruptedException | ExecutionException e) {
                log.warn("Failed to save Stripe customer ID", e);
            }
        }

        Session session = Session.create(
                SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                        .setCustomer(customerId)
                        .setSuccessUrl(frontendUrl + "/teacher/settings?billing=success")
                        .setCancelUrl(frontendUrl + "/teacher/settings?billing=canceled")
                        .addLineItem(
                                SessionCreateParams.LineItem.builder()
                                        .setPrice(priceId)
                                        .setQuantity(1L)
                                        .build()
                        )
                        .putMetadata("schoolId", schoolId)
                        .putMetadata("plan", plan.toUpperCase())
                        .build()
        );

        return session.getUrl();
    }

    public void handleSubscriptionUpdate(String schoolId, String plan, String stripeSubscriptionId,
                                          String status, Long periodStart, Long periodEnd) {
        try {
            Map<String, Object> planLimits = PLANS.getOrDefault(plan, PLANS.get("FREE"));

            QuerySnapshot query = firestore.collection(SUBSCRIPTIONS_COLLECTION)
                    .whereEqualTo("schoolId", schoolId).limit(1).get().get();

            Map<String, Object> updates = new java.util.HashMap<>();
            updates.put("plan", plan);
            updates.put("stripeSubscriptionId", stripeSubscriptionId);
            updates.put("status", status);
            updates.put("maxTeachers", planLimits.get("maxTeachers"));
            updates.put("maxStudents", planLimits.get("maxStudents"));
            updates.put("maxAnalysesPerMonth", planLimits.get("maxAnalysesPerMonth"));
            updates.put("pdfReports", planLimits.get("pdfReports"));
            updates.put("csvImport", planLimits.get("csvImport"));
            updates.put("advancedAnalytics", planLimits.get("advancedAnalytics"));
            updates.put("updatedAt", Instant.now().toString());
            if (periodStart != null) updates.put("currentPeriodStart", Instant.ofEpochSecond(periodStart).toString());
            if (periodEnd != null) updates.put("currentPeriodEnd", Instant.ofEpochSecond(periodEnd).toString());

            if (!query.isEmpty()) {
                query.getDocuments().get(0).getReference().update(updates);
            } else {
                // Create new subscription
                updates.put("schoolId", schoolId);
                updates.put("createdAt", Instant.now().toString());
                DocumentReference docRef = firestore.collection(SUBSCRIPTIONS_COLLECTION).document();
                updates.put("id", docRef.getId());
                docRef.set(updates).get();
            }

            log.info("Updated subscription for school {}: plan={}, status={}", schoolId, plan, status);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error updating subscription", e);
        }
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }
}
