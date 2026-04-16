package com.ai.learningdetection.controller;

import com.ai.learningdetection.dto.ApiResponse;
import com.ai.learningdetection.entity.Notification;
import com.ai.learningdetection.security.IdentifiablePrincipal;
import com.ai.learningdetection.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/notifications", "/notifications"})
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('TEACHER', 'PARENT')")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getNotifications(
            @AuthenticationPrincipal IdentifiablePrincipal principal,
            @RequestParam(defaultValue = "20") int limit) {
        try {
            List<Notification> notifications = notificationService.getNotifications(principal.getId(), limit);
            long unreadCount = notificationService.getUnreadCount(principal.getId());

            Map<String, Object> data = new HashMap<>();
            data.put("notifications", notifications);
            data.put("unreadCount", unreadCount);

            return ResponseEntity.ok(ApiResponse.success(data, "Notifications retrieved"));
        } catch (Exception e) {
            log.error("Error fetching notifications: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to load notifications"));
        }
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(
            @AuthenticationPrincipal IdentifiablePrincipal principal) {
        try {
            long count = notificationService.getUnreadCount(principal.getId());
            return ResponseEntity.ok(ApiResponse.success(Map.of("unreadCount", count), "OK"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to get unread count"));
        }
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @AuthenticationPrincipal IdentifiablePrincipal principal,
            @PathVariable String id) {
        try {
            notificationService.markAsRead(id, principal.getId());
            return ResponseEntity.ok(ApiResponse.success(null, "Marked as read"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to mark as read"));
        }
    }

    @PutMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(
            @AuthenticationPrincipal IdentifiablePrincipal principal) {
        try {
            notificationService.markAllAsRead(principal.getId());
            return ResponseEntity.ok(ApiResponse.success(null, "All marked as read"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to mark all as read"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(
            @AuthenticationPrincipal IdentifiablePrincipal principal,
            @PathVariable String id) {
        try {
            notificationService.delete(id, principal.getId());
            return ResponseEntity.ok(ApiResponse.success(null, "Notification deleted"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to delete notification"));
        }
    }
}
