package com.example.digital_subsidy.service;

import com.example.digital_subsidy.dto.PriorityBreakdownDto;
import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.ApplicationRepository;
import com.example.digital_subsidy.repository.SchemeRepository;
import com.example.digital_subsidy.util.AuditLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class PriorityService {

    private static final Logger logger = LoggerFactory.getLogger(PriorityService.class);

    private final ApplicationRepository applicationRepository;
    private final SchemeRepository schemeRepository;
    private final AuditLogger auditLogger;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public PriorityService(ApplicationRepository applicationRepository,
                           SchemeRepository schemeRepository,
                           AuditLogger auditLogger,
                           NotificationService notificationService,
                           @Autowired(required = false) ObjectMapper objectMapper) {
        this.applicationRepository = applicationRepository;
        this.schemeRepository = schemeRepository;
        this.auditLogger = auditLogger;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper != null ? objectMapper : createDefaultObjectMapper();
    }

    private static ObjectMapper createDefaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }

    @Transactional
    public Application calculatePriority(Application app) {
        if (app == null || app.getScheme() == null) {
            return app;
        }

        Scheme scheme = app.getScheme();

        // Check if manual override is active
        if (Boolean.TRUE.equals(app.getIsPriorityOverridden())) {
            logger.info("Application #{} has manual priority override active. Preserving score {} and level {}.",
                    app.getApplicationNumber(), app.getPriorityScore(), app.getPriorityLevel());
            return app;
        }

        // Default factors & weights
        boolean isEnabled = scheme.getPriorityQueueEnabled();
        int wElig = isEnabled ? scheme.getEligibilityWeight() : 100;
        int wWait = isEnabled ? scheme.getWaitingWeight() : 0;
        int wVuln = isEnabled ? scheme.getVulnerabilityWeight() : 0;

        // 1. Eligibility Factor (0-100)
        int rawEligibility = app.getEligibilityScore() != null ? Math.max(0, Math.min(100, app.getEligibilityScore())) : 0;
        BigDecimal eligContribution = BigDecimal.valueOf(rawEligibility)
                .multiply(BigDecimal.valueOf(wElig))
                .divide(BigDecimal.valueOf(100), 1, RoundingMode.HALF_UP);

        // 2. Waiting Time Factor (0-100)
        LocalDate subDate = app.getCreatedAt() != null ? app.getCreatedAt().toLocalDate() : LocalDate.now();
        long waitingDays = ChronoUnit.DAYS.between(subDate, LocalDate.now());
        if (waitingDays < 0) waitingDays = 0;

        int maxAgingDays = scheme.getMaxAgingDays() != null && scheme.getMaxAgingDays() > 0 ? scheme.getMaxAgingDays() : 30;
        double rawWaitingFactorDouble = Math.min(100.0, (waitingDays * 100.0) / maxAgingDays);
        BigDecimal waitingFactor = BigDecimal.valueOf(rawWaitingFactorDouble).setScale(1, RoundingMode.HALF_UP);

        BigDecimal waitingContribution = waitingFactor
                .multiply(BigDecimal.valueOf(wWait))
                .divide(BigDecimal.valueOf(100), 1, RoundingMode.HALF_UP);

        // 3. Vulnerability Factor (0-100)
        BigDecimal vulnFactor = BigDecimal.ZERO;
        List<String> vulnDetails = new ArrayList<>();
        Beneficiary beneficiary = app.getBeneficiary();

        if (beneficiary != null) {
            int scoreAccumulator = 0;

            // Category indicator (Affirmative welfare inclusion)
            String category = beneficiary.getCategory() != null ? beneficiary.getCategory().trim().toUpperCase() : "";
            if ("ST".equals(category) || "SC".equals(category)) {
                scoreAccumulator += 40;
                vulnDetails.add("Social Category Targeted Support (" + category + "): +40 pts");
            } else if ("OBC".equals(category)) {
                scoreAccumulator += 20;
                vulnDetails.add("OBC Category Priority: +20 pts");
            }

            // Age indicator (Senior Citizens >= 60)
            if (beneficiary.getAge() != null && beneficiary.getAge() >= 60) {
                scoreAccumulator += 30;
                vulnDetails.add("Senior Citizen Beneficiary (Age " + beneficiary.getAge() + "): +30 pts");
            } else if (beneficiary.getAge() != null && beneficiary.getAge() <= 25) {
                scoreAccumulator += 10;
                vulnDetails.add("Youth Beneficiary Priority (Age " + beneficiary.getAge() + "): +10 pts");
            }

            // Gender indicator (Female applicants)
            String gender = beneficiary.getGender() != null ? beneficiary.getGender().trim().toUpperCase() : "";
            if ("FEMALE".equals(gender) || "WOMAN".equals(gender) || "F".equals(gender)) {
                scoreAccumulator += 30;
                vulnDetails.add("Female Applicant Welfare Priority: +30 pts");
            }

            vulnFactor = BigDecimal.valueOf(Math.min(100, scoreAccumulator)).setScale(1, RoundingMode.HALF_UP);
        }

        BigDecimal vulnContribution = vulnFactor
                .multiply(BigDecimal.valueOf(wVuln))
                .divide(BigDecimal.valueOf(100), 1, RoundingMode.HALF_UP);

        // Composite Final Priority Score
        BigDecimal finalScore = eligContribution.add(waitingContribution).add(vulnContribution)
                .setScale(1, RoundingMode.HALF_UP);

        // Priority Level Classification
        int highThresh = scheme.getHighPriorityThreshold() != null ? scheme.getHighPriorityThreshold() : 75;
        int medThresh = scheme.getMediumPriorityThreshold() != null ? scheme.getMediumPriorityThreshold() : 50;

        String level = "STANDARD";
        if (finalScore.compareTo(BigDecimal.valueOf(highThresh)) >= 0) {
            level = "HIGH";
        } else if (finalScore.compareTo(BigDecimal.valueOf(medThresh)) >= 0) {
            level = "MEDIUM";
        }

        // Construct Breakdown DTO
        PriorityBreakdownDto breakdown = new PriorityBreakdownDto();
        breakdown.setEligibilityScore(rawEligibility);
        breakdown.setEligibilityWeight(wElig);
        breakdown.setEligibilityContribution(eligContribution);

        breakdown.setWaitingDays(waitingDays);
        breakdown.setWaitingFactor(waitingFactor);
        breakdown.setWaitingWeight(wWait);
        breakdown.setWaitingContribution(waitingContribution);

        breakdown.setVulnerabilityFactor(vulnFactor);
        breakdown.setVulnerabilityWeight(wVuln);
        breakdown.setVulnerabilityContribution(vulnContribution);
        breakdown.setVulnerabilityDetails(vulnDetails);

        breakdown.setFinalPriorityScore(finalScore);
        breakdown.setPriorityLevel(level);
        breakdown.setOverridden(false);

        try {
            app.setPriorityBreakdownJson(objectMapper.writeValueAsString(breakdown));
        } catch (Exception e) {
            logger.error("Failed to serialize priority breakdown for app #{}", app.getId(), e);
        }

        app.setPriorityScore(finalScore);
        app.setPriorityLevel(level);
        app.setPriorityCalculatedAt(LocalDateTime.now());

        return applicationRepository.save(app);
    }

    @Transactional
    public void recalculatePendingApplicationsPriority() {
        logger.info("[PRIORITY QUEUE SCHEDULER] Running pending application priority recalculation & aging update...");
        List<Application> pendingApps = applicationRepository.findAll().stream()
                .filter(a -> !"APPROVED".equalsIgnoreCase(a.getStatus()) &&
                             !"REJECTED".equalsIgnoreCase(a.getStatus()) &&
                             !"FINAL_REJECTED".equalsIgnoreCase(a.getStatus()) &&
                             !"CANCELLED".equalsIgnoreCase(a.getStatus()) &&
                             !"COMPLETED".equalsIgnoreCase(a.getStatus()))
                .toList();

        for (Application app : pendingApps) {
            calculatePriority(app);

            // Escalation reminder check for long pending queue items
            if (app.getScheme() != null && app.getCreatedAt() != null) {
                long daysPending = ChronoUnit.DAYS.between(app.getCreatedAt().toLocalDate(), LocalDate.now());
                int escDays = app.getScheme().getEscalationWaitingDays() != null ? app.getScheme().getEscalationWaitingDays() : 7;

                if (daysPending >= escDays) {
                    String stage = app.getCurrentStage() != null ? app.getCurrentStage().toUpperCase() : "VERIFICATION";
                    String todayStr = LocalDate.now().toString();
                    String eventKey = "REMINDER_PRIORITY_ESCALATION_" + app.getId() + "_" + todayStr;

                    String targetRole = "FIELD_OFFICER";
                    String targetUrl = "/field-officer/dashboard";
                    if ("DISTRICT_REVIEW".equals(stage)) {
                        targetRole = "DISTRICT_OFFICER";
                        targetUrl = "/district-officer/dashboard";
                    } else if ("FINANCE_APPROVAL".equals(stage)) {
                        targetRole = "FINANCE_APPROVER";
                        targetUrl = "/finance/dashboard";
                    }

                    String state = app.getBeneficiary() != null ? app.getBeneficiary().getState() : null;
                    String district = app.getBeneficiary() != null ? app.getBeneficiary().getDistrict() : null;

                    notificationService.notifyRoleUsers(
                            targetRole, state, district, app,
                            "PRIORITY_ESCALATION",
                            "ESCALATION: High Waiting-Time Application #" + app.getApplicationNumber(),
                            "Application #" + app.getApplicationNumber() + " (" + app.getScheme().getName() + ") has been pending for " + daysPending + " days (Priority: " + app.getPriorityLevel() + " / Score: " + app.getPriorityScore() + "). Please review promptly.",
                            "HIGH", targetUrl, eventKey
                    );
                }
            }
        }
    }

    @Transactional
    public Application manualOverridePriority(Long applicationId, String newLevel, BigDecimal newScore, String reason, String officerEmail, String officerRole) {
        if (!"ROLE_DISTRICT_OFFICER".equalsIgnoreCase(officerRole) &&
            !"DISTRICT_OFFICER".equalsIgnoreCase(officerRole) &&
            !"ROLE_ADMIN".equalsIgnoreCase(officerRole) &&
            !"ADMIN".equalsIgnoreCase(officerRole)) {
            throw new AccessDeniedException("Only District Officers and Admins are authorized to override application priority.");
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A mandatory override reason must be provided when manually changing priority.");
        }

        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found with ID: " + applicationId));

        String oldLevel = app.getPriorityLevel();
        BigDecimal oldScore = app.getPriorityScore();

        String targetLevel = (newLevel != null && !newLevel.isBlank()) ? newLevel.trim().toUpperCase() : oldLevel;
        if (!"HIGH".equals(targetLevel) && !"MEDIUM".equals(targetLevel) && !"STANDARD".equals(targetLevel)) {
            throw new IllegalArgumentException("Invalid priority level. Allowed values: HIGH, MEDIUM, STANDARD.");
        }

        BigDecimal targetScore = newScore != null ? newScore.setScale(1, RoundingMode.HALF_UP) : oldScore;

        app.setPriorityLevel(targetLevel);
        app.setPriorityScore(targetScore);
        app.setIsPriorityOverridden(true);
        app.setPriorityOverrideReason(reason.trim());
        app.setPriorityOverriddenBy(officerEmail);
        app.setPriorityOverriddenAt(LocalDateTime.now());
        app.setPriorityCalculatedAt(LocalDateTime.now());

        // Update Breakdown DTO for transparency
        PriorityBreakdownDto breakdown = getPriorityBreakdown(app);
        breakdown.setFinalPriorityScore(targetScore);
        breakdown.setPriorityLevel(targetLevel);
        breakdown.setOverridden(true);
        breakdown.setOverrideReason(reason.trim());
        breakdown.setOverriddenBy(officerEmail);

        try {
            app.setPriorityBreakdownJson(objectMapper.writeValueAsString(breakdown));
        } catch (Exception e) {
            logger.error("Failed to serialize override breakdown for app #{}", app.getId(), e);
        }

        Application saved = applicationRepository.save(app);

        auditLogger.log(
                "PRIORITY_MANUAL_OVERRIDE",
                "Application",
                app.getId(),
                "Officer " + officerEmail + " manually changed priority for App #" + app.getApplicationNumber() +
                        " from [" + oldLevel + " / " + oldScore + "] to [" + targetLevel + " / " + targetScore + "]. Reason: " + reason
        );

        logger.info("[PRIORITY OVERRIDE] App ID: {} | Old: {} ({}) -> New: {} ({}) | By: {}",
                app.getId(), oldLevel, oldScore, targetLevel, targetScore, officerEmail);

        return saved;
    }

    public PriorityBreakdownDto getPriorityBreakdown(Application app) {
        if (app != null && app.getPriorityBreakdownJson() != null && !app.getPriorityBreakdownJson().isBlank()) {
            try {
                return objectMapper.readValue(app.getPriorityBreakdownJson(), PriorityBreakdownDto.class);
            } catch (Exception e) {
                logger.warn("Failed to parse priorityBreakdownJson for app #{}. Fallback to dynamic calculation.", app.getId());
            }
        }

        // Fallback breakdown
        PriorityBreakdownDto dto = new PriorityBreakdownDto();
        if (app != null) {
            dto.setEligibilityScore(app.getEligibilityScore() != null ? app.getEligibilityScore() : 0);
            dto.setFinalPriorityScore(app.getPriorityScore() != null ? app.getPriorityScore() : BigDecimal.ZERO);
            dto.setPriorityLevel(app.getPriorityLevel() != null ? app.getPriorityLevel() : "STANDARD");
            dto.setOverridden(Boolean.TRUE.equals(app.getIsPriorityOverridden()));
            dto.setOverrideReason(app.getPriorityOverrideReason());
            dto.setOverriddenBy(app.getPriorityOverriddenBy());
        }
        return dto;
    }

    public List<Application> sortApplicationsByPriority(List<Application> applications) {
        if (applications == null || applications.isEmpty()) {
            return new ArrayList<>();
        }

        List<Application> modifiableList = new ArrayList<>(applications);
        modifiableList.sort(Comparator
                .comparing((Application a) -> getPriorityRank(a.getPriorityLevel()))
                .thenComparing((Application a) -> a.getPriorityScore() != null ? a.getPriorityScore() : BigDecimal.ZERO, Comparator.reverseOrder())
                .thenComparing((Application a) -> a.getCreatedAt() != null ? a.getCreatedAt() : LocalDateTime.MIN)
                .thenComparing(Application::getId)
        );

        return modifiableList;
    }

    private int getPriorityRank(String level) {
        if ("HIGH".equalsIgnoreCase(level)) return 1;
        if ("MEDIUM".equalsIgnoreCase(level)) return 2;
        return 3; // STANDARD
    }
}
