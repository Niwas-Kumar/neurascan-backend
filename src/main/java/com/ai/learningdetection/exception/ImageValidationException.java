package com.ai.learningdetection.exception;

public class ImageValidationException extends RuntimeException {
    private final String reason;
    private final String userMessage;
    private final double confidence;

    public ImageValidationException(String reason, String userMessage, double confidence) {
        super(reason);
        this.reason = reason;
        this.userMessage = userMessage;
        this.confidence = confidence;
    }

    public ImageValidationException(String reason) {
        this(reason, "Please upload a clear image of handwriting on paper.", 0.0);
    }

    public String getReason() { return reason; }
    public String getUserMessage() { return userMessage; }
    public double getConfidence() { return confidence; }
}
