package com.ai.learningdetection.controller;

import com.ai.learningdetection.dto.ApiResponse;
import com.ai.learningdetection.dto.QuizDTOs;
import com.ai.learningdetection.entity.QuizResponse;
import com.ai.learningdetection.security.IdentifiablePrincipal;
import com.ai.learningdetection.service.QuizService;
import com.ai.learningdetection.service.QuizAttemptService;
import com.ai.learningdetection.service.QuizDistributionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping({"/api/quizzes", "/quizzes"})
@RequiredArgsConstructor
@Slf4j
public class QuizController {

    private final QuizService quizService;
    private final QuizAttemptService quizAttemptService;
    private final QuizDistributionService quizDistributionService;

    // ============================================================
    // EXISTING ENDPOINTS (Created/Retrieved by Teachers)
    // ============================================================

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<QuizDTOs.QuizDetail>> createQuiz(
            @Valid @RequestBody QuizDTOs.QuizGenerationRequest request,
            @AuthenticationPrincipal IdentifiablePrincipal principal) {

        QuizDTOs.QuizDetail result = quizService.createQuiz(principal.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(result, "Quiz created"));
    }

    @GetMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<List<QuizDTOs.QuizDetail>>> getTeacherQuizzes(
            @AuthenticationPrincipal IdentifiablePrincipal principal) {

        List<QuizDTOs.QuizDetail> quizzes = quizService.getQuizzesByTeacher(principal.getId());
        return ResponseEntity.ok(ApiResponse.success(quizzes, "Quizzes retrieved"));
    }

    @GetMapping("/{quizId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<QuizDTOs.QuizDetail>> getQuizById(
            @PathVariable String quizId,
            @AuthenticationPrincipal IdentifiablePrincipal principal) {

        QuizDTOs.QuizDetail quiz = quizService.getQuizById(quizId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(quiz, "Quiz details retrieved"));
    }

    @PostMapping("/{quizId}/submit")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<QuizResponse>> submitQuiz(
            @PathVariable String quizId,
            @Valid @RequestBody QuizDTOs.QuizSubmissionRequest submissionRequest,
            @AuthenticationPrincipal IdentifiablePrincipal principal) {

        submissionRequest.setQuizId(quizId);
        QuizResponse result = quizService.submitQuizResponse(submissionRequest, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(result, "Quiz submitted"));
    }

    @GetMapping("/{quizId}/responses")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<List<QuizResponse>>> getQuizResponses(
            @PathVariable String quizId,
            @AuthenticationPrincipal IdentifiablePrincipal principal) {

        List<QuizResponse> responses = quizService.getQuizResponses(quizId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(responses, "Quiz responses retrieved"));
    }

    @GetMapping("/student/{studentId}/responses")
    @PreAuthorize("hasAnyRole('PARENT','TEACHER')")
    public ResponseEntity<ApiResponse<List<QuizResponse>>> getStudentQuizResponses(
            @PathVariable String studentId,
            @AuthenticationPrincipal IdentifiablePrincipal principal,
            Authentication authentication) {

        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("");

        List<QuizResponse> responses = quizService.getQuizResponsesForStudent(studentId, principal.getId(), role);
        return ResponseEntity.ok(ApiResponse.success(responses, "Student quiz responses retrieved"));
    }

    // ============================================================
    // NEW ENDPOINTS: Quiz Distribution & Attempts
    // ============================================================

