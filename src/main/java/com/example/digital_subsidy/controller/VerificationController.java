package com.example.digital_subsidy.controller;

import com.example.digital_subsidy.dto.VerificationActionDto;
import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.ApplicationEligibilityAnswerRepository;
import com.example.digital_subsidy.repository.AuditLogRepository;
import com.example.digital_subsidy.repository.OfficerRepository;
import com.example.digital_subsidy.repository.RequiredDocumentRepository;
import com.example.digital_subsidy.repository.VerificationRepository;
import com.example.digital_subsidy.service.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class VerificationController {

    private static final Logger logger = LoggerFactory.getLogger(VerificationController.class);

    private final ApplicationService applicationService;
    private final VerificationService verificationService;
    private final DocumentService documentService;
    private final OfficerRepository officerRepository;
    private final VerificationRepository verificationRepository;
    private final ApplicationEligibilityAnswerRepository answerRepository;
    private final RequiredDocumentRepository requiredDocumentRepository;
    private final AnomalyDetectionService anomalyDetectionService;
    private final ApplicationEscalationService escalationService;
    private final DistrictBudgetService districtBudgetService;
    private final GrievanceService grievanceService;
    private final OfficerService officerService;
    private final AuditLogRepository auditLogRepository;
    private final PriorityService priorityService;
    private final com.example.digital_subsidy.service.FundUtilizationService fundUtilizationService;
    private final DisbursementService disbursementService;

    public VerificationController(ApplicationService applicationService,
                                  VerificationService verificationService,
                                  DocumentService documentService,
                                  OfficerRepository officerRepository,
                                  VerificationRepository verificationRepository,
                                  ApplicationEligibilityAnswerRepository answerRepository,
                                  RequiredDocumentRepository requiredDocumentRepository,
                                  AnomalyDetectionService anomalyDetectionService,
                                  ApplicationEscalationService escalationService,
                                  DistrictBudgetService districtBudgetService,
                                  GrievanceService grievanceService,
                                  OfficerService officerService,
                                  AuditLogRepository auditLogRepository,
                                  PriorityService priorityService,
                                  com.example.digital_subsidy.service.FundUtilizationService fundUtilizationService,
                                  DisbursementService disbursementService) {
        this.applicationService = applicationService;
        this.verificationService = verificationService;
        this.documentService = documentService;
        this.officerRepository = officerRepository;
        this.verificationRepository = verificationRepository;
        this.answerRepository = answerRepository;
        this.requiredDocumentRepository = requiredDocumentRepository;
        this.anomalyDetectionService = anomalyDetectionService;
        this.escalationService = escalationService;
        this.districtBudgetService = districtBudgetService;
        this.grievanceService = grievanceService;
        this.officerService = officerService;
        this.auditLogRepository = auditLogRepository;
        this.priorityService = priorityService;
        this.fundUtilizationService = fundUtilizationService;
        this.disbursementService = disbursementService;
    }

    // ═══════════════════════════════════════════════════════
    //  FIELD OFFICER
    // ═══════════════════════════════════════════════════════

    @GetMapping("/field-officer/dashboard")
    public String fieldOfficerDashboard(@RequestParam(value = "priorityLevel", required = false) String priorityLevel,
                                        @RequestParam(value = "schemeId", required = false) Long schemeId,
                                        Authentication authentication, Model model) {
        String email = authentication != null ? authentication.getName() : "";
        Officer officer = officerRepository.findByUserEmail(email).orElse(null);

        List<Application> pendingApps = applicationService.getApplicationsByStage("FIELD_VERIFICATION");

        if (officer != null && officer.getState() != null && !officer.getState().isBlank()) {
            pendingApps = pendingApps.stream()
                    .filter(a -> a.getBeneficiary() != null && officer.getState().equalsIgnoreCase(a.getBeneficiary().getState()))
                    .filter(a -> a.getAssignedOfficer() == null || (a.getAssignedOfficer().getId().equals(officer.getId())))
                    .toList();
        }

        if (priorityLevel != null && !priorityLevel.isBlank()) {
            pendingApps = pendingApps.stream()
                    .filter(a -> priorityLevel.equalsIgnoreCase(a.getPriorityLevel()))
                    .toList();
        }

        if (schemeId != null) {
            pendingApps = pendingApps.stream()
                    .filter(a -> a.getScheme() != null && schemeId.equals(a.getScheme().getId()))
                    .toList();
        }

        pendingApps = priorityService.sortApplicationsByPriority(pendingApps);

        List<Application> initialAssignedApps = new java.util.ArrayList<>();
        List<Application> reverificationApps = new java.util.ArrayList<>();
        Map<Long, Map<String, Object>> foReverificationDetailsMap = new HashMap<>();

        for (Application app : pendingApps) {
            List<Verification> verifications = verificationRepository.findByApplicationOrderByVerifiedAtDesc(app);
            Verification doReverifyReq = verifications.stream()
                    .filter(v -> ("DISTRICT_OFFICER".equalsIgnoreCase(v.getOfficerRole()) || "ROLE_DISTRICT_OFFICER".equalsIgnoreCase(v.getOfficerRole()))
                            && ("REQUEST_RE_VERIFICATION".equalsIgnoreCase(v.getStatus()) || "RE_VERIFICATION_REQUESTED".equalsIgnoreCase(v.getStatus()) || "RE_VERIFICATION_REQUIRED".equalsIgnoreCase(v.getStatus())))
                    .findFirst()
                    .orElse(null);

            boolean isReverification = "RE_VERIFICATION_REQUIRED".equalsIgnoreCase(app.getStatus()) || doReverifyReq != null;

            if (!isReverification) {
                initialAssignedApps.add(app);
            } else {
                reverificationApps.add(app);

                Verification previousFoVerification = verifications.stream()
                        .filter(v -> ("FIELD_OFFICER".equalsIgnoreCase(v.getOfficerRole()) || "ROLE_FIELD_OFFICER".equalsIgnoreCase(v.getOfficerRole()))
                                && v.getVerifiedAt() != null)
                        .findFirst()
                        .orElse(null);

                Map<String, Object> details = new HashMap<>();
                details.put("doRemarks", (doReverifyReq != null && doReverifyReq.getRemarks() != null) ? doReverifyReq.getRemarks() : (app.getRejectionReason() != null ? app.getRejectionReason() : "Re-verification requested by District Officer"));
                details.put("requestedDate", doReverifyReq != null ? doReverifyReq.getVerifiedAt() : app.getUpdatedAt());
                details.put("previousFoRemarks", previousFoVerification != null ? previousFoVerification.getRemarks() : "No previous report recorded.");
                details.put("previousFoDate", previousFoVerification != null ? previousFoVerification.getVerifiedAt() : null);
                foReverificationDetailsMap.put(app.getId(), details);
            }
        }

        initialAssignedApps = priorityService.sortApplicationsByPriority(initialAssignedApps);
        reverificationApps = priorityService.sortApplicationsByPriority(reverificationApps);

        Map<Long, com.example.digital_subsidy.dto.PriorityBreakdownDto> breakdownMap = new HashMap<>();
        for (Application app : pendingApps) {
            breakdownMap.put(app.getId(), priorityService.getPriorityBreakdown(app));
        }

        List<Verification> myHistory = verificationRepository.findByOfficerUserEmailOrderByIdDesc(email);
        List<Grievance> assignedGrievances = officer != null ? grievanceService.getGrievancesAssignedToOfficer(officer.getId()) : List.of();

        List<Milestone> pendingTermDocumentMilestones = documentService != null ? 
                disbursementService.getPendingTermDocumentVerificationForFieldOfficer(officer != null ? officer.getState() : null, officer != null ? officer.getDistrict() : null) : List.of();

        model.addAttribute("officer", officer);
        model.addAttribute("pendingApplications", pendingApps);
        model.addAttribute("initialApplications", initialAssignedApps);
        model.addAttribute("reverificationApplications", reverificationApps);
        model.addAttribute("pendingTermDocumentMilestones", pendingTermDocumentMilestones);
        model.addAttribute("foReverificationDetailsMap", foReverificationDetailsMap);
        model.addAttribute("breakdownMap", breakdownMap);
        model.addAttribute("selectedPriorityLevel", priorityLevel);
        model.addAttribute("selectedSchemeId", schemeId);
        model.addAttribute("verificationHistoryCount", myHistory.size());
        model.addAttribute("assignedGrievances", assignedGrievances);
        model.addAttribute("actionDto", new VerificationActionDto());
        return "field-officer/dashboard";
    }

    @GetMapping("/field-officer/verification/{id}")
    public String fieldOfficerVerificationDetails(@PathVariable("id") Long id,
                                                 Authentication authentication,
                                                 Model model,
                                                 RedirectAttributes redirectAttributes) {
        try {
            Application app = applicationService.getApplicationById(id);
            if (app == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Application not found with ID: " + id);
                return "redirect:/field-officer/dashboard";
            }

            // IDOR & Regional Authorization
            if (authentication != null) {
                String email = authentication.getName();
                Officer officer = officerRepository.findByUserEmail(email).orElse(null);
                if (officer != null && officer.getState() != null && app.getBeneficiary() != null) {
                    if (!officer.getState().equalsIgnoreCase(app.getBeneficiary().getState())) {
                        redirectAttributes.addFlashAttribute("errorMessage", "Access Denied: You are not authorized for state " + app.getBeneficiary().getState());
                        return "redirect:/field-officer/dashboard";
                    }
                    if (officer.getDistrict() != null && !officer.getDistrict().isBlank() && app.getBeneficiary().getDistrict() != null) {
                        if (!officer.getDistrict().equalsIgnoreCase(app.getBeneficiary().getDistrict())) {
                            redirectAttributes.addFlashAttribute("errorMessage", "Access Denied: You are not authorized for district " + app.getBeneficiary().getDistrict());
                            return "redirect:/field-officer/dashboard";
                        }
                    }
                }
            }

            List<Document> applicationDocs = documentService.getLatestActiveDocumentsByApplication(id);
            var eligibilityAnswers = answerRepository.findByApplicationIdOrderByDisplayOrderAscIdAsc(id);
            var requiredDocs = requiredDocumentRepository.findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(app.getScheme().getId());
            List<Verification> verifications = verificationRepository.findByApplicationOrderByVerifiedAtDesc(app);

            VerificationActionDto actionDto = new VerificationActionDto();
            actionDto.setApplicationId(app.getId());

            model.addAttribute("app", app);
            model.addAttribute("beneficiary", app.getBeneficiary());
            model.addAttribute("scheme", app.getScheme());
            model.addAttribute("applicationDocs", applicationDocs);
            model.addAttribute("eligibilityAnswers", eligibilityAnswers);
            model.addAttribute("requiredDocs", requiredDocs);
            model.addAttribute("verifications", verifications);
            model.addAttribute("actionDto", actionDto);
            return "field-officer/verification-details";
        } catch (Exception e) {
            logger.error("Error loading Field Officer verification for application {}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", "Unable to load verification details for ID: " + id);
            return "redirect:/field-officer/dashboard";
        }
    }

    @GetMapping("/field-officer/history")
    public String fieldOfficerHistory(Authentication authentication, Model model) {
        String email = authentication != null ? authentication.getName() : "";
        List<Verification> history = verificationRepository.findByOfficerUserEmailOrderByIdDesc(email);
        model.addAttribute("history", history);
        return "field-officer/history";
    }

    @GetMapping("/field-officer/reports")
    public String fieldOfficerReports(Authentication authentication, Model model) {
        String email = authentication != null ? authentication.getName() : "";
        List<Verification> history = verificationRepository.findByOfficerUserEmailOrderByIdDesc(email);

        long approvedCount = history.stream().filter(v -> "APPROVE".equalsIgnoreCase(v.getStatus())).count();
        long rejectedCount = history.stream().filter(v -> "REJECT".equalsIgnoreCase(v.getStatus())).count();
        long reverifyCount = history.stream().filter(v -> "REQUEST_RE_VERIFICATION".equalsIgnoreCase(v.getStatus())).count();

        model.addAttribute("totalVerifications", history.size());
        model.addAttribute("approvedCount", approvedCount);
        model.addAttribute("rejectedCount", rejectedCount);
        model.addAttribute("reverifyCount", reverifyCount);
        return "field-officer/reports";
    }

    // ═══════════════════════════════════════════════════════
    //  DISTRICT OFFICER
    // ═══════════════════════════════════════════════════════

    private String resolveDistrictName(Officer officer) {
        if (officer != null && officer.getDistrict() != null && !officer.getDistrict().isBlank()) {
            return officer.getDistrict();
        } else if (officer != null && officer.getState() != null && !officer.getState().isBlank()) {
            return officer.getState() + " District";
        }
        return "District Administration";
    }

    private Map<String, Object> populateDistrictMetrics(String state) {
        List<Application> pendingApps = applicationService.getApplicationsByStage("DISTRICT_REVIEW");
        if (state != null && !state.isBlank()) {
            pendingApps = pendingApps.stream()
                    .filter(a -> a.getBeneficiary() != null && state.equalsIgnoreCase(a.getBeneficiary().getState()))
                    .toList();
        }

        List<ApplicationEscalation> escalations = escalationService.getEscalationsForDistrict(state, "OPEN");
        List<AnomalyFlag> anomalies = anomalyDetectionService.getFlagsForDistrict(state, null).stream()
                .filter(f -> "OPEN".equalsIgnoreCase(f.getStatus()) || "UNDER_REVIEW".equalsIgnoreCase(f.getStatus())).toList();
        List<Grievance> grievances = grievanceService.getGrievancesForDistrict(state, "SUBMITTED");
        Map<String, Object> budgetSummary = districtBudgetService.getDistrictBudgetSummary(state);
        List<Officer> fieldOfficers = officerService.getFieldOfficersByDistrict(state, null);

        Object allocated = budgetSummary != null ? budgetSummary.get("allocatedBudget") : null;
        if (allocated == null) allocated = BigDecimal.ZERO;
        Object approved = budgetSummary != null ? budgetSummary.get("approvedAmount") : null;
        if (approved == null) approved = BigDecimal.ZERO;

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalApplications", applicationService.countTotalApplications());
        metrics.put("pendingCount", pendingApps.size());
        metrics.put("approvedCount", applicationService.countByStatus("APPROVED"));
        metrics.put("escalationCount", escalations.size());
        metrics.put("anomalyCount", anomalies.size());
        metrics.put("openGrievanceCount", grievances.size());
        metrics.put("allocatedBudget", allocated);
        metrics.put("utilizedBudget", approved);
        metrics.put("budgetUtilizationPercent", 0);
        metrics.put("activeFieldOfficers", fieldOfficers.size());
        return metrics;
    }

    @GetMapping({"/district-officer/dashboard", "/district_officer/dashboard"})
    public String districtOfficerDashboard(@RequestParam(value = "priorityLevel", required = false) String priorityLevel,
                                           @RequestParam(value = "schemeId", required = false) Long schemeId,
                                           Authentication authentication, Model model) {
        String email = authentication != null ? authentication.getName() : "";
        Officer officer = officerRepository.findByUserEmail(email).orElse(null);
        String state = officer != null ? officer.getState() : null;
        String districtName = resolveDistrictName(officer);

        List<Application> pendingApps = applicationService.getApplicationsByStage("DISTRICT_REVIEW");
        if (state != null && !state.isBlank()) {
            pendingApps = pendingApps.stream()
                    .filter(a -> a.getBeneficiary() != null && state.equalsIgnoreCase(a.getBeneficiary().getState()))
                    .toList();
        }

        if (priorityLevel != null && !priorityLevel.isBlank()) {
            pendingApps = pendingApps.stream()
                    .filter(a -> priorityLevel.equalsIgnoreCase(a.getPriorityLevel()))
                    .toList();
        }

        if (schemeId != null) {
            pendingApps = pendingApps.stream()
                    .filter(a -> a.getScheme() != null && schemeId.equals(a.getScheme().getId()))
                    .toList();
        }

        pendingApps = priorityService.sortApplicationsByPriority(pendingApps);

        List<Application> initialReviewApps = new java.util.ArrayList<>();
        List<Application> reverificationReviewApps = new java.util.ArrayList<>();
        Map<Long, Map<String, Object>> reverificationDetailsMap = new HashMap<>();

        for (Application app : pendingApps) {
            List<Verification> verifications = verificationRepository.findByApplicationOrderByVerifiedAtDesc(app);
            Verification doReverifyReq = verifications.stream()
                    .filter(v -> ("DISTRICT_OFFICER".equalsIgnoreCase(v.getOfficerRole()) || "ROLE_DISTRICT_OFFICER".equalsIgnoreCase(v.getOfficerRole()))
                            && ("REQUEST_RE_VERIFICATION".equalsIgnoreCase(v.getStatus()) || "RE_VERIFICATION_REQUESTED".equalsIgnoreCase(v.getStatus())))
                    .findFirst()
                    .orElse(null);

            if (doReverifyReq == null) {
                initialReviewApps.add(app);
            } else {
                reverificationReviewApps.add(app);

                Verification foReverifyDone = verifications.stream()
                        .filter(v -> ("FIELD_OFFICER".equalsIgnoreCase(v.getOfficerRole()) || "ROLE_FIELD_OFFICER".equalsIgnoreCase(v.getOfficerRole()))
                                && v.getVerifiedAt() != null && v.getVerifiedAt().isAfter(doReverifyReq.getVerifiedAt()))
                        .findFirst()
                        .orElse(null);

                Map<String, Object> details = new HashMap<>();
                details.put("previousDoRemarks", doReverifyReq.getRemarks() != null ? doReverifyReq.getRemarks() : "Re-verification requested by District Officer");
                details.put("requestedDate", doReverifyReq.getVerifiedAt());
                details.put("completedDate", foReverifyDone != null ? foReverifyDone.getVerifiedAt() : app.getUpdatedAt());
                reverificationDetailsMap.put(app.getId(), details);
            }
        }

        initialReviewApps = priorityService.sortApplicationsByPriority(initialReviewApps);
        reverificationReviewApps = priorityService.sortApplicationsByPriority(reverificationReviewApps);

        Map<Long, com.example.digital_subsidy.dto.PriorityBreakdownDto> breakdownMap = new HashMap<>();
        for (Application app : pendingApps) {
            breakdownMap.put(app.getId(), priorityService.getPriorityBreakdown(app));
        }

        List<ApplicationEscalation> escalations = escalationService.getEscalationsForDistrict(state, "OPEN");
        List<AnomalyFlag> anomalies = anomalyDetectionService.getFlagsForDistrict(state, null).stream()
                .filter(f -> "OPEN".equalsIgnoreCase(f.getStatus()) || "UNDER_REVIEW".equalsIgnoreCase(f.getStatus())).toList();
        List<Grievance> grievances = grievanceService.getGrievancesForDistrict(state, "SUBMITTED");
        Map<String, Object> budgetSummary = districtBudgetService.getDistrictBudgetSummary(state);
        List<Officer> fieldOfficers = officerService.getFieldOfficersByDistrict(state, null);

        Map<String, Object> metrics = populateDistrictMetrics(state);

        List<Milestone> termComplianceReviews = disbursementService.getTermComplianceReviewsForDistrictOfficer(state, officer != null ? officer.getDistrict() : null);

        model.addAttribute("officer", officer);
        model.addAttribute("districtName", districtName);
        model.addAttribute("metrics", metrics);
        model.addAttribute("pendingApplications", pendingApps);
        model.addAttribute("initialApplications", initialReviewApps);
        model.addAttribute("reverificationApplications", reverificationReviewApps);
        model.addAttribute("termComplianceReviews", termComplianceReviews);
        model.addAttribute("reverificationDetailsMap", reverificationDetailsMap);
        model.addAttribute("breakdownMap", breakdownMap);
        model.addAttribute("selectedPriorityLevel", priorityLevel);
        model.addAttribute("selectedSchemeId", schemeId);
        model.addAttribute("assignedOfficers", fieldOfficers);
        model.addAttribute("escalations", escalations);
        model.addAttribute("anomalies", anomalies);
        model.addAttribute("grievances", grievances);
        model.addAttribute("budgetSummary", budgetSummary);
        if (fundUtilizationService != null) {
            model.addAttribute("regionalUtilizationSummary", fundUtilizationService.getSystemFundUtilizationSummary(null, state, districtName));
            model.addAttribute("regionalSchemeUtilizations", fundUtilizationService.getSchemeWiseFundUtilization(null, state));
            model.addAttribute("regionalDistrictUtilizations", fundUtilizationService.getDistrictWiseFundUtilization(state));
        }
        model.addAttribute("actionDto", new VerificationActionDto());
        return "district-officer/dashboard";
    }

    @PostMapping("/officer/applications/{id}/override-priority")
    public String overridePriority(@PathVariable("id") Long id,
                                   @RequestParam("newLevel") String newLevel,
                                   @RequestParam(value = "newScore", required = false) BigDecimal newScore,
                                   @RequestParam("reason") String reason,
                                   Authentication authentication,
                                   RedirectAttributes redirectAttributes) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        String email = authentication.getName();
        String role = authentication.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .findFirst().orElse("");

        try {
            priorityService.manualOverridePriority(id, newLevel, newScore, reason, email, role);
            redirectAttributes.addFlashAttribute("successMessage", "Priority manually overridden successfully for Application #" + id);
        } catch (Exception e) {
            logger.error("Failed manual priority override for app #{}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        if (role.contains("DISTRICT_OFFICER")) {
            return "redirect:/district-officer/dashboard";
        }
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/district-officer/review/{id}")
    public String districtOfficerReviewDetails(@PathVariable("id") Long id,
                                               Authentication authentication,
                                               Model model,
                                               RedirectAttributes redirectAttributes) {
        try {
            Application app = applicationService.getApplicationById(id);
            if (app == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Application not found with ID: " + id);
                return "redirect:/district-officer/dashboard";
            }

            Beneficiary beneficiary = app.getBeneficiary();
            String email = authentication != null ? authentication.getName() : "";
            Officer officer = officerRepository.findByUserEmail(email).orElse(null);
            String districtName = resolveDistrictName(officer);

            // Regional Authorization Check
            if (officer != null && officer.getState() != null && beneficiary != null && beneficiary.getState() != null) {
                if (!officer.getState().equalsIgnoreCase(beneficiary.getState())) {
                    redirectAttributes.addFlashAttribute("errorMessage", "Access Denied: Authorized only for state " + officer.getState());
                    return "redirect:/district-officer/dashboard";
                }
                if (officer.getDistrict() != null && !officer.getDistrict().isBlank() && beneficiary.getDistrict() != null) {
                    if (!officer.getDistrict().equalsIgnoreCase(beneficiary.getDistrict())) {
                        redirectAttributes.addFlashAttribute("errorMessage", "Access Denied: Authorized only for district " + officer.getDistrict());
                        return "redirect:/district-officer/dashboard";
                    }
                }
            }

            // Run anomaly scan to detect any non-blocking warning flags
            anomalyDetectionService.scanAndFlagAnomalies(app);
            List<AnomalyFlag> flags = anomalyDetectionService.getFlagsForApplication(id);

            List<Verification> verifications = verificationRepository.findByApplicationOrderByVerifiedAtDesc(app);
            Verification fieldVerification = (verifications != null) ? verifications.stream()
                    .filter(v -> "FIELD_OFFICER".equalsIgnoreCase(v.getOfficerRole()) || "ROLE_FIELD_OFFICER".equalsIgnoreCase(v.getOfficerRole()))
                    .findFirst().orElse(null) : null;

            List<Document> applicationDocs = documentService.getLatestActiveDocumentsByApplication(id);
            var eligibilityAnswers = answerRepository.findByApplicationIdOrderByDisplayOrderAscIdAsc(id);
            List<Officer> fieldOfficers = officerService.getFieldOfficersByDistrict(officer != null ? officer.getState() : null, officer != null ? officer.getDistrict() : null);

            VerificationActionDto actionDto = new VerificationActionDto();
            actionDto.setApplicationId(app.getId());

            model.addAttribute("officer", officer);
            model.addAttribute("districtName", districtName);
            model.addAttribute("app", app);
            model.addAttribute("beneficiary", beneficiary);
            model.addAttribute("scheme", app.getScheme());
            model.addAttribute("fieldVerification", fieldVerification);
            model.addAttribute("applicationDocs", applicationDocs);
            model.addAttribute("eligibilityAnswers", eligibilityAnswers);
            model.addAttribute("anomalyFlags", flags);
            model.addAttribute("fieldOfficers", fieldOfficers);
            model.addAttribute("actionDto", actionDto);

            return "district-officer/review-details";
        } catch (Exception e) {
            logger.error("Error loading District Officer review for application {}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage", "Unable to load review details for ID: " + id);
            return "redirect:/district-officer/dashboard";
        }
    }

    @GetMapping("/district-officer/escalations")
    public String districtOfficerEscalations(@RequestParam(value = "filter", required = false, defaultValue = "ALL") String filter,
                                            Authentication authentication,
                                            Model model) {
        String email = authentication != null ? authentication.getName() : "";
        Officer officer = officerRepository.findByUserEmail(email).orElse(null);
        String state = officer != null ? officer.getState() : null;
        String districtName = resolveDistrictName(officer);

        List<ApplicationEscalation> escalations = escalationService.getEscalationsForDistrict(state, filter);
        model.addAttribute("officer", officer);
        model.addAttribute("districtName", districtName);
        model.addAttribute("metrics", populateDistrictMetrics(state));
        model.addAttribute("escalations", escalations);
        model.addAttribute("filter", filter);
        return "district-officer/escalations";
    }

    @PostMapping({"/district-officer/escalations/{id}/resolve", "/district-officer/escalation/resolve"})
    public String resolveEscalation(@PathVariable(value = "id", required = false) Long pathId,
                                   @RequestParam(value = "escalationId", required = false) Long paramId,
                                   @RequestParam(value = "remarks", required = false, defaultValue = "Resolved by District Officer") String remarks,
                                   Authentication authentication,
                                   RedirectAttributes redirectAttributes) {
        Long id = pathId != null ? pathId : paramId;
        String email = authentication != null ? authentication.getName() : "";
        try {
            if (id != null) {
                escalationService.resolveEscalation(id, remarks, email);
                redirectAttributes.addFlashAttribute("successMessage", "Escalation #" + id + " marked as RESOLVED.");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/district-officer/escalations";
    }

    @GetMapping("/district-officer/anomalies")
    public String districtOfficerAnomalies(Authentication authentication, Model model) {
        String email = authentication != null ? authentication.getName() : "";
        Officer officer = officerRepository.findByUserEmail(email).orElse(null);
        String state = officer != null ? officer.getState() : null;
        String districtName = resolveDistrictName(officer);

        List<AnomalyFlag> flags = anomalyDetectionService.getFlagsForDistrict(state, null);
        model.addAttribute("officer", officer);
        model.addAttribute("districtName", districtName);
        model.addAttribute("metrics", populateDistrictMetrics(state));
        model.addAttribute("anomalies", flags);
        return "district-officer/anomalies";
    }

    @PostMapping({"/district-officer/anomalies/{id}/resolve", "/district-officer/anomaly/resolve"})
    public String resolveAnomaly(@PathVariable(value = "id", required = false) Long pathId,
                                 @RequestParam(value = "anomalyId", required = false) Long paramId,
                                 @RequestParam(value = "status", required = false, defaultValue = "RESOLVED") String status,
                                 @RequestParam(value = "remarks", required = false, defaultValue = "Reviewed by District Officer") String remarks,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        Long id = pathId != null ? pathId : paramId;
        String email = authentication != null ? authentication.getName() : "";
        try {
            if (id != null) {
                anomalyDetectionService.resolveFlag(id, status, remarks, email);
                redirectAttributes.addFlashAttribute("successMessage", "Anomaly flag #" + id + " updated to " + status + ".");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/district-officer/anomalies";
    }

    @GetMapping("/district-officer/budget")
    public String districtOfficerBudget(Authentication authentication, Model model) {
        String email = authentication != null ? authentication.getName() : "";
        Officer officer = officerRepository.findByUserEmail(email).orElse(null);
        String state = officer != null ? officer.getState() : null;
        String districtName = resolveDistrictName(officer);

        Map<String, Object> budgetSummary = districtBudgetService.getDistrictBudgetSummary(state);
        model.addAttribute("officer", officer);
        model.addAttribute("districtName", districtName);
        model.addAttribute("metrics", populateDistrictMetrics(state));
        model.addAttribute("budgetSummary", budgetSummary);
        model.addAttribute("budget", budgetSummary);
        return "district-officer/budget";
    }



    @GetMapping("/district-officer/officers")
    public String districtOfficerOfficers(Authentication authentication, Model model) {
        String email = authentication != null ? authentication.getName() : "";
        Officer officer = officerRepository.findByUserEmail(email).orElse(null);
        String state = officer != null ? officer.getState() : null;
        String districtName = resolveDistrictName(officer);

        List<Officer> fieldOfficers = officerService.getFieldOfficersByDistrict(state, null);
        List<Application> pendingApps = applicationService.getApplicationsByStage("DISTRICT_REVIEW");

        model.addAttribute("officer", officer);
        model.addAttribute("districtName", districtName);
        model.addAttribute("metrics", populateDistrictMetrics(state));
        model.addAttribute("officers", fieldOfficers);
        model.addAttribute("fieldOfficers", fieldOfficers);
        model.addAttribute("pendingApplications", pendingApps);
        return "district-officer/officers";
    }

    @PostMapping("/district-officer/reassign")
    public String reassignApplication(@RequestParam("applicationId") Long applicationId,
                                      @RequestParam(value = "officerId", required = false) Long officerId,
                                      Authentication authentication,
                                      RedirectAttributes redirectAttributes) {
        String email = authentication != null ? authentication.getName() : "";
        try {
            Application app = applicationService.getApplicationById(applicationId);
            Officer officer = officerId != null ? officerRepository.findById(officerId).orElse(null) : null;
            officerService.assignApplicationToOfficer(app, officer, email);
            redirectAttributes.addFlashAttribute("successMessage", "Application #" + applicationId + " reassigned successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/district-officer/officers";
    }

    @GetMapping("/district-officer/reports")
    public String districtOfficerReports(Authentication authentication, Model model) {
        String email = authentication != null ? authentication.getName() : "";
        Officer officer = officerRepository.findByUserEmail(email).orElse(null);
        String state = officer != null ? officer.getState() : null;
        String districtName = resolveDistrictName(officer);

        Map<String, Object> budgetSummary = districtBudgetService.getDistrictBudgetSummary(state);
        Map<String, Object> metrics = populateDistrictMetrics(state);

        model.addAttribute("officer", officer);
        model.addAttribute("districtName", districtName);
        model.addAttribute("budgetSummary", budgetSummary);
        model.addAttribute("metrics", metrics);
        return "district-officer/reports";
    }

    @GetMapping("/district-officer/audit")
    public String districtOfficerAudit(Authentication authentication, Model model) {
        String email = authentication != null ? authentication.getName() : "";
        Officer officer = officerRepository.findByUserEmail(email).orElse(null);
        String state = officer != null ? officer.getState() : null;
        String districtName = resolveDistrictName(officer);

        model.addAttribute("officer", officer);
        model.addAttribute("districtName", districtName);
        model.addAttribute("metrics", populateDistrictMetrics(state));
        model.addAttribute("audits", applicationService.getApplicationsByStage("DISTRICT_REVIEW"));
        model.addAttribute("auditLogs", auditLogRepository.findAll());
        return "district-officer/audit";
    }

    // ═══════════════════════════════════════════════════════
    //  SHARED VERIFICATION ACTION
    // ═══════════════════════════════════════════════════════

    @PostMapping("/district-officer/review/{id}")
    public String processDistrictOfficerReviewPost(@PathVariable("id") Long id,
                                                   @Valid @ModelAttribute("actionDto") VerificationActionDto actionDto,
                                                   @RequestParam(value = "action", required = false) String actionParam,
                                                   Authentication authentication,
                                                   RedirectAttributes redirectAttributes) {
        if (actionDto == null) {
            actionDto = new VerificationActionDto();
        }
        actionDto.setApplicationId(id);
        if ((actionDto.getAction() == null || actionDto.getAction().isBlank()) && actionParam != null && !actionParam.isBlank()) {
            actionDto.setAction(actionParam);
        }
        return processVerification(actionDto, "DISTRICT_OFFICER", actionParam, authentication, redirectAttributes);
    }

    @PostMapping("/officer/verify")
    public String processVerification(@Valid @ModelAttribute("actionDto") VerificationActionDto actionDto,
                                      @RequestParam(value = "role", required = false, defaultValue = "DISTRICT_OFFICER") String role,
                                      @RequestParam(value = "action", required = false) String actionParam,
                                      Authentication authentication,
                                      RedirectAttributes redirectAttributes) {
        if (actionDto == null) {
            actionDto = new VerificationActionDto();
        }
        if ((actionDto.getAction() == null || actionDto.getAction().isBlank()) && actionParam != null && !actionParam.isBlank()) {
            actionDto.setAction(actionParam);
        }

        if ("DISTRICT_OFFICER".equalsIgnoreCase(role) || "ROLE_DISTRICT_OFFICER".equalsIgnoreCase(role)) {
            boolean isDistrictOfficer = authentication != null && authentication.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_DISTRICT_OFFICER".equalsIgnoreCase(a.getAuthority())
                            || "DISTRICT_OFFICER".equalsIgnoreCase(a.getAuthority())
                            || "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority()));
            if (!isDistrictOfficer) {
                redirectAttributes.addFlashAttribute("errorMessage", "Access Denied: Only District Officers are authorized to perform this review action.");
                if (actionDto.getApplicationId() != null) {
                    return "redirect:/district-officer/review/" + actionDto.getApplicationId();
                }
                return "redirect:/district-officer/dashboard";
            }
        }

        String officerEmail = authentication != null ? authentication.getName() : "";
        try {
            Verification savedVer = verificationService.processVerification(officerEmail, role, actionDto);
            String appRef = (savedVer != null && savedVer.getApplication() != null && savedVer.getApplication().getApplicationNumber() != null)
                    ? savedVer.getApplication().getApplicationNumber()
                    : (actionDto.getApplicationId() != null ? "#" + actionDto.getApplicationId() : "");

            if ("FIELD_OFFICER".equalsIgnoreCase(role)) {
                redirectAttributes.addFlashAttribute("successMessage", "Field verification for Application " + appRef + " completed successfully.");
                return "redirect:/field-officer/dashboard?verified=true";
            } else if ("DISTRICT_OFFICER".equalsIgnoreCase(role)) {
                if ("APPROVE".equalsIgnoreCase(actionDto.getAction())) {
                    redirectAttributes.addFlashAttribute("successMessage", "Application " + appRef + " sanctioned and forwarded for Finance Treasury approval.");
                } else if ("REJECT".equalsIgnoreCase(actionDto.getAction())) {
                    redirectAttributes.addFlashAttribute("successMessage", "Application " + appRef + " has been final rejected.");
                } else {
                    redirectAttributes.addFlashAttribute("successMessage", "Application sent to Field Officer for re-verification successfully.");
                }
                return "redirect:/district-officer/dashboard?verified=true";
            } else {
                redirectAttributes.addFlashAttribute("successMessage", "Finance Treasury approval completed for Application " + appRef + ".");
                return "redirect:/finance/dashboard?verified=true";
            }
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            if ("FIELD_OFFICER".equalsIgnoreCase(role) && actionDto.getApplicationId() != null) {
                return "redirect:/field-officer/verification/" + actionDto.getApplicationId();
            } else if ("DISTRICT_OFFICER".equalsIgnoreCase(role) && actionDto.getApplicationId() != null) {
                return "redirect:/district-officer/review/" + actionDto.getApplicationId();
            }
            return "redirect:/district-officer/dashboard";
        }
    }
}
