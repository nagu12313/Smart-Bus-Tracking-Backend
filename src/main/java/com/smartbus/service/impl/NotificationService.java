package com.smartbus.service.impl;

import com.smartbus.dto.response.NotificationResponse;
import com.smartbus.entity.*;
import com.smartbus.repository.NotificationRepository;
import com.smartbus.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotificationsForUser(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAsRead(Long notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            n.setIsRead(true);
            notificationRepository.save(n);
        });
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllReadByUserId(userId);
    }

    /**
     * Create a notification for all ADMIN and SCHOOL_ADMIN users.
     */
    @Transactional
    public void createSystemNotification(String type, String title, String message,
                                          Trip trip, Bus bus) {
        List<User> admins = userRepository.findByRole(Role.ADMIN);
        admins.addAll(userRepository.findByRole(Role.SCHOOL_ADMIN));

        for (User admin : admins) {
            createNotificationForUser(admin, type, title, message, trip, bus);
        }
        log.info("System notification sent to {} admin(s): {}", admins.size(), title);
    }

    /**
     * Create a notification for a specific user.
     */
    @Transactional
    public void createNotificationForUser(User user, String type, String title,
                                           String message, Trip trip, Bus bus) {
        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .message(message)
                .trip(trip)
                .bus(bus)
                .isRead(false)
                .build();
        notificationRepository.save(notification);
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getType())
                .isRead(n.getIsRead())
                .tripId(n.getTrip() != null ? n.getTrip().getId() : null)
                .busId(n.getBus() != null ? n.getBus().getId() : null)
                .createdAt(n.getCreatedAt())
                .build();
    }
}
