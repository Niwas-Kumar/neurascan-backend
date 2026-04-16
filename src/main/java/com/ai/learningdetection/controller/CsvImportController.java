package com.ai.learningdetection.controller;

import com.ai.learningdetection.dto.ApiResponse;
import com.ai.learningdetection.security.IdentifiablePrincipal;
import com.ai.learningdetection.security.TeacherUserDetails;
import com.ai.learningdetection.service.CsvImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping({"/api/import", "/import"})
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")
public class CsvImportController {

    private final CsvImportService csvImportService;

    @PostMapping("/students")
    public ResponseEntity<ApiResponse<Map<String, Object>>> importStudents(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal IdentifiablePrincipal principal) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("File is empty"));
        }

        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("text/csv") && !contentType.equals("application/vnd.ms-excel"))) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Only CSV files are accepted"));
        }

        String schoolId = null;
        if (principal instanceof TeacherUserDetails teacherDetails) {
            schoolId = teacherDetails.getSchoolId();
        }

        Map<String, Object> result = csvImportService.importStudents(file, principal.getId(), schoolId);
        boolean success = (boolean) result.getOrDefault("success", false);

        if (!success) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error((String) result.get("message")));
        }

        return ResponseEntity.ok(ApiResponse.success(result, "CSV import completed"));
    }
}
