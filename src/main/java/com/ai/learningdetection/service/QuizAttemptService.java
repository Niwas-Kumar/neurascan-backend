package com.ai.learningdetection.service;

import com.ai.learningdetection.dto.QuizDTOs;
import com.ai.learningdetection.entity.QuestionResponse;
import com.ai.learningdetection.entity.Quiz;
import com.ai.learningdetection.entity.QuizAttempt;
import com.ai.learningdetection.entity.QuizLink;
import com.ai.learningdetection.exception.ResourceNotFoundException;
import com.ai.learningdetection.exception.UnauthorizedAccessException;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Service for managing quiz attempt sessions and tracking per-question metrics.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuizAttemptService {

    private final Firestore firestore;
    private final AiIntegrationService aiIntegrationService;

    private static final String QUIZ_ATTEMPTS_COLLECTION = "quiz_attempts";
    private static final String QUESTION_RESPONSES_COLLECTION = "question_responses";
    private static final String QUIZ_LINKS_COLLECTION = "quiz_links";
    private static final String QUIZZES_COLLECTION = "quizzes";

    /**
     * Start a new quiz attempt session.
     * Creates a QuizAttempt record and returns attempt details including quiz questions.
     */
    public QuizDTOs.QuizAttemptDetail startQuizAttempt(
            String quizId, 
            String recipientId, 
            String recipientType, // "STUDENT" or "PARENT"
            String token) throws ExecutionException, InterruptedException {
        
        try {
            // Verify quiz exists and get quiz questions
            DocumentSnapshot quizSnap = firestore.collection(QUIZZES_COLLECTION)
                    .document(quizId).get().get();
            
            if (!quizSnap.exists()) {
                throw new ResourceNotFoundException("Quiz", "id", quizId);
            }
            
            Quiz quiz = quizSnap.toObject(Quiz.class);
            
            // Verify quiz link is valid
            validateQuizLink(quizId, token);

            // ── Single-attempt enforcement ──
            // Use single-field query on attemptToken (auto-indexed, no composite index needed)
            QuerySnapshot existingAttempts = firestore.collection(QUIZ_ATTEMPTS_COLLECTION)
                    .whereEqualTo("attemptToken", token)
                    .get().get();

            for (DocumentSnapshot doc : existingAttempts.getDocuments()) {
                String docQuizId = doc.getString("quizId");
                if (!quizId.equals(docQuizId)) continue;

                Boolean isCompleted = doc.getBoolean("completed");
                if (Boolean.TRUE.equals(isCompleted)) {
                    log.warn("⛔ Blocked duplicate attempt for quiz: {} token: {}...", quizId, token.substring(0, 8));
                    throw new IllegalStateException("This quiz has already been completed. Only one attempt is allowed.");
                } else {
                    // Resume existing in-progress attempt
                    QuizAttempt existing = doc.toObject(QuizAttempt.class);
                    log.info("🔄 Resuming existing attempt: {} for quiz: {}", existing.getId(), quizId);
                    return convertToAttemptDetail(existing);
                }
            }

            // Create new quiz attempt
            DocumentReference attemptRef = firestore.collection(QUIZ_ATTEMPTS_COLLECTION).document();
            
            QuizAttempt attempt = QuizAttempt.builder()
                    .id(attemptRef.getId())
                    .quizId(quizId)
                    .studentId("STUDENT".equals(recipientType) ? recipientId : null)
                    .parentId("PARENT".equals(recipientType) ? recipientId : null)
                    .classId(quiz.getClassId())
                    .attemptToken(token)
                    .startedAt(new Date())
                    .isCompleted(false)
                    .totalQuestions(quiz.getQuestions() != null ? quiz.getQuestions().size() : 0)
                    .correctAnswers(0)
                    .score(0.0)
                    .totalTimeSpentMs(0)
                    .questionResponseIds(new ArrayList<>())
                    .sentToAiModel(false)
                    .build();
            
            attemptRef.set(attempt).get();
            log.info("✅ Quiz attempt started: {} by {} ({})", quizId, recipientId, recipientType);
            
            // Update quiz link with attempt reference
            markQuizLinkAccessed(quizId, token, attemptRef.getId());
            
            // Convert to DTO and include quiz questions
            return QuizDTOs.QuizAttemptDetail.builder()
                    .id(attempt.getId())
                    .quizId(attempt.getQuizId())
                    .studentId(attempt.getStudentId())
                    .parentId(attempt.getParentId())
                    .attemptToken(attempt.getAttemptToken())
                    .startedAt(attempt.getStartedAt())
                    .isCompleted(false)
                    .totalQuestions(attempt.getTotalQuestions())
                    .correctAnswers(0)
                    .score(0.0)
                    .totalTimeSpentMs(0)
                    .build();
                    
        } catch (ExecutionException | InterruptedException e) {
            log.error("❌ Error starting quiz attempt: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Submit a response to a single quiz question.
     * Tracks response time and evaluates correctness.
     */
        public QuizDTOs.QuestionResponseDetail submitQuestionResponse(
            String quizAttemptId,
            QuizDTOs.QuestionResponseRequest request) throws ExecutionException, InterruptedException {
            return submitQuestionResponse(quizAttemptId, request, null, null, null);
        }

        public QuizDTOs.QuestionResponseDetail submitQuestionResponse(
            String quizAttemptId,
            QuizDTOs.QuestionResponseRequest request,
            String requesterId,
            String requesterRole) throws ExecutionException, InterruptedException {
            return submitQuestionResponse(quizAttemptId, request, requesterId, requesterRole, null);
            }

            public QuizDTOs.QuestionResponseDetail submitQuestionResponse(
                String quizAttemptId,
                QuizDTOs.QuestionResponseRequest request,
                String requesterId,
                String requesterRole,
                String accessToken) throws ExecutionException, InterruptedException {
        
        try {
            QuizAttempt attempt = assertRequesterCanAccessAttempt(quizAttemptId, requesterId, requesterRole, accessToken);

            if (attempt.isCompleted()) {
                throw new IllegalStateException("Quiz attempt is already completed");
            }
            
            // Get quiz to find correct answer
            DocumentSnapshot quizSnap = firestore.collection(QUIZZES_COLLECTION)
                    .document(attempt.getQuizId()).get().get();
            Quiz quiz = quizSnap.toObject(Quiz.class);
            
            // Find the question
            Quiz.QuizQuestion question = quiz.getQuestions().stream()
                    .filter(q -> q.getId().equals(request.getQuestionId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Question", "id", request.getQuestionId()));
            
            // Determine if answer is correct
            boolean isCorrect = question.getAnswer().equalsIgnoreCase(request.getStudentAnswer());
            
            // Determine confidence level based on response time
            String confidenceLevel = determineConfidenceLevel(request.getResponseTimeMs());
            
            // Create question response
            DocumentReference responseRef = firestore.collection(QUESTION_RESPONSES_COLLECTION).document();
            
            QuestionResponse qResponse = QuestionResponse.builder()
                    .id(responseRef.getId())
                    .quizAttemptId(quizAttemptId)
                    .quizId(attempt.getQuizId())
                    .questionId(request.getQuestionId())
                    .studentId(attempt.getStudentId() != null ? attempt.getStudentId() : attempt.getParentId())
                    .questionText(question.getQuestion())
                    .correctAnswer(question.getAnswer())
                    .studentAnswer(request.getStudentAnswer())
                    .isCorrect(isCorrect)
                    .responseTimeMs(request.getResponseTimeMs())
                    .answeredAt(new Date())
                    .confidenceLevel(confidenceLevel)
                    .build();
            
            responseRef.set(qResponse).get();
            
            // Update quiz attempt with this response
            attempt.getQuestionResponseIds().add(responseRef.getId());
            attempt.setTotalTimeSpentMs(attempt.getTotalTimeSpentMs() + request.getResponseTimeMs());
            
            if (isCorrect) {
                attempt.setCorrectAnswers(attempt.getCorrectAnswers() + 1);
            }
            
            firestore.collection(QUIZ_ATTEMPTS_COLLECTION)
                    .document(quizAttemptId)
                    .set(attempt)
                    .get();
            
            log.info("📝 Question response recorded: attempt={}, question={}, correct={}", 
                    quizAttemptId, request.getQuestionId(), isCorrect);
            
            return QuizDTOs.QuestionResponseDetail.builder()
                    .id(qResponse.getId())
                    .questionId(qResponse.getQuestionId())
                    .questionText(qResponse.getQuestionText())
                    .correctAnswer(qResponse.getCorrectAnswer())
                    .studentAnswer(qResponse.getStudentAnswer())
                    .isCorrect(isCorrect)
                    .responseTimeMs(qResponse.getResponseTimeMs())
                    .confidenceLevel(confidenceLevel)
                    .build();
                    
        } catch (ExecutionException | InterruptedException e) {
            log.error("❌ Error submitting question response: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Complete the quiz attempt and calculate results.
     * Triggers AI analysis if enabled.
     */
        public QuizDTOs.QuizAttemptDetail completeQuizAttempt(String quizAttemptId)
            throws ExecutionException, InterruptedException {
            return completeQuizAttempt(quizAttemptId, null, null, null);
        }

        public QuizDTOs.QuizAttemptDetail completeQuizAttempt(
            String quizAttemptId,
            String requesterId,
            String requesterRole)
                throws ExecutionException, InterruptedException {
            return completeQuizAttempt(quizAttemptId, requesterId, requesterRole, null);
        }

        public QuizDTOs.QuizAttemptDetail completeQuizAttempt(
                String quizAttemptId,
                String requesterId,
                String requesterRole,
                String accessToken)
            throws ExecutionException, InterruptedException {
        
        try {
            QuizAttempt attempt = assertRequesterCanAccessAttempt(quizAttemptId, requesterId, requesterRole, accessToken);

            if (attempt.isCompleted()) {
                return convertToAttemptDetail(attempt);
            }
            
            // Calculate final score
            double score = attempt.getTotalQuestions() > 0 
                    ? (attempt.getCorrectAnswers() * 100.0) / attempt.getTotalQuestions()
                    : 0.0;
            
            attempt.setScore(score);
            attempt.setCompletedAt(new Date());
            attempt.setCompleted(true);
            
            // Save completion
            firestore.collection(QUIZ_ATTEMPTS_COLLECTION)
                    .document(quizAttemptId)
                    .set(attempt)
                    .get();
            
            // Update quiz statistics
            updateQuizStatistics(attempt.getQuizId(), score);
            
            // Send to AI model for analysis
            analyzeQuizAttempt(attempt);
            
            log.info("✅ Quiz completed: attempt={}, score={}", quizAttemptId, score);
            
            return convertToAttemptDetail(attempt);
            
        } catch (ExecutionException | InterruptedException e) {
            log.error("❌ Error completing quiz: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Get quiz attempt details including all question responses.
     */
        public QuizDTOs.QuizAttemptDetail getQuizAttemptDetail(String quizAttemptId)
            throws ExecutionException, InterruptedException {
            return getQuizAttemptDetail(quizAttemptId, null, null, null);
        }

        public QuizDTOs.QuizAttemptDetail getQuizAttemptDetail(
            String quizAttemptId,
            String requesterId,
            String requesterRole)
                throws ExecutionException, InterruptedException {
            return getQuizAttemptDetail(quizAttemptId, requesterId, requesterRole, null);
        }

        public QuizDTOs.QuizAttemptDetail getQuizAttemptDetail(
                String quizAttemptId,
                String requesterId,
                String requesterRole,
                String accessToken)
            throws ExecutionException, InterruptedException {
        
        try {
            QuizAttempt attempt = assertRequesterCanAccessAttempt(quizAttemptId, requesterId, requesterRole, accessToken);
            
            // Fetch all question responses
            List<QuizDTOs.QuestionResponseDetail> responses = new ArrayList<>();
            if (attempt.getQuestionResponseIds() != null && !attempt.getQuestionResponseIds().isEmpty()) {
                for (String responseId : attempt.getQuestionResponseIds()) {
                    DocumentSnapshot respSnap = firestore.collection(QUESTION_RESPONSES_COLLECTION)
                            .document(responseId).get().get();
                    
                    if (respSnap.exists()) {
                        QuestionResponse qResp = respSnap.toObject(QuestionResponse.class);
                        responses.add(QuizDTOs.QuestionResponseDetail.builder()
                                .id(qResp.getId())
                                .questionId(qResp.getQuestionId())
                                .questionText(qResp.getQuestionText())
                                .correctAnswer(qResp.getCorrectAnswer())
                                .studentAnswer(qResp.getStudentAnswer())
                                .isCorrect(qResp.isCorrect())
                                .responseTimeMs(qResp.getResponseTimeMs())
                                .confidenceLevel(qResp.getConfidenceLevel())
                                .explanationNote(qResp.getExplanationNote())
                                .build());
                    }
                }
            }
            
            return QuizDTOs.QuizAttemptDetail.builder()
                    .id(attempt.getId())
                    .quizId(attempt.getQuizId())
                    .studentId(attempt.getStudentId())
                    .parentId(attempt.getParentId())
                    .attemptToken(attempt.getAttemptToken())
                    .startedAt(attempt.getStartedAt())
                    .completedAt(attempt.getCompletedAt())
                    .isCompleted(attempt.isCompleted())
                    .totalQuestions(attempt.getTotalQuestions())
                    .correctAnswers(attempt.getCorrectAnswers())
                    .score(attempt.getScore())
                    .totalTimeSpentMs(attempt.getTotalTimeSpentMs())
                    .questionResponses(responses)
                    .learningGapSummary(attempt.getLearningGapSummary())
                    .strongAreas(attempt.getStrongAreas())
                    .weakAreas(attempt.getWeakAreas())
                    .build();
                    
        } catch (ExecutionException | InterruptedException e) {
            log.error("❌ Error fetching quiz attempt: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Get all attempts for a student for a specific quiz.
     */
        public List<QuizDTOs.QuizAttemptDetail> getStudentQuizAttempts(
            String studentId,
            String quizId,
            String requesterId,
            String requesterRole)
            throws ExecutionException, InterruptedException {

        try {
            assertRequesterCanAccessStudent(studentId, requesterId, requesterRole);

            QuerySnapshot snapshot = firestore.collection(QUIZ_ATTEMPTS_COLLECTION)
                    .whereEqualTo("studentId", studentId)
                    .whereEqualTo("quizId", quizId)
                    .get().get();

            return snapshot.getDocuments().stream()
                    .map(doc -> doc.toObject(QuizAttempt.class))
                    .map(this::convertToAttemptDetail)
                    .collect(Collectors.toList());

        } catch (ExecutionException | InterruptedException e) {
            log.error("❌ Error fetching student quiz attempts: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Get ALL quiz attempts for a student across all quizzes.
     * Used by parent dashboard to show complete quiz progress.
     * Validates that requester has permission to view this student's data.
     */
    public List<QuizDTOs.QuizAttemptDetail> getAllStudentAttempts(String studentId, String requesterId, String requesterRole)
            throws ExecutionException, InterruptedException {

        try {
            assertRequesterCanAccessStudent(studentId, requesterId, requesterRole);

            // Fetch all quiz attempts for this student
            QuerySnapshot snapshot = firestore.collection(QUIZ_ATTEMPTS_COLLECTION)
                    .whereEqualTo("studentId", studentId)
                    .get().get();

            List<QuizDTOs.QuizAttemptDetail> attempts = new ArrayList<>();

            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                QuizAttempt attempt = doc.toObject(QuizAttempt.class);
                QuizDTOs.QuizAttemptDetail detail = convertToAttemptDetailWithResponses(attempt);

                // Fetch quiz topic if available
                try {
                    DocumentSnapshot quizDoc = firestore.collection(QUIZZES_COLLECTION)
                            .document(attempt.getQuizId()).get().get();
                    if (quizDoc.exists()) {
                        detail.setTopic(quizDoc.getString("topic"));
                    }
                } catch (Exception e) {
                    log.warn("Could not fetch quiz topic: {}", e.getMessage());
                }

                attempts.add(detail);
            }

            // Sort by date, most recent first
            attempts.sort((a, b) -> {
                Date dateA = a.getCompletedAt() != null ? a.getCompletedAt() : a.getStartedAt();
                Date dateB = b.getCompletedAt() != null ? b.getCompletedAt() : b.getStartedAt();
                if (dateA == null && dateB == null) return 0;
                if (dateA == null) return 1;
                if (dateB == null) return -1;
                return dateB.compareTo(dateA);
            });

            log.info("[QUIZ_ATTEMPTS_SUCCESS] Found {} quiz attempts for student: {}", attempts.size(), studentId);
            return attempts;

        } catch (ExecutionException | InterruptedException e) {
            log.error("❌ Error fetching all student quiz attempts: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Convert attempt to detail with question responses included.
     */
    private QuizDTOs.QuizAttemptDetail convertToAttemptDetailWithResponses(QuizAttempt attempt) {
        List<QuizDTOs.QuestionResponseDetail> responses = new ArrayList<>();

        if (attempt.getQuestionResponseIds() != null && !attempt.getQuestionResponseIds().isEmpty()) {
            for (String responseId : attempt.getQuestionResponseIds()) {
                try {
                    DocumentSnapshot respSnap = firestore.collection(QUESTION_RESPONSES_COLLECTION)
                            .document(responseId).get().get();

                    if (respSnap.exists()) {
                        QuestionResponse qResp = respSnap.toObject(QuestionResponse.class);
                        responses.add(QuizDTOs.QuestionResponseDetail.builder()
                                .id(qResp.getId())
                                .questionId(qResp.getQuestionId())
                                .questionText(qResp.getQuestionText())
                                .correctAnswer(qResp.getCorrectAnswer())
                                .studentAnswer(qResp.getStudentAnswer())
                                .isCorrect(qResp.isCorrect())
                                .responseTimeMs(qResp.getResponseTimeMs())
                                .confidenceLevel(qResp.getConfidenceLevel())
                                .explanationNote(qResp.getExplanationNote())
                                .build());
                    }
                } catch (Exception e) {
                    log.warn("Could not fetch response {}: {}", responseId, e.getMessage());
                }
            }
        }

        return QuizDTOs.QuizAttemptDetail.builder()
                .id(attempt.getId())
                .quizId(attempt.getQuizId())
                .studentId(attempt.getStudentId())
                .parentId(attempt.getParentId())
                .attemptToken(attempt.getAttemptToken())
                .startedAt(attempt.getStartedAt())
                .completedAt(attempt.getCompletedAt())
                .isCompleted(attempt.isCompleted())
                .totalQuestions(attempt.getTotalQuestions())
                .correctAnswers(attempt.getCorrectAnswers())
                .score(attempt.getScore())
                .totalTimeSpentMs(attempt.getTotalTimeSpentMs())
                .questionResponses(responses)
                .learningGapSummary(attempt.getLearningGapSummary())
                .strongAreas(attempt.getStrongAreas())
                .weakAreas(attempt.getWeakAreas())
                .build();
    }

    // ============================================================
    // Private Helper Methods
    // ============================================================

    private void validateQuizLink(String quizId, String token) throws ExecutionException, InterruptedException {
        DocumentSnapshot linkSnap = firestore.collection(QUIZ_LINKS_COLLECTION)
                .whereEqualTo("quizId", quizId)
                .whereEqualTo("token", token)
                .get().get()
                .getDocuments()
                .stream()
                .findFirst()
                .map(d -> d)
                .orElse(null);
        
        if (linkSnap == null) {
            throw new RuntimeException("Invalid or expired quiz link");
        }
        
        QuizLink link = linkSnap.toObject(QuizLink.class);
        if (link.isExpired()) {
            throw new RuntimeException("Quiz link has expired");
        }
    }

    private void markQuizLinkAccessed(String quizId, String token, String quizAttemptId) {
        try {
            QuerySnapshot snapshot = firestore.collection(QUIZ_LINKS_COLLECTION)
                    .whereEqualTo("quizId", quizId)
                    .whereEqualTo("token", token)
                    .get().get();
            
            if (!snapshot.isEmpty()) {
                DocumentSnapshot doc = snapshot.getDocuments().get(0);
                QuizLink link = doc.toObject(QuizLink.class);
                link.setFirstAccessAt(new Date());
                link.setAccessCount(link.getAccessCount() + 1);
                link.setQuizAttemptId(quizAttemptId);
                
                doc.getReference().set(link).get();
            }
        } catch (Exception e) {
            log.warn("⚠️ Could not update quiz link access: {}", e.getMessage());
        }
    }

    private String determineConfidenceLevel(long responseTimeMs) {
        if (responseTimeMs < 5000) {
            return "high";      // Answered quickly = high confidence
        } else if (responseTimeMs < 15000) {
            return "medium";    // Moderate time
        } else {
            return "low";       // Took long time = unsure
        }
    }

    private void updateQuizStatistics(String quizId, double score) {
        try {
            DocumentSnapshot quizSnap = firestore.collection(QUIZZES_COLLECTION)
                    .document(quizId).get().get();
            
            if (quizSnap.exists()) {
                Quiz quiz = quizSnap.toObject(Quiz.class);
                
                int totalAttempts = quiz.getTotalAttempts() != 0 ? quiz.getTotalAttempts() + 1 : 1;
                double newAverage = (quiz.getAverageScore() * (totalAttempts - 1) + score) / totalAttempts;
                
                quiz.setTotalAttempts(totalAttempts);
                quiz.setAverageScore(newAverage);
                
                firestore.collection(QUIZZES_COLLECTION)
                        .document(quizId)
                        .set(quiz)
                        .get();
            }
        } catch (Exception e) {
            log.warn("⚠️ Could not update quiz statistics: {}", e.getMessage());
        }
    }

    private void analyzeQuizAttempt(QuizAttempt attempt) {
        try {
            // Send to AI model for analysis
            log.info("🤖 Sending quiz attempt to AI model for analysis: {}", attempt.getId());
            
            // This would integrate with your AI service
            Map<String, Object> analysisData = prepareAnalysisData(attempt);
            // aiIntegrationService.analyzeQuizAttempt(analysisData);
            
            attempt.setSentToAiModel(true);
            attempt.setAiAnalysisDate(new Date());
            
            firestore.collection(QUIZ_ATTEMPTS_COLLECTION)
                    .document(attempt.getId())
                    .set(attempt)
                    .get();
        } catch (Exception e) {
            log.warn("⚠️ Could not send quiz to AI model: {}", e.getMessage());
        }
    }

    private Map<String, Object> prepareAnalysisData(QuizAttempt attempt) {
        Map<String, Object> data = new HashMap<>();
        data.put("attemptId", attempt.getId());
        data.put("quizId", attempt.getQuizId());
        data.put("score", attempt.getScore());
        data.put("correctAnswers", attempt.getCorrectAnswers());
        data.put("totalQuestions", attempt.getTotalQuestions());
        data.put("totalTimeMs", attempt.getTotalTimeSpentMs());
        return data;
    }

    private QuizDTOs.QuizAttemptDetail convertToAttemptDetail(QuizAttempt attempt) {
        return QuizDTOs.QuizAttemptDetail.builder()
                .id(attempt.getId())
                .quizId(attempt.getQuizId())
                .studentId(attempt.getStudentId())
                .parentId(attempt.getParentId())
                .attemptToken(attempt.getAttemptToken())
                .startedAt(attempt.getStartedAt())
                .completedAt(attempt.getCompletedAt())
                .isCompleted(attempt.isCompleted())
                .totalQuestions(attempt.getTotalQuestions())
                .correctAnswers(attempt.getCorrectAnswers())
                .score(attempt.getScore())
                .totalTimeSpentMs(attempt.getTotalTimeSpentMs())
                .learningGapSummary(attempt.getLearningGapSummary())
                .strongAreas(attempt.getStrongAreas())
                .weakAreas(attempt.getWeakAreas())
                .build();
    }

        private QuizAttempt assertRequesterCanAccessAttempt(
            String quizAttemptId,
            String requesterId,
            String requesterRole,
            String accessToken)
            throws ExecutionException, InterruptedException {
        DocumentSnapshot attemptSnap = firestore.collection(QUIZ_ATTEMPTS_COLLECTION)
                .document(quizAttemptId)
                .get().get();

        if (!attemptSnap.exists()) {
            throw new ResourceNotFoundException("QuizAttempt", "id", quizAttemptId);
        }

        QuizAttempt attempt = attemptSnap.toObject(QuizAttempt.class);
        if (attempt == null) {
            throw new ResourceNotFoundException("QuizAttempt", "id", quizAttemptId);
        }

        String normalizedRole = normalizeRole(requesterRole);
        if ("TEACHER".equals(normalizedRole)) {
            DocumentSnapshot quizSnap = firestore.collection(QUIZZES_COLLECTION)
                    .document(attempt.getQuizId())
                    .get().get();
            if (!quizSnap.exists() || !requesterId.equals(quizSnap.getString("teacherId"))) {
                throw new UnauthorizedAccessException("Not authorized to access this quiz attempt");
            }
            return attempt;
        }

        if ("PARENT".equals(normalizedRole)) {
            if (attempt.getParentId() != null && requesterId.equals(attempt.getParentId())) {
                return attempt;
            }
            if (attempt.getStudentId() != null && isParentAuthorizedForStudent(requesterId, attempt.getStudentId())) {
                return attempt;
            }
            throw new UnauthorizedAccessException("Not authorized to access this quiz attempt");
        }

        // Public access is allowed only when the attempt is tied to an active quiz link.
        QuerySnapshot linkQuery = firestore.collection(QUIZ_LINKS_COLLECTION)
                .whereEqualTo("quizAttemptId", quizAttemptId)
                .limit(1)
                .get().get();

        if (linkQuery.isEmpty() && attempt.getAttemptToken() != null) {
            linkQuery = firestore.collection(QUIZ_LINKS_COLLECTION)
                .whereEqualTo("quizId", attempt.getQuizId())
                .whereEqualTo("token", attempt.getAttemptToken())
                .limit(1)
                .get().get();
        }

        if (linkQuery.isEmpty()) {
            throw new BadCredentialsException("Invalid quiz attempt session");
        }

        QuizLink link = linkQuery.getDocuments().get(0).toObject(QuizLink.class);
        if (link == null || link.isExpired() || (link.getExpiredAt() != null && link.getExpiredAt().before(new Date()))) {
            throw new BadCredentialsException("Quiz attempt session has expired");
        }

        if (accessToken == null || accessToken.isBlank() || !Objects.equals(accessToken, link.getToken())) {
            throw new BadCredentialsException("Invalid quiz access token");
        }

        if (!Objects.equals(link.getQuizId(), attempt.getQuizId())) {
            throw new UnauthorizedAccessException("Quiz attempt does not match the access link");
        }

        return attempt;
    }

    private void assertRequesterCanAccessStudent(String studentId, String requesterId, String requesterRole)
            throws ExecutionException, InterruptedException {
        String normalizedRole = normalizeRole(requesterRole);

        if ("TEACHER".equals(normalizedRole)) {
            DocumentSnapshot studentDoc = firestore.collection("students").document(studentId).get().get();
            if (!studentDoc.exists() || !requesterId.equals(studentDoc.getString("teacherId"))) {
                throw new UnauthorizedAccessException("Not authorized to access this student");
            }
            return;
        }

        if ("PARENT".equals(normalizedRole)) {
            if (!isParentAuthorizedForStudent(requesterId, studentId)) {
                throw new UnauthorizedAccessException("You do not have permission to view this student's data. Please connect to this student from your dashboard.");
            }
            return;
        }

        throw new UnauthorizedAccessException("Not authorized");
    }

    private boolean isParentAuthorizedForStudent(String parentId, String studentId)
            throws ExecutionException, InterruptedException {
        QuerySnapshot relationshipQuery = firestore.collection("parent_student_relationships")
                .whereEqualTo("parentId", parentId)
                .whereEqualTo("studentId", studentId)
                .whereEqualTo("verificationStatus", "VERIFIED")
                .limit(1)
                .get().get();

        if (relationshipQuery.isEmpty()) {
            return false;
        }

        DocumentSnapshot rel = relationshipQuery.getDocuments().get(0);
        String disconnectedAt = rel.getString("disconnectedAt");
        return disconnectedAt == null || disconnectedAt.isBlank();
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "";
        }
        return role.startsWith("ROLE_") ? role.substring(5).toUpperCase(Locale.ROOT) : role.toUpperCase(Locale.ROOT);
    }
}
