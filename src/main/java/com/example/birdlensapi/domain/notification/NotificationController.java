package com.example.birdlensapi.domain.notification;

import com.example.birdlensapi.common.dto.ApiResponse;
import com.example.birdlensapi.domain.notification.dto.NotificationPageResponse;
import com.example.birdlensapi.domain.notification.dto.NotificationResponse;
import com.example.birdlensapi.domain.user.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationPageResponse>> getNotifications(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        UUID userId = ((User) userDetails).getId();
        NotificationPageResponse response = notificationService.getUserNotifications(userId, pageable);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(
            @PathVariable("id") UUID notificationId,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = ((User) userDetails).getId();
        NotificationResponse response = notificationService.markAsRead(notificationId, userId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}