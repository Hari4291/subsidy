package com.example.digital_subsidy.controller;

import com.example.digital_subsidy.model.Notification;
import com.example.digital_subsidy.model.User;
import com.example.digital_subsidy.repository.UserRepository;
import com.example.digital_subsidy.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class NotificationController {

    private static final Logger logger = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NotificationController(NotificationService notificationService, UserRepository userRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @GetMapping("/notifications")
    public String viewNotifications(@RequestParam(value = "filter", required = false, defaultValue = "all") String filter,
                                    Authentication authentication,
                                    Model model) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AccessDeniedException("User not found: " + email));

        List<Notification> allNotifs = notificationService.getUserNotifications(user);
        List<Notification> filteredList;

        if ("unread".equalsIgnoreCase(filter)) {
            filteredList = allNotifs.stream().filter(n -> !n.isRead()).toList();
        } else if ("read".equalsIgnoreCase(filter)) {
            filteredList = allNotifs.stream().filter(Notification::isRead).toList();
        } else {
            filteredList = allNotifs;
        }

        long unreadCount = allNotifs.stream().filter(n -> !n.isRead()).count();

        model.addAttribute("notifications", filteredList);
        model.addAttribute("allCount", allNotifs.size());
        model.addAttribute("unreadCount", unreadCount);
        model.addAttribute("readCount", allNotifs.size() - unreadCount);
        model.addAttribute("currentFilter", filter);
        model.addAttribute("user", user);

        return "notifications";
    }

    @PostMapping({"/notifications/{id}/read", "/notifications/{id}/mark-read"})
    public String markAsRead(@PathVariable("id") Long id,
                             @RequestParam(value = "redirect", required = false) String redirectUrl,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        if (authentication == null) {
            return "redirect:/login";
        }
        String email = authentication.getName();
        User user = userRepository.findByEmail(email).orElseThrow();

        try {
            notificationService.markAsRead(id, user);
        } catch (Exception e) {
            logger.error("Error marking notification {} as read for user {}", id, email, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        if (redirectUrl != null && !redirectUrl.isBlank()) {
            return "redirect:" + redirectUrl;
        }
        return "redirect:/notifications";
    }

    @PostMapping({"/notifications/read-all", "/notifications/mark-all-read"})
    public String markAllAsRead(Authentication authentication, RedirectAttributes redirectAttributes) {
        if (authentication == null) {
            return "redirect:/login";
        }
        String email = authentication.getName();
        User user = userRepository.findByEmail(email).orElseThrow();

        notificationService.markAllAsRead(user);
        redirectAttributes.addFlashAttribute("successMessage", "All notifications marked as read.");
        return "redirect:/notifications";
    }

    @GetMapping("/api/notifications/unread")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getUnreadNotifications(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.ok(List.of());
        }

        String email = authentication.getName();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return ResponseEntity.ok(List.of());
        }

        List<Notification> unreadList = notificationService.getUnreadNotifications(user);

        List<Map<String, Object>> response = unreadList.stream().map(n -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", n.getId());
            m.put("type", n.getNotificationType());
            m.put("title", n.getTitle());
            m.put("message", n.getMessage());
            m.put("priority", n.getPriority() != null ? n.getPriority() : "NORMAL");
            m.put("actionUrl", n.getActionUrl());
            m.put("isRead", n.isRead());
            m.put("createdAt", n.getCreatedAt() != null ? n.getCreatedAt().toString() : "");
            return m;
        }).toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/notifications/unread-count")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getUnreadCount(Authentication authentication) {
        Map<String, Object> resp = new HashMap<>();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            resp.put("unreadCount", 0);
            return ResponseEntity.ok(resp);
        }

        String email = authentication.getName();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            resp.put("unreadCount", 0);
            return ResponseEntity.ok(resp);
        }

        long count = notificationService.getUnreadCount(user);
        List<Notification> recentUnread = notificationService.getUnreadNotifications(user).stream().limit(5).toList();

        resp.put("unreadCount", count);
        resp.put("recentNotifications", recentUnread.stream().map(n -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", n.getId());
            m.put("title", n.getTitle());
            m.put("message", n.getMessage());
            m.put("priority", n.getPriority() != null ? n.getPriority() : "NORMAL");
            m.put("actionUrl", n.getActionUrl());
            m.put("createdAt", n.getCreatedAt() != null ? n.getCreatedAt().toString() : "");
            return m;
        }).toList());

        return ResponseEntity.ok(resp);
    }
}
