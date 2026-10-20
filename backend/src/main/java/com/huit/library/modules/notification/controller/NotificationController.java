package com.huit.library.modules.notification.controller;

import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.notification.entity.NotificationEntity;
import com.huit.library.modules.notification.service.NotificationService;
import com.huit.library.modules.notification.dto.RegisterPushDeviceRequestDTO;
import com.huit.library.modules.notification.dto.UnregisterPushDeviceRequestDTO;
import com.huit.library.modules.notification.service.PushDeviceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final PushDeviceService pushDeviceService;

    public NotificationController(NotificationService notificationService,
                                  PushDeviceService pushDeviceService) {
        this.notificationService = notificationService;
        this.pushDeviceService = pushDeviceService;
    }

    @GetMapping
    public ResponseEntity<List<NotificationEntity>> getMyNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(notificationService.getMyNotifications(
                userDetails.getUser().getUserId()));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<NotificationEntity> markAsRead(
            @PathVariable UUID notificationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(notificationService.markAsRead(
                notificationId, userDetails.getUser().getUserId()));
    }

    @PostMapping("/devices")
    public ResponseEntity<?> registerDevice(
            @Valid @RequestBody RegisterPushDeviceRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(pushDeviceService.register(
                userDetails.getUser().getUserId(), request));
    }

    @DeleteMapping("/devices")
    public ResponseEntity<Void> unregisterDevice(
            @Valid @RequestBody UnregisterPushDeviceRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        pushDeviceService.unregister(userDetails.getUser().getUserId(), request.token());
        return ResponseEntity.noContent().build();
    }
}
