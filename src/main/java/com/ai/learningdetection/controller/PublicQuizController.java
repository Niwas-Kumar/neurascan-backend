package com.ai.learningdetection.controller;

import com.ai.learningdetection.dto.ApiResponse;
import com.ai.learningdetection.dto.QuizDTOs;
import com.ai.learningdetection.entity.Quiz;
import com.ai.learningdetection.entity.QuizLink;
import com.ai.learningdetection.exception.ResourceNotFoundException;
import com.ai.learningdetection.service.QuizAttemptService;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Public endpoints for quiz attempts - no authentication required.
 * Access is controlled via secure tokens sent in email links.
 */
@RestController
@RequestMapping({"/api/quiz-attempt", "/quiz-attempt"})
@RequiredArgsConstructor
@Slf4j
public class PublicQuizController {

    private final Firestore firestore;
    private final QuizAttemptService quizAttemptService;

    private static final String QUIZ_LINKS_COLLECTION = "quiz_links";
    private static final String QUIZZES_COLLECTION = "quizzes";

        /**
         * GET /api/quiz-attempt/validate?quizId=xxx
     * Validates a quiz link token and returns quiz details if valid.
     * Called when user first opens the quiz attempt page.
     */
    @GetMapping("/validate")
    public ResponseEntity<ApiResponse<QuizDTOs.QuizAttemptStartResponse>> validateQuizLink(
            @RequestParam String quizId,
                        @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        try {
                        String token = extractBearerToken(authorizationHeader);
            log.info("🔐 Validating quiz link: quizId={}, token={}", quizId, token.substring(0, 8) + "...");

            // Find quiz link by token
            QuerySnapshot linkQuery = firestore.collection(QUIZ_LINKS_COLLECTION)
                    .whereEqualTo("quizId", quizId)
                    .whereEqualTo("token", token)
                    .get().get();

            if (linkQuery.isEmpty()) {
                log.warn("❌ Invalid quiz link token");
                                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Invalid or expired quiz link. Please request a new link from your teacher."));
            }

            DocumentSnapshot linkDoc = linkQuery.getDocuments().get(0);
            QuizLink link = linkDoc.toObject(QuizLink.class);

            // Check if expired
            if (link.isExpired() || (link.getExpiredAt() != null && link.getExpiredAt().before(new Date()))) {
                log.warn("❌ Quiz link has expired");
                                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("This quiz link has expired. Please request a new link from your teacher."));
            }

            // Check if already attempted (single-attempt enforcement)
            if (link.getQuizAttemptId() != null && !link.getQuizAttemptId().isBlank()) {
                DocumentSnapshot existingAttempt = firestore.collection("quiz_attempts")
                        .document(link.getQuizAttemptId()).get().get();
                if (existingAttempt.exists()) {
                    Boolean isCompleted = existingAttempt.getBoolean("isCompleted");
                    if (Boolean.TRUE.equals(isCompleted)) {
                        Double score = existingAttempt.getDouble("score");
                        log.info("⚠️ Quiz already attempted by: {}", link.getRecipientEmail());
                        QuizDTOs.QuizAttemptStartResponse alreadyDone = QuizDTOs.QuizAttemptStartResponse.builder()
                                .quizId(quizId)
                                .valid(false)
                                .alreadyAttempted(true)
                                .completedScore(score)
                                .message("You have already completed this quiz. Only one attempt is allowed.")
                                .build();
                        return ResponseEntity.ok(ApiResponse.success(alreadyDone, "Quiz already attempted"));
                    }
                }
            }

            // Get quiz details
            DocumentSnapshot quizSnap = firestore.collection(QUIZZES_COLLECTION)
                    .document(quizId).get().get();

            if (!quizSnap.exists()) {
                throw new ResourceNotFoundException("Quiz", "id", quizId);
            }

            Quiz quiz = quizSnap.toObject(Quiz.class);

            // Convert questions (hide correct answers for the attempt)
            List<QuizDTOs.QuizQuestionPublic> questions = quiz.getQuestions().stream()
                    .map(q -> QuizDTOs.QuizQuestionPublic.builder()
                            .id(q.getId())
                            .question(q.getQuestion())
                            .options(q.getOptions())
                            .build())
                    .collect(Collectors.toList());

            QuizDTOs.QuizAttemptStartResponse response = QuizDTOs.QuizAttemptStartResponse.builder()
                    .quizId(quiz.getId())
                    .topic(quiz.getTopic())
                    .totalQuestions(quiz.getQuestions().size())
                    .questions(questions)
                    .recipientType(link.getRecipientType())
                    .recipientEmail(link.getRecipientEmail())
                    .studentId(link.getRecipientId())
                    .valid(true)
                    .build();

            log.info("✅ Quiz link validated successfully for: {}", link.getRecipientEmail());
            return ResponseEntity.ok(ApiResponse.success(response, "Quiz link is valid"));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("❌ Error validating quiz link: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Could not validate quiz link: " + e.getMessage()));
        }
    }

    /**
     * POST /api/quiz-attempt/start
     * Starts a new quiz attempt session.
     */
    @PostMapping("/start")
    public ResponseEntity<ApiResponse<QuizDTOs.QuizAttemptDetail>> startAttempt(
            @RequestBody QuizDTOs.QuizAttemptStartRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        try {
            String token = extractBearerToken(authorizationHeader);
            log.info("🚀 Starting quiz attempt: quizId={}", request.getQuizId());

            // Validate token first
            QuerySnapshot linkQuery = firestore.collection(QUIZ_LINKS_COLLECTION)
                    .whereEqualTo("quizId", request.getQuizId())
                    .whereEqualTo("token", token)
                    .get().get();

            if (linkQuery.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Invalid quiz link token"));
            }

            DocumentSnapshot linkDoc = linkQuery.getDocuments().get(0);
            QuizLink link = linkDoc.toObject(QuizLink.class);

            // Start the attempt
            QuizDTOs.QuizAttemptDetail attempt = quizAttemptService.startQuizAttempt(
                    request.getQuizId(),
                    link.getRecipientId(),
                    link.getRecipientType(),
                    token
            );

            log.info("✅ Quiz attempt started: attemptId={}", attempt.getId());
            return ResponseEntity.ok(ApiResponse.success(attempt, "Quiz attempt started"));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("❌ Error starting quiz attempt: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Could not start quiz: " + e.getMessage()));
        }
    }

    /**
     * POST /api/quiz-attempt/{attemptId}/answer
     * Submit an answer to a single question.
     */
    @PostMapping("/{attemptId}/answer")
    public ResponseEntity<ApiResponse<QuizDTOs.QuestionResponseDetail>> submitAnswer(
            @PathVariable String attemptId,
            @RequestBody QuizDTOs.QuestionResponseRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        try {
            String token = extractBearerToken(authorizationHeader);
            request.setQuizAttemptId(attemptId);
            QuizDTOs.QuestionResponseDetail response = quizAttemptService.submitQuestionResponse(
                    attemptId,
                    request,
                    null,
                    null,
                    token);

            log.info("📝 Answer submitted: attemptId={}, questionId={}, correct={}",
                    attemptId, request.getQuestionId(), response.isCorrect());

            return ResponseEntity.ok(ApiResponse.success(response, "Answer recorded"));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("❌ Error submitting answer: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Could not submit answer: " + e.getMessage()));
        }
    }

    /**
     * POST /api/quiz-attempt/{attemptId}/complete
     * Complete the quiz attempt and get final results.
     */
    @PostMapping("/{attemptId}/complete")
    public ResponseEntity<ApiResponse<QuizDTOs.QuizAttemptDetail>> completeAttempt(
            @PathVariable String attemptId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        try {
            String token = extractBearerToken(authorizationHeader);
            QuizDTOs.QuizAttemptDetail result = quizAttemptService.completeQuizAttempt(
                    attemptId,
                    null,
                    null,
                    token);

            log.info("✅ Quiz completed: attemptId={}, score={}", attemptId, result.getScore());
            return ResponseEntity.ok(ApiResponse.success(result, "Quiz completed successfully!"));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("❌ Error completing quiz: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Could not complete quiz: " + e.getMessage()));
        }
    }

    /**
     * GET /api/quiz-attempt/{attemptId}/result
     * Get the result of a completed quiz attempt.
     */
    @GetMapping("/{attemptId}/result")
    public ResponseEntity<ApiResponse<QuizDTOs.QuizAttemptDetail>> getAttemptResult(
            @PathVariable String attemptId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        try {
            String token = extractBearerToken(authorizationHeader);
            QuizDTOs.QuizAttemptDetail result = quizAttemptService.getQuizAttemptDetail(
                    attemptId,
                    null,
                    null,
                    token);
            return ResponseEntity.ok(ApiResponse.success(result, "Quiz result retrieved"));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("❌ Error fetching quiz result: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Could not fetch quiz result: " + e.getMessage()));
        }
    }

    // Security: token must come only from Authorization Bearer header, never query/body.
    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new BadCredentialsException("Missing or invalid Authorization header");
        }
        String token = authorizationHeader.substring(7).trim();
        if (token.isEmpty()) {
            throw new BadCredentialsException("Missing or invalid Authorization header");
        }
        return token;
    }
}
