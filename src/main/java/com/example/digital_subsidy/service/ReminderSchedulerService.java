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
    private final com.example.digital_subsidy.util.AuditLogger auditLogger;

    @Value("${subsidy.reminder.pending-hours:24}")
    private int pendingHoursThreshold = 24;

    public ReminderSchedulerService(ApplicationRepository applicationRepository,
                                   MilestoneRepository milestoneRepository,
                                   NotificationService notificationService,
                                   PriorityService priorityService,
                                   com.example.digital_subsidy.util.AuditLogger auditLogger) {
        this.applicationRepository = applicationRepository;
        this.milestoneRepository = milestoneRepository;
        this.notificationService = notificationService;
        this.priorityService = priorityService;
        this.auditLogger = auditLogger;
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

        // Process compliance reminders & overdue detection
        processComplianceReminders();
        processOverdueCompliance();

        logger.info("[REMINDER SCHEDULER COMPLETED]");
    }

    @Transactional
    public int processComplianceReminders() {
        List<Milestone> milestones = milestoneRepository.findAll();
        LocalDate today = LocalDate.now();
        String todayStr = today.format(DateTimeFormatter.ISO_DATE);
        int reminderCount = 0;

        for (Milestone m : milestones) {
            if ("VERIFIED".equalsIgnoreCase(m.getComplianceVerificationStatus()) ||
                "SUBMITTED".equalsIgnoreCase(m.getComplianceVerificationStatus()) ||
                "UNDER_REVIEW".equalsIgnoreCase(m.getComplianceVerificationStatus()) ||
                "RELEASED".equalsIgnoreCase(m.getCompletionStatus()) ||
                "COMPLETED".equalsIgnoreCase(m.getCompletionStatus())) {
                continue;
            }

            if (m.getDueDate() == null) continue;

            long daysUntilDue = java.time.temporal.ChronoUnit.DAYS.between(today, m.getDueDate());
            Application app = m.getDisbursement() != null ? m.getDisbursement().getApplication() : null;
            if (app == null || app.getBeneficiary() == null || app.getBeneficiary().getUser() == null) continue;

            String window = null;
            if (daysUntilDue == 7) window = "7_DAYS";
            else if (daysUntilDue == 3) window = "3_DAYS";
            else if (daysUntilDue == 1) window = "1_DAY";
            else if (daysUntilDue == 0) window = "DUE_TODAY";

            if (window != null) {
                String eventKey = "REMINDER_COMPLIANCE_MS_" + m.getId() + "_" + window + "_" + todayStr;
                String docName = DisbursementService.getDocumentTypeDisplayName(m.getRequiredDocumentType());
                String title = daysUntilDue == 0 ? "URGENT: " + docName + " Due Today" : "REMINDER: Please Upload " + docName + " for Term " + m.getStageNumber();
                String msg = "Reminder: Please upload your " + docName + " for Term " + m.getStageNumber() + " (" + (app.getScheme() != null ? app.getScheme().getName() : "Subsidy") + ") before " + m.getDueDate() + ".";

                var createdNotif = notificationService.createNotification(
                        app.getBeneficiary().getUser(), app, "COMPLIANCE_REMINDER",
                        title, msg, daysUntilDue <= 1 ? "HIGH" : "MEDIUM",
                        "/disbursements/schedule/" + app.getId(), eventKey
                );

                if (createdNotif != null) {
                    m.setReminderStatus(window + "_SENT");
                    m.setLastReminderDate(LocalDateTime.now());
                    milestoneRepository.save(m);
                    reminderCount++;

                    auditLogger.log("COMPLIANCE_REMINDER_SENT", "Milestone", m.getId(),
                            "Generated compliance reminder (" + window + ") for " + m.getMilestoneName());
                }
            }
        }
        return reminderCount;
    }

    @Transactional
    public int processOverdueCompliance() {
        List<Milestone> milestones = milestoneRepository.findAll();
        LocalDate today = LocalDate.now();
        String todayStr = today.format(DateTimeFormatter.ISO_DATE);
        int overdueCount = 0;

        for (Milestone m : milestones) {
            if ("VERIFIED".equalsIgnoreCase(m.getComplianceVerificationStatus()) || "RELEASED".equalsIgnoreCase(m.getCompletionStatus()) || "COMPLETED".equalsIgnoreCase(m.getCompletionStatus())) {
                continue;
            }

            if (m.getDueDate() != null && m.getDueDate().isBefore(today)) {
                boolean wasOverdue = Boolean.TRUE.equals(m.getOverdueFlag()) || "OVERDUE".equalsIgnoreCase(m.getComplianceVerificationStatus());
                
                m.setComplianceVerificationStatus("OVERDUE");
                m.setComplianceStatus("NON_COMPLIANT");
                m.setOverdueFlag(Boolean.TRUE);
                milestoneRepository.save(m);

                Application app = m.getDisbursement() != null ? m.getDisbursement().getApplication() : null;
                if (app != null && app.getBeneficiary() != null && app.getBeneficiary().getUser() != null) {
                    String eventKey = "OVERDUE_COMPLIANCE_MS_" + m.getId() + "_" + todayStr;
                    notificationService.createNotification(
                            app.getBeneficiary().getUser(), app, "COMPLIANCE_OVERDUE",
                            "OVERDUE: Compliance Milestone Overdue",
                            "Compliance milestone '" + m.getMilestoneName() + "' for Application #" + app.getApplicationNumber() + " was due on " + m.getDueDate() + ". Release is blocked until compliance is verified.",
                            "HIGH", "/disbursements/schedule/" + app.getId(), eventKey);
                }

                if (!wasOverdue) {
                    auditLogger.log("COMPLIANCE_OVERDUE_FLAGGED", "Milestone", m.getId(),
                            "Milestone '" + m.getMilestoneName() + "' flagged OVERDUE / NON_COMPLIANT");
                }
                overdueCount++;
            }
        }
        return overdueCount;
    }
}
