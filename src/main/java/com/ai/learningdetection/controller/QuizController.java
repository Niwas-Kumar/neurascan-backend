package com.ai.learningdetection.controller;

import com.ai.learningdetection.dto.ApiResponse;
import com.ai.learningdetection.dto.QuizDTOs;
import com.ai.learningdetection.entity.QuizResponse;
import com.ai.learningdetection.security.IdentifiablePrincipal;
import com.ai.learningdetection.service.QuizService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/quizzes", "/quizzes"})
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<QuizDTOs.QuizDetail>> createQuiz(
            @RequestBody QuizDTOs.QuizGenerationRequest request,
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
            @RequestBody QuizDTOs.QuizSubmissionRequest submissionRequest,
            @AuthenticationPrincipal IdentifiablePrincipal principal) {

        submissionRequest.setQuizId(quizId);
        // only teacher or parent can submit via student record
        QuizResponse result = quizService.submitQuizResponse(submissionRequest);
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
}
