package com.ai.learningdetection.service;

import com.ai.learningdetection.dto.AnalysisDTOs;
import com.ai.learningdetection.exception.AiServiceException;
import com.ai.learningdetection.exception.ImageValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiIntegrationService {

    @Value("${ai.service.url}")
    private String aiServiceUrl;

    private final RestTemplate restTemplate;

    /**
     * Sends the uploaded file to the Python AI microservice for analysis.
     * Returns a parsed AiServiceResponse containing scores and analysis text.
     */
    public AnalysisDTOs.AiServiceResponse analyzeFile(Path filePath) {
        log.info("Sending file to AI service: {}", filePath.getFileName());

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(filePath));

            HttpEntity<MultiValueMap<String, Object>> requestEntity =
                    new HttpEntity<>(body, headers);

            ResponseEntity<AnalysisDTOs.AiServiceResponse> response = restTemplate.exchange(
                    aiServiceUrl,
                    HttpMethod.POST,
                    requestEntity,
                    AnalysisDTOs.AiServiceResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                AnalysisDTOs.AiServiceResponse aiResponse = response.getBody();
                log.info("AI analysis complete — dyslexia: {}, dysgraphia: {}",
                        aiResponse.getDyslexia_score(), aiResponse.getDysgraphia_score());
                validateAiResponse(aiResponse);
                return aiResponse;
            } else {
                throw new AiServiceException(
                        "AI service returned status: " + response.getStatusCode());
            }

        } catch (ResourceAccessException ex) {
            log.error("Cannot reach AI service at {}: {}", aiServiceUrl, ex.getMessage());
            throw new AiServiceException(
                    "AI service is not reachable. Please ensure it is running at " + aiServiceUrl, ex);
        } catch (HttpClientErrorException ex) {
            // Handle 400 Bad Request - likely validation error
            if (ex.getStatusCode() == HttpStatus.BAD_REQUEST) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, Object> errorBody = mapper.readValue(ex.getResponseBodyAsString(), Map.class);

                    if (Boolean.TRUE.equals(errorBody.get("validation_error"))) {
                        String reason = (String) errorBody.getOrDefault("reason", "Invalid image");
                        String message = (String) errorBody.getOrDefault("message",
                                "Please upload a clear image of handwriting on paper.");
                        double confidence = errorBody.get("confidence") != null
                                ? ((Number) errorBody.get("confidence")).doubleValue() : 0.0;

                        log.warn("Image validation failed: {} (confidence: {}%)", reason, confidence);
                        throw new ImageValidationException(reason, message, confidence);
                    }
                } catch (ImageValidationException ive) {
                    throw ive;
                } catch (Exception parseEx) {
                    log.warn("Could not parse validation error response: {}", parseEx.getMessage());
                }
            }
            throw new AiServiceException("AI service returned error: " + ex.getStatusCode(), ex);
        } catch (AiServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error calling AI service: {}", ex.getMessage(), ex);
            throw new AiServiceException("Error communicating with AI analysis service.", ex);
        }
    }

    /**
     * Fallback: returns mock analysis when AI service is unavailable (for development/testing).
     */
    public AnalysisDTOs.AiServiceResponse getMockAnalysis() {
        log.warn("Using MOCK AI analysis — AI service not available");
        AnalysisDTOs.AiServiceResponse mock = new AnalysisDTOs.AiServiceResponse();
        mock.setDyslexia_score(0.0);
        mock.setDysgraphia_score(0.0);
        mock.setAnalysis("Mock analysis — AI service was not reachable. Please re-analyze when the service is available.");
        return mock;
    }

    private void validateAiResponse(AnalysisDTOs.AiServiceResponse response) {
        if (response.getDyslexia_score() == null) response.setDyslexia_score(0.0);
        if (response.getDysgraphia_score() == null) response.setDysgraphia_score(0.0);
        if (response.getAnalysis() == null || response.getAnalysis().isBlank()) {
            response.setAnalysis("Analysis complete.");
        }
        // Clamp scores to 0-100
        response.setDyslexia_score(Math.max(0, Math.min(100, response.getDyslexia_score())));
        response.setDysgraphia_score(Math.max(0, Math.min(100, response.getDysgraphia_score())));
    }

    /**
     * Basic connectivity check to the AI service endpoint.
     * Returns true if the AI service host is reachable (any status code).
     */
    public boolean isServiceReachable() {
        try {
            // For a URL intended for POST, a GET/HEAD may return 405 but still indicates network reachability.
            ResponseEntity<String> response = restTemplate.getForEntity(aiServiceUrl, String.class);
            log.info("AI service alive response code={}", response.getStatusCode());
            return true;
        } catch (Exception ex) {
            log.warn("AI health check failed for {}: {}", aiServiceUrl, ex.getMessage());
            return false;
        }
    }

    public AnalysisDTOs.AiServiceResponse analyzeFileWithExternalModel(Path filePath) {
        String externalUrl = aiServiceUrl.replaceAll("/analyze$", "/analyze/external");
        if (!externalUrl.contains("/analyze/external")) {
            externalUrl = aiServiceUrl + "/external";
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(filePath));

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<AnalysisDTOs.AiServiceResponse> response = restTemplate.exchange(
                    externalUrl,
                    HttpMethod.POST,
                    requestEntity,
                    AnalysisDTOs.AiServiceResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                AnalysisDTOs.AiServiceResponse aiResponse = response.getBody();
                validateAiResponse(aiResponse);
                return aiResponse;
            } else {
                log.warn("External AI analysis service returned status: {}", response.getStatusCode());
                return getMockAnalysis();
            }

        } catch (HttpClientErrorException ex) {
            // Handle 400 Bad Request - validation error from AI service
            if (ex.getStatusCode() == HttpStatus.BAD_REQUEST) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, Object> errorBody = mapper.readValue(ex.getResponseBodyAsString(), Map.class);

                    if (Boolean.TRUE.equals(errorBody.get("validation_error"))) {
                        String reason = (String) errorBody.getOrDefault("reason", "Invalid image");
                        String message = (String) errorBody.getOrDefault("message",
                                "Please upload a clear image of handwriting on paper.");
                        double confidence = errorBody.get("confidence") != null
                                ? ((Number) errorBody.get("confidence")).doubleValue() : 0.0;

                        log.warn("Image validation failed: {} (confidence: {}%)", reason, confidence);
                        throw new ImageValidationException(reason, message, confidence);
                    }
                } catch (ImageValidationException ive) {
                    throw ive;
                } catch (Exception parseEx) {
                    log.warn("Could not parse validation error response: {}", parseEx.getMessage());
                }
            }
            log.warn("External AI service returned error: {}", ex.getStatusCode());
            throw new AiServiceException("AI service returned error: " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            log.warn("Cannot reach external AI service: {}", ex.getMessage());
            return getMockAnalysis();
        } catch (ImageValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Failed to call external AI analysis endpoint: {}", ex.getMessage());
            return getMockAnalysis();
        }
    }

    public java.util.Map<String, Object> generateQuizFromText(String topic, String text, int questionCount) {
        String quizUrl = aiServiceUrl.replaceAll("/analyze$", "/quiz/generate");
        if (!quizUrl.contains("/quiz/generate")) {
            quizUrl = aiServiceUrl + "/quiz/generate";
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("topic", topic);
            body.put("text", text);
            body.put("question_count", questionCount);

            HttpEntity<java.util.Map<String, Object>> requestEntity =
                    new HttpEntity<>(body, headers);

            ResponseEntity<java.util.Map> response = restTemplate.postForEntity(
                    quizUrl,
                    requestEntity,
                    java.util.Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }

            log.warn("Quiz generator service returned non-OK: {}", response.getStatusCode());
            return java.util.Map.of("questions", java.util.Collections.emptyList());

        } catch (Exception ex) {
            log.warn("Quiz generation failed, fallback empty quiz: {}", ex.getMessage());
            return java.util.Map.of("questions", java.util.Collections.emptyList());
        }
    }
}


