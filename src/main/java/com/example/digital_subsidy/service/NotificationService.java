package com.example.digital_subsidy.service;

import com.example.digital_subsidy.exception.ResourceNotFoundException;
import com.example.digital_subsidy.model.Application;
import com.example.digital_subsidy.model.Notification;
import com.example.digital_subsidy.model.Officer;
import com.example.digital_subsidy.model.User;
import com.example.digital_subsidy.repository.NotificationRepository;
import com.example.digital_subsidy.repository.OfficerRepository;
import com.example.digital_subsidy.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final OfficerRepository officerRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               OfficerRepository officerRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.officerRepository = officerRepository;
    }

    @Transactional
    public Notification createNotification(User recipient, Application application, String type, String title, String message, String priority, String actionUrl, String eventKey) {
        if (recipient == null) return null;

        // Deduplication check using unique eventKey
        if (eventKey != null && !eventKey.isBlank() && notificationRepository.existsByEventKey(eventKey)) {
            logger.debug("Notification with eventKey '{}' already exists. Skipping creation.", eventKey);
            return null;
        }

        Notification notification = new Notification(recipient, application, type, title, message, priority, actionUrl, eventKey);
        Notification saved = notificationRepository.save(notification);
        logger.info("[NOTIFICATION CREATED] ID: {} | User: {} | Type: {} | Title: {}",
                saved.getId(), recipient.getEmail(), type, title);
        return saved;
    }

    @Transactional
    public void notifyRoleUsers(String roleName, String state, String district, Application application, String type, String title, String message, String priority, String actionUrl, String eventKeyPrefix) {
        String fullRoleName = roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName;
        List<User> roleUsers = userRepository.findByRolesName(fullRoleName);

        for (User user : roleUsers) {
            // Check state/district authorization if filtering officers by region
            if (state != null && !state.isBlank()) {
                Officer officer = officerRepository.findByUserEmail(user.getEmail()).orElse(null);
                if (officer != null && officer.getState() != null) {
                    if (!officer.getState().equalsIgnoreCase(state)) {
                        continue; // Skip officers outside state
                    }
                    if (district != null && !district.isBlank() && officer.getDistrict() != null && !officer.getDistrict().isBlank()) {
                        if (!officer.getDistrict().equalsIgnoreCase(district)) {
                            continue; // Skip officers outside district
                        }
                    }
                }
            }

            String userEventKey = (eventKeyPrefix != null && !eventKeyPrefix.isBlank()) ? eventKeyPrefix + "_USER_" + user.getId() : null;
            createNotification(user, application, type, title, message, priority, actionUrl, userEventKey);
        }
    }

    public List<Notification> getUserNotifications(User user) {
        return notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(user.getId());
    }

    public List<Notification> getUnreadNotifications(User user) {
        return notificationRepository.findByRecipientUserIdAndIsReadFalseOrderByCreatedAtDesc(user.getId());
    }

    public long getUnreadCount(User user) {
        return notificationRepository.countByRecipientUserIdAndIsReadFalse(user.getId());
    }

    @Transactional
    public void markAsRead(Long notificationId, User user) {
        Notification notification = notificationRepository.findByIdAndRecipientUserId(notificationId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with ID: " + notificationId + " for user: " + user.getEmail()));
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(LocalDateTime.now());
            notificationRepository.save(notification);
        }
    }

    @Transactional
    public void markAllAsRead(User user) {
        List<Notification> unread = notificationRepository.findByRecipientUserIdAndIsReadFalseOrderByCreatedAtDesc(user.getId());
        LocalDateTime now = LocalDateTime.now();
        for (Notification n : unread) {
            n.setRead(true);
            n.setReadAt(now);
            notificationRepository.save(n);
        }
    }
}
