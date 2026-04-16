package com.ai.learningdetection.controller;

import com.ai.learningdetection.security.IdentifiablePrincipal;
import com.ai.learningdetection.service.PdfReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/reports", "/reports"})
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")
public class PdfReportController {

    private final PdfReportService pdfReportService;

    @GetMapping("/student/{studentId}/pdf")
    public ResponseEntity<byte[]> downloadStudentReport(
            @PathVariable String studentId,
            @AuthenticationPrincipal IdentifiablePrincipal principal) {

        byte[] pdfBytes = pdfReportService.generateStudentReport(studentId, principal.getId());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=student_report_" + studentId + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdfBytes.length)
                .body(pdfBytes);
    }
}
