package com.ai.learningdetection.controller;

import com.ai.learningdetection.dto.ApiResponse;
import com.ai.learningdetection.dto.StudentDTOs;
import com.ai.learningdetection.security.IdentifiablePrincipal;
import com.ai.learningdetection.security.TeacherUserDetails;
import com.ai.learningdetection.service.StudentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/students", "/students"})
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")
public class StudentController {

    private final StudentService studentService;

    /**
     * GET /api/students
     * Returns all students belonging to the authenticated teacher.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<StudentDTOs.StudentResponse>>> getAllStudents(
            @AuthenticationPrincipal IdentifiablePrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String className,
            @RequestParam(required = false) String classId,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String rollNumber) {

        // Backward-compatible alias: classId maps to className filter for drill-down routes.
        String effectiveClassFilter =
                (classId != null && !classId.isBlank()) ? classId : className;

        List<StudentDTOs.StudentResponse> students =
                studentService.getStudentsByTeacher(principal.getId(), search, effectiveClassFilter, tag, rollNumber);
        return ResponseEntity.ok(
                ApiResponse.success(students, "Students retrieved successfully"));
    }

    /**
     * GET /api/students/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StudentDTOs.StudentResponse>> getStudentById(
            @PathVariable String id,
            @AuthenticationPrincipal IdentifiablePrincipal principal) {

        StudentDTOs.StudentResponse student =
                studentService.getStudentById(id, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(student));
    }

    /**
     * POST /api/students
     * Creates a new student under the authenticated teacher.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<StudentDTOs.StudentResponse>> createStudent(
            @Valid @RequestBody StudentDTOs.StudentRequest request,
            @AuthenticationPrincipal IdentifiablePrincipal principal) {

        StudentDTOs.StudentResponse student =
                studentService.createStudent(request, principal.getId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(student, "Student created successfully"));
    }

    /**
     * PUT /api/students/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<StudentDTOs.StudentResponse>> updateStudent(
            @PathVariable String id,
            @Valid @RequestBody StudentDTOs.StudentRequest request,
            @AuthenticationPrincipal IdentifiablePrincipal principal) {

        StudentDTOs.StudentResponse student =
                studentService.updateStudent(id, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(student, "Student updated successfully"));
    }

    /**
     * DELETE /api/students/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStudent(
            @PathVariable String id,
            @AuthenticationPrincipal IdentifiablePrincipal principal) {

        studentService.deleteStudent(id, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Student deleted successfully"));
    }

    /**
     * GET /api/students/school
     * Returns all students in the teacher's school (school-wide view).
     */
    @GetMapping("/school")
    public ResponseEntity<ApiResponse<List<StudentDTOs.StudentResponse>>> getSchoolStudents(
            @AuthenticationPrincipal IdentifiablePrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String className,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String rollNumber) {

        String schoolId = null;
        if (principal instanceof TeacherUserDetails teacherDetails) {
            schoolId = teacherDetails.getSchoolId();
        }
        if (schoolId == null || schoolId.isBlank()) {
            return ResponseEntity.ok(
                    ApiResponse.success(List.of(), "No school assigned to this teacher"));
        }

        List<StudentDTOs.StudentResponse> students =
                studentService.getStudentsBySchool(schoolId, search, className, tag, rollNumber);
        return ResponseEntity.ok(
                ApiResponse.success(students, "School students retrieved successfully"));
    }

}
