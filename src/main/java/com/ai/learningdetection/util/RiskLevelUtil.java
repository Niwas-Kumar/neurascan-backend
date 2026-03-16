package com.ai.learningdetection.util;

public class RiskLevelUtil {

    public static final double HIGH_RISK_THRESHOLD = 70.0;
    public static final double MEDIUM_RISK_THRESHOLD = 45.0;

    public static String calculateRiskLevel(Double dyslexiaScore, Double dysgraphiaScore) {
        double maxScore = Math.max(dyslexiaScore != null ? dyslexiaScore : 0,
                                   dysgraphiaScore != null ? dysgraphiaScore : 0);
        if (maxScore >= HIGH_RISK_THRESHOLD) {
            return "HIGH";
        } else if (maxScore >= MEDIUM_RISK_THRESHOLD) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    public static boolean isAtRisk(Double dyslexiaScore, Double dysgraphiaScore) {
        return (dyslexiaScore != null && dyslexiaScore >= MEDIUM_RISK_THRESHOLD)
                || (dysgraphiaScore != null && dysgraphiaScore >= MEDIUM_RISK_THRESHOLD);
    }

    private RiskLevelUtil() {}
}
