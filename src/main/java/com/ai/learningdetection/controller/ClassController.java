package com.ai.learningdetection.controller;

import com.ai.learningdetection.dto.ApiResponse;
import com.ai.learningdetection.dto.ClassDTOs;
import com.ai.learningdetection.security.IdentifiablePrincipal;
import com.ai.learningdetection.service.ClassService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/classes", "/classes"})
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")
public class ClassController {

    private final ClassService classService;

    @PostMapping
    public ResponseEntity<ApiResponse<ClassDTOs.ClassResponse>> createClass(
            @Valid @RequestBody ClassDTOs.ClassCreateRequest request,
            @AuthenticationPrincipal IdentifiablePrincipal principal) {
        request.setTeacherId(principal.getId());
        ClassDTOs.ClassResponse response = classService.createClass(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "Class created"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ClassDTOs.ClassResponse>>> getClasses(
            @AuthenticationPrincipal IdentifiablePrincipal principal) {
        List<ClassDTOs.ClassResponse> classes = classService.getClassesByTeacher(principal.getId());
        return ResponseEntity.ok(ApiResponse.success(classes, "Classes retrieved"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ClassDTOs.ClassResponse>> getClassById(
            @PathVariable String id,
            @AuthenticationPrincipal IdentifiablePrincipal principal) {
        ClassDTOs.ClassResponse response = classService.getClassById(id, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "Class details retrieved"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ClassDTOs.ClassResponse>> updateClass(
            @PathVariable String id,
            @Valid @RequestBody ClassDTOs.ClassCreateRequest request,
            @AuthenticationPrincipal IdentifiablePrincipal principal) {
        ClassDTOs.ClassResponse response = classService.updateClass(id, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "Class updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> archiveClass(
            @PathVariable String id,
            @AuthenticationPrincipal IdentifiablePrincipal principal) {
        classService.deleteClass(id, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "Class archived"));
    }
}
