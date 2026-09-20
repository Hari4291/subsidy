package com.example.digital_subsidy.service;

import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ReminderSchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(ReminderSchedulerService.class);

    private final ApplicationRepository applicationRepository;
    private final MilestoneRepository milestoneRepository;
    private final NotificationService notificationService;
    private final PriorityService priorityService;

    @Value("${subsidy.reminder.pending-hours:24}")
    private int pendingHoursThreshold = 24;

    public ReminderSchedulerService(ApplicationRepository applicationRepository,
                                   MilestoneRepository milestoneRepository,
                                   NotificationService notificationService,
                                   PriorityService priorityService) {
        this.applicationRepository = applicationRepository;
        this.milestoneRepository = milestoneRepository;
        this.notificationService = notificationService;
        this.priorityService = priorityService;
    }

    // Run scheduled reminders every 1 hour (cron or fixedDelay)
    @Scheduled(fixedDelayString = "${subsidy.reminder.fixed-delay-ms:3600000}")
    @Transactional
    public void processScheduledReminders() {
        logger.info("[REMINDER SCHEDULER] Running pending application & milestone reminder check...");
        
        // 0. Recalculate priority scores & aging for pending applications
        try {
            priorityService.recalculatePendingApplicationsPriority();
        } catch (Exception e) {
            logger.error("Error recalculating application priority scores", e);
        }

        String todayStr = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        LocalDateTime thresholdTime = LocalDateTime.now().minusHours(pendingHoursThreshold);

        List<Application> allApps = applicationRepository.findAll();

        for (Application app : allApps) {
            String status = app.getStatus() != null ? app.getStatus().toUpperCase() : "";
            String stage = app.getCurrentStage() != null ? app.getCurrentStage().toUpperCase() : "";

            // Do NOT send reminders for COMPLETED, CANCELLED, REJECTED or FINAL_REJECTED applications
            if ("COMPLETED".equals(status) || "CANCELLED".equals(status) || "REJECTED".equals(status) || "FINAL_REJECTED".equals(status)) {
                continue;
            }

            LocalDateTime checkDate = app.getUpdatedAt() != null ? app.getUpdatedAt() : app.getCreatedAt();
            if (checkDate != null && checkDate.isBefore(thresholdTime)) {

                // 1. FIELD_VERIFICATION Reminder
                if ("FIELD_VERIFICATION".equals(stage) || "RE_VERIFICATION_REQUIRED".equals(status)) {
                    String state = app.getBeneficiary() != null ? app.getBeneficiary().getState() : null;
                    String district = app.getBeneficiary() != null ? app.getBeneficiary().getDistrict() : null;
                    String eventKey = "REMINDER_FO_APP_" + app.getId() + "_" + todayStr;

                    if (app.getAssignedOfficer() != null && app.getAssignedOfficer().getUser() != null) {
                        notificationService.createNotification(
                                app.getAssignedOfficer().getUser(), app, "REMINDER_PENDING_FO",
                                "REMINDER: Field Verification Pending",
                                "Application #" + app.getApplicationNumber() + " (" + app.getScheme().getName() + ") has been pending field verification over " + pendingHoursThreshold + " hours.",
                                "HIGH", "/field-officer/verification/" + app.getId(), eventKey);
                    } else {
                        notificationService.notifyRoleUsers("FIELD_OFFICER", state, district, app,
                                "REMINDER_PENDING_FO", "REMINDER: Field Verification Pending",
                                "Application #" + app.getApplicationNumber() + " (" + app.getScheme().getName() + ") is pending ground verification.",
                                "MEDIUM", "/field-officer/dashboard", eventKey);
                    }
                }

                // 2. DISTRICT_REVIEW Reminder
                if ("DISTRICT_REVIEW".equals(stage)) {
                    String state = app.getBeneficiary() != null ? app.getBeneficiary().getState() : null;
                    String district = app.getBeneficiary() != null ? app.getBeneficiary().getDistrict() : null;
                    String eventKey = "REMINDER_DO_APP_" + app.getId() + "_" + todayStr;

                    notificationService.notifyRoleUsers("DISTRICT_OFFICER", state, district, app,
                            "REMINDER_PENDING_DO", "REMINDER: District Review Pending",
                            "Application #" + app.getApplicationNumber() + " (" + app.getScheme().getName() + ") is pending District Officer sanction review.",
                            "HIGH", "/officer/verify/" + app.getId(), eventKey);
                }

                // 3. FINANCE_APPROVAL Reminder
                if ("FINANCE_APPROVAL".equals(stage)) {
                    String eventKey = "REMINDER_FINANCE_APP_" + app.getId() + "_" + todayStr;

                    notificationService.notifyRoleUsers("FINANCE_APPROVER", null, null, app,
                            "REMINDER_PENDING_FINANCE", "REMINDER: Finance Approval Pending",
                            "Application #" + app.getApplicationNumber() + " (" + app.getScheme().getName() + ") is pending treasury sanction approval.",
                            "HIGH", "/finance/dashboard", eventKey);
                }
            }
        }

        // 4. Overdue Compliance Milestones Check
        List<Milestone> milestones = milestoneRepository.findAll();
        LocalDate today = LocalDate.now();
        for (Milestone m : milestones) {
            if (!"COMPLETED".equalsIgnoreCase(m.getCompletionStatus()) && m.getDueDate() != null && m.getDueDate().isBefore(today)) {
                if (m.getDisbursement() != null && m.getDisbursement().getApplication() != null) {
                    Application app = m.getDisbursement().getApplication();
                    String eventKey = "REMINDER_OVERDUE_MILESTONE_" + m.getId() + "_" + todayStr;

                    if (app.getBeneficiary() != null && app.getBeneficiary().getUser() != null) {
                        notificationService.createNotification(
                                app.getBeneficiary().getUser(), app, "COMPLIANCE_OVERDUE",
                                "OVERDUE: Compliance Milestone Due",
                                "Compliance milestone '" + m.getMilestoneName() + "' for Application #" + app.getApplicationNumber() + " was due on " + m.getDueDate() + ". Please submit required proof.",
                                "HIGH", "/disbursements/schedule/" + app.getId(), eventKey);
                    }
                }
            }
        }
        logger.info("[REMINDER SCHEDULER COMPLETED]");
    }
}
