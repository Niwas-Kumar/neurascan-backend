package com.ai.learningdetection.controller;

import com.ai.learningdetection.dto.ApiResponse;
import com.ai.learningdetection.entity.School;
import com.ai.learningdetection.service.AdminService;
import com.ai.learningdetection.service.SchoolService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({ "/api/admin", "/admin" })
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final AdminService adminService;
    private final SchoolService schoolService;

    // ── DTOs ──────────────────────────────────────────────────

    @Data
    public static class AdminLoginRequest {
        @NotBlank private String email;
        @NotBlank private String password;
    }

    @Data
    public static class CreateSchoolRequest {
        @NotBlank private String name;
        private String address;
    }

    @Data
    public static class TeacherActionRequest {
        private String reason;
    }

    // ── Auth ──────────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> adminLogin(
            @Valid @RequestBody AdminLoginRequest request) {
        Map<String, Object> response = adminService.loginAdmin(request.getEmail(), request.getPassword());
        return ResponseEntity.ok(ApiResponse.success(response, "Admin login successful"));
    }

    // ── School Management ─────────────────────────────────────

    @GetMapping("/schools")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<School>>> getAllSchools() {
        return ResponseEntity.ok(ApiResponse.success(schoolService.getAllSchools()));
    }

    @PostMapping("/schools")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<School>> createSchool(
            @Valid @RequestBody CreateSchoolRequest request) {
        School school = schoolService.createSchool(request.getName(), request.getAddress());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(school, "School created successfully"));
    }

    @PutMapping("/schools/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> toggleSchool(
            @PathVariable String id,
            @RequestParam boolean active) {
        schoolService.toggleSchoolStatus(id, active);
        return ResponseEntity.ok(ApiResponse.success(null, "School status updated"));
    }

    @PostMapping("/schools/{id}/regenerate-code")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> regenerateCode(@PathVariable String id) {
        String newCode = schoolService.regenerateSchoolCode(id);
        return ResponseEntity.ok(ApiResponse.success(newCode, "School code regenerated"));
    }

    // ── Teacher Verification ──────────────────────────────────

    @GetMapping("/teachers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTeachers(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getTeachers(status)));
    }

    @PutMapping("/teachers/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> approveTeacher(@PathVariable String id) {
        adminService.approveTeacher(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Teacher approved successfully"));
    }

    @PutMapping("/teachers/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> rejectTeacher(
            @PathVariable String id,
            @RequestBody(required = false) TeacherActionRequest request) {
        adminService.rejectTeacher(id, request != null ? request.getReason() : null);
        return ResponseEntity.ok(ApiResponse.success(null, "Teacher rejected"));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        long pendingCount = adminService.getPendingTeacherCount();
        List<School> schools = schoolService.getAllSchools();
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "pendingTeachers", pendingCount,
                "totalSchools", schools.size(),
                "activeSchools", schools.stream().filter(School::isActive).count()
        )));
    }

    // ── Public: Validate School Code (for registration) ───────

    @GetMapping("/schools/validate-code")
    public ResponseEntity<ApiResponse<Map<String, String>>> validateSchoolCode(
            @RequestParam String code) {
        School school = schoolService.validateSchoolCode(code);
        if (school == null) {
            return ResponseEntity.ok(ApiResponse.error("Invalid school code"));
        }
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("schoolId", school.getId(), "schoolName", school.getName()),
                "Valid school code"
        ));
    }
}
