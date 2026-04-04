package com.ai.learningdetection.controller;

import com.ai.learningdetection.dto.ApiResponse;
import com.ai.learningdetection.entity.Parent;
import com.ai.learningdetection.entity.Teacher;
import com.ai.learningdetection.exception.UnauthorizedAccessException;
import com.ai.learningdetection.security.IdentifiablePrincipal;
import com.ai.learningdetection.util.JwtUtil;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ExecutionException;

/**
 * Authenticated endpoints for the Settings page.
 * Requires a valid JWT token (sent by the React frontend).
 */
@RestController
@RequestMapping({"/api/auth", "/auth"})
@RequiredArgsConstructor
public class ProfileController {

    private final Firestore firestore;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    private static final String TEACHERS_COLLECTION = "teachers";
    private static final String PARENTS_COLLECTION = "parents";

    // ── Inner DTOs ────────────────────────────────────────────

    @Data
    static class UpdateProfileRequest {
        @NotBlank(message = "Name is required")
        private String name;

        private String school; // Optional for parents, used by teachers
        private String studentId; // Optional for parents to link their child
    }

    @Data
    static class ChangePasswordRequest {
        @NotBlank(message = "Current password is required")
        private String currentPassword;

        @NotBlank(message = "New password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        private String newPassword;
    }

    @Data
    @lombok.Builder
    static class UserProfileResponse {
        private String id;
        private String name;
        private String email;
        private String role;
        private String school;
        private String studentId;
        private String picture;
        private String jwtToken;
    }

    // ── Helper ────────────────────────────────────────────────

    private boolean isTeacher(UserDetails ud) {
        return ud.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_TEACHER"));
    }

    // ── Endpoints ────────────────────────────────────────────

    /**
     * GET /api/auth/profile
     * Fetch current user details.
     */
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @AuthenticationPrincipal IdentifiablePrincipal principal,
            @AuthenticationPrincipal UserDetails ud) throws ExecutionException, InterruptedException {

        boolean teacher = isTeacher(ud);
        String collection = teacher ? TEACHERS_COLLECTION : PARENTS_COLLECTION;
        
        DocumentSnapshot doc = firestore.collection(collection).document(principal.getId()).get().get();
        if (!doc.exists()) {
            throw new BadCredentialsException("User not found");
        }

        String name = doc.getString("name");
        String email = doc.getString("email");
        String role = teacher ? "ROLE_TEACHER" : "ROLE_PARENT";

        UserProfileResponse res = UserProfileResponse.builder()
                .id(doc.getId())
                .name(name)
                .email(email)
                .role(role)
                .school(doc.getString("school"))
                .studentId(doc.getString("studentId"))
                .picture(doc.getString("picture"))
                .jwtToken(jwtUtil.generateToken(email, role, doc.getId(), name, doc.getString("picture")))
                .build();

        return ResponseEntity.ok(ApiResponse.success(res));
    }

    /**
     * PUT /api/auth/profile
     * Update display name for teacher or parent and return updated profile.
     */
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal IdentifiablePrincipal principal,
            @AuthenticationPrincipal UserDetails ud) throws ExecutionException, InterruptedException {

        boolean teacher = isTeacher(ud);
        String collection = teacher ? TEACHERS_COLLECTION : PARENTS_COLLECTION;
        
        // Update the name
        firestore.collection(collection).document(principal.getId()).update("name", request.getName()).get();

        // If teacher, also update school
        if (teacher && request.getSchool() != null) {
            firestore.collection(collection).document(principal.getId()).update("school", request.getSchool()).get();
        }
        
        // If parent, also update studentId (for linking to child's progress)
        if (!teacher && request.getStudentId() != null && !request.getStudentId().isEmpty()) {
            if (!hasVerifiedRelationship(principal.getId(), request.getStudentId())) {
                throw new UnauthorizedAccessException(
                        "Student access is not verified for this account. Please connect using the Parent-Student verification flow.");
            }

            firestore.collection(collection).document(principal.getId()).update("studentId", request.getStudentId()).get();

            // Keep legacy parentUid link in sync, but never overwrite another parent's existing link.
            linkStudentToParent(request.getStudentId(), principal.getId());
        }
        
        // Fetch and return the updated profile
        DocumentSnapshot doc = firestore.collection(collection).document(principal.getId()).get().get();
        if (!doc.exists()) {
            throw new BadCredentialsException("User not found after update");
        }

        String name = doc.getString("name");
        String email = doc.getString("email");
        String role = teacher ? "ROLE_TEACHER" : "ROLE_PARENT";

        UserProfileResponse res = UserProfileResponse.builder()
                .id(doc.getId())
                .name(name)
                .email(email)
                .role(role)
                .school(doc.getString("school"))
                .studentId(doc.getString("studentId"))
                .picture(doc.getString("picture"))
                .jwtToken(jwtUtil.generateToken(email, role, doc.getId(), name, doc.getString("picture")))
                .build();

        return ResponseEntity.ok(ApiResponse.success(res, "Profile updated successfully"));
    }

    /**
     * PUT /api/auth/change-password
     * Change password from Settings page (requires current password).
     */
    @PutMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal IdentifiablePrincipal principal,
            @AuthenticationPrincipal UserDetails ud) throws ExecutionException, InterruptedException {

        String collection = isTeacher(ud) ? TEACHERS_COLLECTION : PARENTS_COLLECTION;
        
        // Get current stored password
        String storedPassword;
        if (isTeacher(ud)) {
            Teacher t = firestore.collection(TEACHERS_COLLECTION).document(principal.getId()).get().get().toObject(Teacher.class);
            if (t == null) throw new BadCredentialsException("Teacher not found");
            storedPassword = t.getPassword();
        } else {
            Parent p = firestore.collection(PARENTS_COLLECTION).document(principal.getId()).get().get().toObject(Parent.class);
            if (p == null) throw new BadCredentialsException("Parent not found");
            storedPassword = p.getPassword();
        }

        // Verify current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), storedPassword)) {
            throw new BadCredentialsException("Current password is incorrect.");
        }

        // Save new password
        String encoded = passwordEncoder.encode(request.getNewPassword());
        firestore.collection(collection).document(principal.getId()).update("password", encoded).get();

        return ResponseEntity.ok(ApiResponse.success(null, "Password changed successfully"));
    }

    /**
     * Link a student to a parent by setting parentUid on the student record.
     */
    private void linkStudentToParent(String studentId, String parentId) {
        try {
            var studentRef = firestore.collection("students").document(studentId);
            var studentSnap = studentRef.get().get();

            if (studentSnap.exists()) {
                String currentParentUid = studentSnap.getString("parentUid");
                if (currentParentUid == null || currentParentUid.isBlank() || parentId.equals(currentParentUid)) {
                    studentRef.update("parentUid", parentId).get();
                }
            }
        } catch (Exception e) {
            // Log but don't fail the profile update
            e.printStackTrace();
        }
    }

    private boolean hasVerifiedRelationship(String parentId, String studentId) {
        try {
            QuerySnapshot relationshipQuery = firestore.collection("parent_student_relationships")
                    .whereEqualTo("parentId", parentId)
                    .whereEqualTo("studentId", studentId)
                    .whereEqualTo("verificationStatus", "VERIFIED")
                    .limit(1)
                    .get().get();

            if (relationshipQuery.isEmpty()) {
                return false;
            }

            DocumentSnapshot rel = relationshipQuery.getDocuments().get(0);
            String disconnectedAt = rel.getString("disconnectedAt");
            return disconnectedAt == null || disconnectedAt.isBlank();
        } catch (Exception e) {
            return false;
        }
    }
}
