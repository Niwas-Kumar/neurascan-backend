package com.ai.learningdetection.controller;

import com.ai.learningdetection.security.TeacherUserDetails;
import com.ai.learningdetection.service.ExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/export", "/export"})
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('TEACHER')")
public class ExportController {

    private final ExportService exportService;

    @GetMapping("/students")
    public ResponseEntity<byte[]> exportStudents(
            @AuthenticationPrincipal TeacherUserDetails principal) {
        try {
            String csv = exportService.exportStudentsCsv(principal.getId());
            return buildCsvResponse(csv, "students_export.csv");
        } catch (Exception e) {
            log.error("Failed to export students CSV: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/reports")
    public ResponseEntity<byte[]> exportReports(
            @AuthenticationPrincipal TeacherUserDetails principal) {
        try {
            String csv = exportService.exportReportsCsv(principal.getId());
            return buildCsvResponse(csv, "analysis_reports_export.csv");
        } catch (Exception e) {
            log.error("Failed to export reports CSV: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    private ResponseEntity<byte[]> buildCsvResponse(String csv, String filename) {
        byte[] bytes = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("text/csv"))
                .contentLength(bytes.length)
                .body(bytes);
    }
}
