package com.ai.learningdetection.service;

import com.ai.learningdetection.entity.Admin;
import com.ai.learningdetection.entity.Teacher;
import com.ai.learningdetection.util.JwtUtil;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final Firestore firestore;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final NotificationService notificationService;

    private static final String ADMINS_COLLECTION = "admins";
    private static final String TEACHERS_COLLECTION = "teachers";
    private static final String STUDENTS_COLLECTION = "students";

    /**
     * Admin login.
     */
    public Map<String, Object> loginAdmin(String email, String password) {
        try {
            QuerySnapshot query = firestore.collection(ADMINS_COLLECTION)
                    .whereEqualTo("email", email)
                    .limit(1)
                    .get().get();

            if (query.isEmpty()) {
                throw new BadCredentialsException("Invalid admin credentials.");
            }

            Admin admin = query.getDocuments().get(0).toObject(Admin.class);

            if (!passwordEncoder.matches(password, admin.getPassword())) {
                throw new BadCredentialsException("Invalid admin credentials.");
            }

            String token = jwtUtil.generateToken(admin.getEmail(), "ROLE_ADMIN", admin.getId(), admin.getName(), null);
            log.info("Admin logged in: {}", admin.getEmail());

            return Map.of(
                    "jwtToken", token,
                    "userRole", "ROLE_ADMIN",
                    "userId", admin.getId(),
                    "userName", admin.getName(),
                    "userEmail", admin.getEmail(),
                    "message", "Admin login successful"
            );
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Firestore error during admin login", e);
        }
    }

    /**
     * Get all teachers with optional status filter.
     */
    public List<Map<String, Object>> getTeachers(String statusFilter) {
        try {
            Query query = firestore.collection(TEACHERS_COLLECTION);
            if (statusFilter != null && !statusFilter.isEmpty()) {
                query = query.whereEqualTo("verificationStatus", statusFilter.toUpperCase());
            }

            QuerySnapshot snapshot = query.get().get();
            return snapshot.getDocuments().stream()
                    .map(doc -> {
                        Teacher t = doc.toObject(Teacher.class);
                        return Map.<String, Object>of(
                                "id", t.getId() != null ? t.getId() : doc.getId(),
                                "name", t.getName() != null ? t.getName() : "",
                                "email", t.getEmail() != null ? t.getEmail() : "",
                                "schoolId", t.getSchoolId() != null ? t.getSchoolId() : "",
                                "verificationStatus", t.getVerificationStatus() != null ? t.getVerificationStatus() : "APPROVED",
                                "createdAt", t.getCreatedAt() != null ? t.getCreatedAt() : ""
                        );
                    })
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to fetch teachers", e);
        }
    }

    /**
     * Approve a teacher.
     */
    public void approveTeacher(String teacherId) {
        try {
            DocumentReference docRef = firestore.collection(TEACHERS_COLLECTION).document(teacherId);
            DocumentSnapshot doc = docRef.get().get();
            if (!doc.exists()) {
                throw new RuntimeException("Teacher not found: " + teacherId);
            }

            docRef.update(
                    "verificationStatus", "APPROVED",
                    "updatedAt", Instant.now().toString()
            ).get();

            Teacher teacher = doc.toObject(Teacher.class);
            log.info("✓ Teacher approved: {} ({})", teacher.getName(), teacher.getEmail());

            // Send approval notification email
            try {
                emailService.sendTeacherApprovalEmail(teacher.getEmail(), teacher.getName(), true, null);
            } catch (Exception e) {
                log.warn("Failed to send approval email to {}: {}", teacher.getEmail(), e.getMessage());
            }

            // In-app notification
            notificationService.notifyTeacherApproved(teacherId, teacher.getName());
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to approve teacher", e);
        }
    }

    /**
     * Reject a teacher.
     */
    public void rejectTeacher(String teacherId, String reason) {
        try {
            DocumentReference docRef = firestore.collection(TEACHERS_COLLECTION).document(teacherId);
            DocumentSnapshot doc = docRef.get().get();
            if (!doc.exists()) {
                throw new RuntimeException("Teacher not found: " + teacherId);
            }

            docRef.update(
                    "verificationStatus", "REJECTED",
                    "updatedAt", Instant.now().toString()
            ).get();

            Teacher teacher = doc.toObject(Teacher.class);
            log.info("✗ Teacher rejected: {} ({}) - Reason: {}", teacher.getName(), teacher.getEmail(), reason);

            // Send rejection notification email
            try {
                emailService.sendTeacherApprovalEmail(teacher.getEmail(), teacher.getName(), false, reason);
            } catch (Exception e) {
                log.warn("Failed to send rejection email to {}: {}", teacher.getEmail(), e.getMessage());
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to reject teacher", e);
        }
    }

    /**
     * Get count of pending teachers.
     */
    public long getPendingTeacherCount() {
        try {
            QuerySnapshot query = firestore.collection(TEACHERS_COLLECTION)
                    .whereEqualTo("verificationStatus", "PENDING")
                    .get().get();
            return query.size();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to count pending teachers", e);
        }
    }

    public long getTotalTeacherCount() {
        try {
            return firestore.collection(TEACHERS_COLLECTION).get().get().size();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to count teachers", e);
        }
    }

    public long getTotalStudentCount() {
        try {
            return firestore.collection(STUDENTS_COLLECTION).get().get().size();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to count students", e);
        }
    }

    /**
     * Seed the first admin account if none exists (run once).
     */
    public void seedAdminIfEmpty(String email, String password, String name) {
        try {
            QuerySnapshot existing = firestore.collection(ADMINS_COLLECTION)
                    .limit(1).get().get();
            if (!existing.isEmpty()) {
                log.info("Admin account already exists, skipping seed.");
                return;
            }

            DocumentReference docRef = firestore.collection(ADMINS_COLLECTION).document();
            Admin admin = Admin.builder()
                    .id(docRef.getId())
                    .name(name)
                    .email(email)
                    .password(passwordEncoder.encode(password))
                    .createdAt(Instant.now().toString())
                    .emailVerified(true)
                    .build();

            docRef.set(admin).get();
            log.info("✓ Seeded admin account: {}", email);
        } catch (InterruptedException | ExecutionException e) {
            log.error("❌ Failed to seed admin: {}", e.getMessage(), e);
        }
    }
}
