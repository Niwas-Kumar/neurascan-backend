package com.ai.learningdetection.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {
    private String id;
    private String schoolId;
    private String plan;           // FREE, BASIC, PRO, ENTERPRISE
    private String stripeCustomerId;
    private String stripeSubscriptionId;
    private String status;         // ACTIVE, CANCELED, PAST_DUE, TRIALING
    private int maxTeachers;
    private int maxStudents;
    private int maxAnalysesPerMonth;
    private boolean pdfReports;
    private boolean csvImport;
    private boolean advancedAnalytics;
    private String currentPeriodStart;
    private String currentPeriodEnd;
    private String createdAt;
    private String updatedAt;
}
