package com.ai.learningdetection.service;

import com.ai.learningdetection.entity.Notification;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final Firestore firestore;
    private static final String COLLECTION = "notifications";

    /**
     * Create and persist a notification.
     */
    public void send(String userId, String role, String type, String title, String message, String link) {
        try {
            DocumentReference ref = firestore.collection(COLLECTION).document();
            Notification n = Notification.builder()
                    .id(ref.getId())
                    .userId(userId)
                    .role(role)
                    .type(type)
                    .title(title)
                    .message(message)
                    .link(link)
                    .read(false)
                    .createdAt(Instant.now().toString())
                    .build();
            ref.set(n);
            log.debug("Notification sent to {} ({}): {}", userId, type, title);
        } catch (Exception e) {
            log.warn("Failed to send notification to {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Get notifications for a user, ordered newest-first.
     */
    public List<Notification> getNotifications(String userId, int limit) throws ExecutionException, InterruptedException {
        QuerySnapshot snap = firestore.collection(COLLECTION)
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
                .get().get();

        List<Notification> list = new ArrayList<>();
        for (DocumentSnapshot doc : snap.getDocuments()) {
            list.add(doc.toObject(Notification.class));
        }
        return list;
    }

    /**
     * Count unread notifications for a user.
     */
    public long getUnreadCount(String userId) throws ExecutionException, InterruptedException {
        QuerySnapshot snap = firestore.collection(COLLECTION)
                .whereEqualTo("userId", userId)
                .whereEqualTo("read", false)
                .get().get();
        return snap.size();
    }

    /**
     * Mark a single notification as read.
     */
    public void markAsRead(String notificationId, String userId) throws ExecutionException, InterruptedException {
        DocumentReference ref = firestore.collection(COLLECTION).document(notificationId);
        DocumentSnapshot doc = ref.get().get();
        if (doc.exists() && userId.equals(doc.getString("userId"))) {
            ref.update("read", true);
        }
    }

    /**
     * Mark all notifications as read for a user.
     */
    public void markAllAsRead(String userId) throws ExecutionException, InterruptedException {
        QuerySnapshot snap = firestore.collection(COLLECTION)
                .whereEqualTo("userId", userId)
                .whereEqualTo("read", false)
                .get().get();

        WriteBatch batch = firestore.batch();
        for (DocumentSnapshot doc : snap.getDocuments()) {
            batch.update(doc.getReference(), "read", true);
        }
        batch.commit().get();
    }

    /**
     * Delete a notification.
     */
    public void delete(String notificationId, String userId) throws ExecutionException, InterruptedException {
        DocumentReference ref = firestore.collection(COLLECTION).document(notificationId);
        DocumentSnapshot doc = ref.get().get();
        if (doc.exists() && userId.equals(doc.getString("userId"))) {
            ref.delete();
        }
    }

    // ── Convenience methods for specific events ────────────────────

    public void notifyAnalysisComplete(String teacherId, String studentName, String riskLevel, String reportId) {
        send(teacherId, "ROLE_TEACHER", "ANALYSIS_COMPLETE",
                "Analysis Complete",
                "Analysis for " + studentName + " is ready. Risk level: " + riskLevel,
                "/teacher/reports");
    }

    public void notifyParentAnalysis(String parentId, String studentName, String riskLevel) {
        send(parentId, "ROLE_PARENT", "ANALYSIS_COMPLETE",
                "New Analysis Available",
                "A new analysis for " + studentName + " is available. Risk: " + riskLevel,
                "/parent/progress");
    }

    public void notifyTeacherApproved(String teacherId, String teacherName) {
        send(teacherId, "ROLE_TEACHER", "TEACHER_APPROVED",
                "Account Approved",
                "Welcome " + teacherName + "! Your teacher account has been approved.",
                "/teacher/dashboard");
    }

    public void notifyQuizCompleted(String teacherId, String studentName, String quizId, double score) {
        send(teacherId, "ROLE_TEACHER", "QUIZ_SUBMITTED",
                "Quiz Submitted",
                studentName + " completed a quiz with score " + String.format("%.0f", score) + "%",
                "/teacher/quizzes");
    }

    public void notifyStudentLinked(String teacherId, String parentName, String studentName) {
        send(teacherId, "ROLE_TEACHER", "STUDENT_LINKED",
                "Parent Connected",
                parentName + " has linked to student " + studentName,
                "/teacher/classes");
    }
}
