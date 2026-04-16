package com.ai.learningdetection.controller;

import com.ai.learningdetection.dto.AnalysisDTOs;
import com.ai.learningdetection.dto.ApiResponse;
import com.ai.learningdetection.security.IdentifiablePrincipal;
import com.ai.learningdetection.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


import java.util.List;

@RestController
@RequestMapping({"/api/analysis", "/analysis"})
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    // ============================================================
    // TEACHER ENDPOINTS
    // ============================================================

    /**
     * POST /api/analysis/upload
     * Teacher uploads a student test paper for AI analysis.
     *
     * Form params:
     *   - studentId (Long)
     *   - file      (MultipartFile)
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<AnalysisDTOs.UploadResponse>> uploadAndAnalyze(
            @RequestParam("studentId") String studentId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal IdentifiablePrincipal principal) {

        AnalysisDTOs.UploadResponse result =
                analysisService.uploadAndAnalyze(studentId, file, principal.getId());

        return ResponseEntity.ok(
                ApiResponse.success(result, "File uploaded and analyzed successfully"));
    }

    /**
     * GET /api/analysis/reports
     * Returns all analysis reports for the teacher's students.
     */
    @GetMapping("/reports")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<List<AnalysisDTOs.AnalysisReportResponse>>> getReports(
            @AuthenticationPrincipal IdentifiablePrincipal principal) {

        List<AnalysisDTOs.AnalysisReportResponse> reports =
                analysisService.getReportsByTeacher(principal.getId());

        return ResponseEntity.ok(
                ApiResponse.success(reports, "Reports retrieved successfully"));
    }

    /**
     * GET /api/analysis/dashboard
     * Returns statistics for the teacher's class.
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<AnalysisDTOs.DashboardResponse>> getDashboard(
            @AuthenticationPrincipal IdentifiablePrincipal principal) {

        AnalysisDTOs.DashboardResponse dashboard =
                analysisService.getDashboard(principal.getId());

        return ResponseEntity.ok(
                ApiResponse.success(dashboard, "Dashboard data retrieved successfully"));
    }

    /**
     * GET /api/analysis/student-progress/{studentId}
     * Teacher retrieves a student's analysis history (timeline).
     */
    @GetMapping("/student-progress/{studentId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<AnalysisDTOs.ProgressResponse>> getStudentProgress(
            @PathVariable String studentId,
            @AuthenticationPrincipal IdentifiablePrincipal principal) {

        AnalysisDTOs.ProgressResponse progress =
                analysisService.getProgressForTeacher(studentId, principal.getId());

        return ResponseEntity.ok(
                ApiResponse.success(progress, "Student progress retrieved successfully"));
    }

    // ============================================================
    // PARENT ENDPOINTS
    // ============================================================

    /**
     * GET /api/analysis/student-report/{studentId}
     * Parent retrieves the latest analysis report for their child.
     */
    @GetMapping("/student-report/{studentId}")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ApiResponse<AnalysisDTOs.AnalysisReportResponse>> getStudentReport(
            @PathVariable String studentId,
            @AuthenticationPrincipal IdentifiablePrincipal principal) {

        AnalysisDTOs.AnalysisReportResponse report =
                analysisService.getLatestReportForParent(studentId, principal.getId());

        return ResponseEntity.ok(
                ApiResponse.success(report, "Latest report retrieved successfully"));
    }

    /**
     * GET /api/analysis/progress/{studentId}
     * Parent retrieves all past reports for their child (progress tracking).
     */
    @GetMapping("/progress/{studentId}")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ApiResponse<AnalysisDTOs.ProgressResponse>> getProgress(
            @PathVariable String studentId,
            @AuthenticationPrincipal IdentifiablePrincipal principal) {

        AnalysisDTOs.ProgressResponse progress =
                analysisService.getProgressForParent(studentId, principal.getId());

        return ResponseEntity.ok(
                ApiResponse.success(progress, "Progress data retrieved successfully"));
    }

    /**
     * GET /api/analysis/comprehensive/{studentId}
     * Parent retrieves a comprehensive report that fuses handwriting analysis + quiz screening.
     * This is purely additive — all existing endpoints remain unchanged.
     */
    @GetMapping("/comprehensive/{studentId}")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ApiResponse<AnalysisDTOs.ComprehensiveReportResponse>> getComprehensiveReport(
            @PathVariable String studentId,
            @AuthenticationPrincipal IdentifiablePrincipal principal) {

        AnalysisDTOs.ComprehensiveReportResponse report =
                analysisService.getComprehensiveReport(studentId, principal.getId());

        return ResponseEntity.ok(
                ApiResponse.success(report, "Comprehensive report retrieved successfully"));
    }

    // ============================================================
    // PARENT SELF-SERVE ANALYSIS (Independent users)
    // ============================================================

    /**
     * POST /api/analysis/parent-upload
     * Parent uploads their child's test paper for AI analysis.
     * The parent must have a linked student to upload for.
     */
    @PostMapping(value = "/parent-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ApiResponse<AnalysisDTOs.UploadResponse>> parentUploadAndAnalyze(
            @RequestParam("studentId") String studentId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal IdentifiablePrincipal principal) {

        // Parent uses the same analysis pipeline, but we pass the parent's ID as the "teacher"
        // This allows independent parents to get AI analysis for their own child
        AnalysisDTOs.UploadResponse result =
                analysisService.uploadAndAnalyze(studentId, file, principal.getId());

        return ResponseEntity.ok(
                ApiResponse.success(result, "File uploaded and analyzed successfully"));
    }

    /**
     * GET /api/analysis/parent-reports
     * Parent retrieves all analysis reports they have uploaded.
     */
    @GetMapping("/parent-reports")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<ApiResponse<List<AnalysisDTOs.AnalysisReportResponse>>> getParentReports(
            @AuthenticationPrincipal IdentifiablePrincipal principal) {

        // Reuse teacher report logic — it fetches by the uploader's ID
        List<AnalysisDTOs.AnalysisReportResponse> reports =
                analysisService.getReportsByTeacher(principal.getId());

        return ResponseEntity.ok(
                ApiResponse.success(reports, "Reports retrieved successfully"));
    }

}