    /**
     * POST /api/quizzes/{quizId}/distribute
     * Distribute a quiz to students and/or parents.
     * Teacher only endpoint.
     *
     * Request body:
     * {
     *   "studentIds": ["student1", "student2"],
     *   "parentEmails": ["parent1@email.com", "parent2@email.com"],
     *   "customMessage": "Optional message to include in email"
     * }
     */
    @PostMapping("/{quizId}/distribute")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<List<QuizDTOs.QuizLinkResponse>>> distributeQuiz(
            @PathVariable String quizId,
            @Valid @RequestBody QuizDTOs.QuizDistributionRequest request,
            @AuthenticationPrincipal IdentifiablePrincipal principal) {

        try {
            List<QuizDTOs.QuizLinkResponse> links = new java.util.ArrayList<>();

            // Resolve validity days (default 7, clamp 1-30)
            int validityDays = request.getValidityDays() != null
                    ? Math.max(1, Math.min(30, request.getValidityDays()))
                    : 7;

            // Send to students if provided
            if (request.getStudentIds() != null && !request.getStudentIds().isEmpty()) {
                links.addAll(quizDistributionService.distributeQuizToStudents(
                        quizId,
                        request.getStudentIds(),
                        principal.getId(),
                        request.getCustomMessage(),
                        validityDays));
            }

            // Send to parents if provided
            if (request.getParentEmails() != null && !request.getParentEmails().isEmpty()) {
                links.addAll(quizDistributionService.distributeQuizToParents(
                        quizId,
                        request.getParentEmails(),
                        principal.getId(),
                        request.getCustomMessage(),
                        validityDays));
            }

            return ResponseEntity.ok(ApiResponse.success(links, "Quiz distributed successfully"));

        } catch (Exception e) {
            log.error("Failed to distribute quiz {}: {}", quizId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to distribute quiz. Please try again."));
        }
    }

    /**
     * POST /api/quizzes/{quizId}/attempt
     * Start a new quiz attempt using a shared token.
     * Public endpoint - no authentication required (token validates access).
     *
     * Request body:
     * {
     *   "quizId": "quiz123",
     *   "token": "secure_token_from_email_link"
     * }
     */
    @PostMapping("/{quizId}/attempt")
    public ResponseEntity<ApiResponse<QuizDTOs.QuizAttemptDetail>> startQuizAttempt(
            @PathVariable String quizId,
            @Valid @RequestBody QuizDTOs.QuizAttemptStartRequest request) {

        try {
            // Extract recipient info from token validation
            // For now, we'll use a simple approach - the frontend will send these
            QuizDTOs.QuizAttemptDetail attempt = quizAttemptService
                    .startQuizAttempt(quizId, "tempRecipientId", "STUDENT", request.getToken());

            return ResponseEntity.ok(ApiResponse.success(attempt, "Quiz attempt started"));

        } catch (Exception e) {
            log.error("Failed to start quiz attempt for quiz {}: {}", quizId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to start quiz attempt. Please try again."));
        }
    }

    /**
     * POST /api/quizzes/attempts/{quizAttemptId}/response
     * Submit a response to a single question.
     * Student/Parent endpoint - no authentication required (token validates).
     *
     * Request body:
     * {
     *   "quizAttemptId": "attempt123",
     *   "questionId": "question456",
     *   "studentAnswer": "Option B",
     *   "responseTimeMs": 5000
     * }
     */
    @PostMapping("/attempts/{quizAttemptId}/response")
    public ResponseEntity<ApiResponse<QuizDTOs.QuestionResponseDetail>> submitQuestionResponse(
            @PathVariable String quizAttemptId,
            @Valid @RequestBody QuizDTOs.QuestionResponseRequest request) {

        try {
            request.setQuizAttemptId(quizAttemptId);
            QuizDTOs.QuestionResponseDetail response = quizAttemptService
                    .submitQuestionResponse(quizAttemptId, request);

            return ResponseEntity.ok(ApiResponse.success(response, "Response recorded"));

        } catch (Exception e) {
            log.error("Failed to submit response for attempt {}: {}", quizAttemptId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to submit response. Please try again."));
        }
    }

    /**
     * POST /api/quizzes/attempts/{quizAttemptId}/complete
     * Mark quiz attempt as complete and calculate final score.
     * Triggers AI analysis.
     */
    @PostMapping("/attempts/{quizAttemptId}/complete")
    public ResponseEntity<ApiResponse<QuizDTOs.QuizAttemptDetail>> completeQuizAttempt(
            @PathVariable String quizAttemptId,
            @AuthenticationPrincipal IdentifiablePrincipal principal,
            Authentication authentication) {

        try {
            QuizDTOs.QuizAttemptDetail result = quizAttemptService.completeQuizAttempt(
                    quizAttemptId,
                    principal.getId(),
                    resolvePrimaryRole(authentication));
            return ResponseEntity.ok(ApiResponse.success(result, "Quiz completed"));

        } catch (Exception e) {
            log.error("Failed to complete quiz attempt {}: {}", quizAttemptId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to complete quiz. Please try again."));
        }
    }

    /**
     * GET /api/quizzes/attempts/{quizAttemptId}
     * Retrieve full details of a quiz attempt with all question responses.
     * Both student and teacher can access their own/student's attempts.
     */
    @GetMapping("/attempts/{quizAttemptId}")
    public ResponseEntity<ApiResponse<QuizDTOs.QuizAttemptDetail>> getQuizAttemptDetails(
            @PathVariable String quizAttemptId,
            @AuthenticationPrincipal IdentifiablePrincipal principal,
            Authentication authentication) {

        try {
            QuizDTOs.QuizAttemptDetail attempt = quizAttemptService.getQuizAttemptDetail(
                    quizAttemptId,
                    principal.getId(),
                    resolvePrimaryRole(authentication));
            return ResponseEntity.ok(ApiResponse.success(attempt, "Attempt details retrieved"));

        } catch (Exception e) {
            log.error("Failed to fetch attempt details {}: {}", quizAttemptId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to fetch attempt details. Please try again."));
        }
    }

    /**
     * GET /api/quizzes/{quizId}/progress
     * Get quiz progress and analytics (teacher dashboard).
     * Shows participation rate, average score, student performance.
     */
    @GetMapping("/{quizId}/progress")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<QuizDTOs.QuizProgressResponse>> getQuizProgress(
            @PathVariable String quizId,
            @AuthenticationPrincipal IdentifiablePrincipal principal) {

        try {
            QuizDTOs.QuizProgressResponse progress = quizDistributionService.getQuizProgress(quizId, principal.getId());
            return ResponseEntity.ok(ApiResponse.success(progress, "Quiz progress retrieved"));

        } catch (Exception e) {
            log.error("Failed to fetch quiz progress for {}: {}", quizId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to fetch quiz progress. Please try again."));
        }
    }

    /**
     * GET /api/quizzes/student/{studentId}/attempts
     * Get all quiz attempts for a student for a specific quiz.
     * Parent can view their child's attempts; Teacher can view their students.
     */
    @GetMapping("/student/{studentId}/attempts")
    @PreAuthorize("hasAnyRole('PARENT', 'TEACHER')")
    public ResponseEntity<ApiResponse<List<QuizDTOs.QuizAttemptDetail>>> getStudentQuizAttempts(
            @PathVariable String studentId,
            @RequestParam String quizId,
            @AuthenticationPrincipal IdentifiablePrincipal principal,
            Authentication authentication) {

        try {
            List<QuizDTOs.QuizAttemptDetail> attempts = quizAttemptService.getStudentQuizAttempts(
                    studentId,
                    quizId,
                    principal.getId(),
                    resolvePrimaryRole(authentication));
            return ResponseEntity.ok(ApiResponse.success(attempts, "Student quiz attempts retrieved"));

        } catch (Exception e) {
            log.error("Failed to fetch student {} attempts: {}", studentId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to fetch student attempts. Please try again."));
        }
    }

    /**
     * GET /api/quizzes/student/{studentId}/all-attempts
     * Get ALL quiz attempts for a student (all quizzes).
     * This is for parent dashboard to show complete quiz progress.
     */
    @GetMapping("/student/{studentId}/all-attempts")
    @PreAuthorize("hasAnyRole('PARENT', 'TEACHER')")
    public ResponseEntity<ApiResponse<List<QuizDTOs.QuizAttemptDetail>>> getAllStudentQuizAttempts(
            @PathVariable String studentId,
            @AuthenticationPrincipal IdentifiablePrincipal principal,
            Authentication authentication) {

        try {
            String role = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .findFirst()
                    .orElse("");

            // Validate parent can access this student
            List<QuizDTOs.QuizAttemptDetail> attempts = quizAttemptService.getAllStudentAttempts(studentId, principal.getId(), role);
            return ResponseEntity.ok(ApiResponse.success(attempts, "All student quiz attempts retrieved"));

        } catch (Exception e) {
            log.error("Failed to fetch all attempts for student {}: {}", studentId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to fetch student attempts. Please try again."));
        }
    }

    private String resolvePrimaryRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("");
    }
}

