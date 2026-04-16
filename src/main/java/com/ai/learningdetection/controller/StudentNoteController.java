package com.ai.learningdetection.controller;

import com.ai.learningdetection.entity.StudentNote;
import com.ai.learningdetection.security.IdentifiablePrincipal;
import com.ai.learningdetection.service.StudentNoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping({"/api/notes", "/notes"})
@RequiredArgsConstructor
public class StudentNoteController {

    private final StudentNoteService noteService;

    // ──── Teacher endpoints ────

    @PostMapping("/student/{studentId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> createNote(
            @PathVariable String studentId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal IdentifiablePrincipal principal) throws ExecutionException, InterruptedException {

        String content = (String) body.get("content");
        boolean visible = body.get("visibleToParent") != null && (Boolean) body.get("visibleToParent");

        StudentNote note = noteService.createNote(studentId, principal.getId(), principal.getName(), content, visible);
        return ResponseEntity.ok(Map.of("status", "success", "data", note));
    }

    @GetMapping("/teacher/student/{studentId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> getNotesForTeacher(
            @PathVariable String studentId,
            @AuthenticationPrincipal IdentifiablePrincipal principal) throws ExecutionException, InterruptedException {

        List<StudentNote> notes = noteService.getNotesForTeacher(studentId, principal.getId());
        return ResponseEntity.ok(Map.of("status", "success", "data", notes));
    }

    @PutMapping("/{noteId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> updateNote(
            @PathVariable String noteId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal IdentifiablePrincipal principal) throws ExecutionException, InterruptedException {

        String content = (String) body.get("content");
        Boolean visible = body.containsKey("visibleToParent") ? (Boolean) body.get("visibleToParent") : null;

        StudentNote note = noteService.updateNote(noteId, principal.getId(), content, visible);
        return ResponseEntity.ok(Map.of("status", "success", "data", note));
    }

    @DeleteMapping("/{noteId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<?> deleteNote(
            @PathVariable String noteId,
            @AuthenticationPrincipal IdentifiablePrincipal principal) throws ExecutionException, InterruptedException {

        noteService.deleteNote(noteId, principal.getId());
        return ResponseEntity.ok(Map.of("status", "success", "message", "Note deleted"));
    }

    // ──── Parent endpoint ────

    @GetMapping("/parent/student/{studentId}")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<?> getNotesForParent(
            @PathVariable String studentId,
            @AuthenticationPrincipal IdentifiablePrincipal principal) throws ExecutionException, InterruptedException {

        List<StudentNote> notes = noteService.getNotesForParent(studentId, principal.getId());
        return ResponseEntity.ok(Map.of("status", "success", "data", notes));
    }
}
