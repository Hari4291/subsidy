package com.example.digital_subsidy.service;

import com.example.digital_subsidy.model.Application;
import com.example.digital_subsidy.model.ApplicationEscalation;
import com.example.digital_subsidy.repository.ApplicationEscalationRepository;
import com.example.digital_subsidy.repository.ApplicationRepository;
import com.example.digital_subsidy.util.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ApplicationEscalationService {

    private final ApplicationEscalationRepository escalationRepository;
    private final ApplicationRepository applicationRepository;
    private final AuditLogger auditLogger;
    private final int defaultThresholdDays = 7;

    public ApplicationEscalationService(ApplicationEscalationRepository escalationRepository,
                                        ApplicationRepository applicationRepository,
                                        AuditLogger auditLogger) {
        this.escalationRepository = escalationRepository;
        this.applicationRepository = applicationRepository;
        this.auditLogger = auditLogger;
    }

    @Transactional
    public void scanAndEvaluateEscalations() {
        List<Application> pendingApps = applicationRepository.findByStatus("PENDING");
        LocalDateTime now = LocalDateTime.now();

        for (Application app : pendingApps) {
            LocalDateTime refTime = app.getUpdatedAt() != null ? app.getUpdatedAt() : app.getCreatedAt();
            long pendingDays = Duration.between(refTime, now).toDays();

            if (pendingDays >= defaultThresholdDays) {
                String severity = pendingDays >= 14 ? "CRITICAL" : "ESCALATED";
                String reason = "Application pending in stage '" + app.getCurrentStage() + "' for " + pendingDays + " days (threshold: " + defaultThresholdDays + " days).";

                ApplicationEscalation escalation = escalationRepository.findByApplicationIdAndStatus(app.getId(), "OPEN")
                        .orElse(new ApplicationEscalation(app, (int) pendingDays, defaultThresholdDays, severity, reason));

                escalation.setPendingDays((int) pendingDays);
                escalation.setSeverity(severity);
                escalation.setEscalationReason(reason);
                escalationRepository.save(escalation);
            }
        }
    }

    @Transactional
    public ApplicationEscalation createEscalation(Application app, String reason, String escalatedBy) {
        ApplicationEscalation esc = new ApplicationEscalation(app, 0, defaultThresholdDays, "ESCALATED", reason);
        esc.setEscalatedByRole("FIELD_OFFICER");
        return escalationRepository.save(esc);
    }

    public List<ApplicationEscalation> getEscalationsForDistrict(String state, String filter) {
        scanAndEvaluateEscalations();
        List<ApplicationEscalation> all = (state != null && !state.isBlank())
                ? escalationRepository.findByApplicationBeneficiaryState(state)
                : escalationRepository.findAll();

        if ("ESCALATED".equalsIgnoreCase(filter)) {
            return all.stream().filter(e -> "OPEN".equalsIgnoreCase(e.getStatus()) && "ESCALATED".equalsIgnoreCase(e.getSeverity())).toList();
        } else if ("CRITICAL".equalsIgnoreCase(filter)) {
            return all.stream().filter(e -> "OPEN".equalsIgnoreCase(e.getStatus()) && "CRITICAL".equalsIgnoreCase(e.getSeverity())).toList();
        } else if ("RESOLVED".equalsIgnoreCase(filter)) {
            return all.stream().filter(e -> "RESOLVED".equalsIgnoreCase(e.getStatus())).toList();
        } else if ("OPEN".equalsIgnoreCase(filter)) {
            return all.stream().filter(e -> "OPEN".equalsIgnoreCase(e.getStatus())).toList();
        }
        return all;
    }

    @Transactional
    public ApplicationEscalation resolveEscalation(Long escalationId, String remarks, String resolvedBy) {
        ApplicationEscalation escalation = escalationRepository.findById(escalationId).orElse(null);
        if (escalation != null) {
            escalation.setStatus("RESOLVED");
            escalation.setRemarks(remarks);
            escalation.setResolvedBy(resolvedBy);
            escalation.setResolvedAt(LocalDateTime.now());
            ApplicationEscalation saved = escalationRepository.save(escalation);
            auditLogger.log("ESCALATION_RESOLVED", "ApplicationEscalation", escalation.getId(),
                    "Escalation #" + escalation.getId() + " marked RESOLVED by " + resolvedBy + ". Remarks: " + remarks);
            return saved;
        }
        return null;
    }
}
