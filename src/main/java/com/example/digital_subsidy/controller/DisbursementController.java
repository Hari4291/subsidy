package com.example.digital_subsidy.controller;

import com.example.digital_subsidy.dto.DisbursementPlanDto;
import com.example.digital_subsidy.dto.StageConfigDto;
import com.example.digital_subsidy.dto.StageReleaseDto;
import com.example.digital_subsidy.dto.VerificationActionDto;
import com.example.digital_subsidy.model.Application;
import com.example.digital_subsidy.model.Beneficiary;
import com.example.digital_subsidy.model.Disbursement;
import com.example.digital_subsidy.model.DisbursementRelease;
import com.example.digital_subsidy.model.Milestone;
import com.example.digital_subsidy.service.ApplicationService;
import com.example.digital_subsidy.service.BeneficiaryService;
import com.example.digital_subsidy.service.DisbursementService;
import com.example.digital_subsidy.service.VerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class DisbursementController {

    private static final Logger log = LoggerFactory.getLogger(DisbursementController.class);

    private final ApplicationService applicationService;
    private final VerificationService verificationService;
    private final DisbursementService disbursementService;
    private final BeneficiaryService beneficiaryService;
    private final com.example.digital_subsidy.service.FundUtilizationService fundUtilizationService;
    private final com.example.digital_subsidy.repository.MilestoneRepository milestoneRepository;
    private final com.example.digital_subsidy.service.FundTrackingService fundTrackingService;
    private final com.example.digital_subsidy.service.SchemeService schemeService;

    public DisbursementController(ApplicationService applicationService,
                                  VerificationService verificationService,
                                  DisbursementService disbursementService,
                                  BeneficiaryService beneficiaryService,
                                  com.example.digital_subsidy.service.FundUtilizationService fundUtilizationService,
                                  com.example.digital_subsidy.repository.MilestoneRepository milestoneRepository,
                                  com.example.digital_subsidy.service.FundTrackingService fundTrackingService,
                                  com.example.digital_subsidy.service.SchemeService schemeService) {
        this.applicationService = applicationService;
        this.verificationService = verificationService;
        this.disbursementService = disbursementService;
        this.beneficiaryService = beneficiaryService;
        this.fundUtilizationService = fundUtilizationService;
        this.milestoneRepository = milestoneRepository;
        this.fundTrackingService = fundTrackingService;
        this.schemeService = schemeService;
    }

    @GetMapping("/finance/dashboard")
    public String financeDashboard(Model model) {
        List<Application> pendingFinanceApps = applicationService.getApplicationsByStage("FINANCE_APPROVAL");
        List<Application> approvedApps = disbursementService.getUnreleasedActiveDisbursementApplications();
        List<Disbursement> allDisbursements = disbursementService.getAllDisbursementsWithDetails();

        Map<String, Object> metrics = disbursementService.getFinanceDashboardMetrics();
        var partiallyReleasedApps = disbursementService.getPartiallyReleasedApplications();

        var utilizationSummary = fundUtilizationService.getSystemFundUtilizationSummary(null, null, null);
        var schemeUtilizations = fundUtilizationService.getSchemeWiseFundUtilization(null, null);
        var regionalUtilizations = fundUtilizationService.getDistrictWiseFundUtilization(null);

        var verifiedTermsPendingRelease = disbursementService.getVerifiedTermsPendingRelease();

        // Financial Fund Tracking Module attributes
        var fundTrackingSummary = fundTrackingService.getFundTrackingSummary(null, null, "ALL", null);
        var beneficiaryFundTrackings = fundTrackingService.getBeneficiaryFundTrackingList(null, null, "ALL", null);
        var termLevelSummaries = fundTrackingService.getTermLevelSummaries(null);
        var allSchemes = schemeService.getAllSchemes();

        model.addAttribute("pendingApplications", pendingFinanceApps);
        model.addAttribute("approvedApplications", approvedApps);
        model.addAttribute("partiallyReleasedApplications", partiallyReleasedApps);
        model.addAttribute("verifiedTermsPendingRelease", verifiedTermsPendingRelease);
        model.addAttribute("allDisbursements", allDisbursements);
        model.addAttribute("metrics", metrics);
        model.addAttribute("totalDisbursed", disbursementService.getTotalDisbursedAmount());
        model.addAttribute("utilizationSummary", utilizationSummary);
        model.addAttribute("schemeUtilizations", schemeUtilizations);
        model.addAttribute("regionalUtilizations", regionalUtilizations);
        model.addAttribute("actionDto", new VerificationActionDto());

        // Fund Tracking Module Attributes
        model.addAttribute("fundTrackingSummary", fundTrackingSummary);
        model.addAttribute("beneficiaryFundTrackings", beneficiaryFundTrackings);
        model.addAttribute("termLevelSummaries", termLevelSummaries);
        model.addAttribute("allSchemes", allSchemes);
        return "finance/dashboard";
    }

    @GetMapping("/finance/disbursements")
    public String viewAllDisbursements(Model model) {
        List<Disbursement> disbursements = disbursementService.getAllDisbursementsWithDetails();
        Map<String, Object> metrics = disbursementService.getFinanceDashboardMetrics();
        var partiallyReleasedApps = disbursementService.getPartiallyReleasedApplications();

        model.addAttribute("disbursements", disbursements);
        model.addAttribute("partiallyReleasedApplications", partiallyReleasedApps);
        model.addAttribute("metrics", metrics);
        return "finance/dashboard"; // or rendered inside tabbed view
    }

    @GetMapping({"/beneficiary/disbursements/upload-compliance", "/disbursements/upload-compliance"})
    public String uploadComplianceRedirect(@RequestParam("applicationId") Long applicationId,
                                          @RequestParam(value = "milestoneId", required = false) Long milestoneId) {
        return "redirect:/disbursements/schedule/" + applicationId + (milestoneId != null ? "?milestoneId=" + milestoneId : "");
    }

    @GetMapping("/disbursements/schedule/{applicationId}")
    public String viewMilestoneSchedule(@PathVariable("applicationId") Long applicationId,
                                        Authentication authentication,
                                        Model model,
                                        RedirectAttributes redirectAttributes) {
        String username = (authentication != null) ? authentication.getName() : "ANONYMOUS";
        Set<String> authorities = (authentication != null) ? authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet()) : Set.of();

        log.info("Accessing milestone schedule for applicationId: {}, username: {}, resolved authorities: {}",
                applicationId, username, authorities);

        Application application;
        try {
            application = applicationService.getApplicationById(applicationId);
        } catch (Exception e) {
            log.warn("Application not found for schedule view with ID {}: {}", applicationId, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Application not found with ID: " + applicationId);
            return "redirect:/beneficiary/dashboard";
        }

        boolean isFinance = authorities.contains("ROLE_FINANCE_APPROVER") || authorities.contains("FINANCE_APPROVER");
        boolean isAdmin = authorities.contains("ROLE_ADMIN") || authorities.contains("ADMIN");
        boolean isBeneficiary = authorities.contains("ROLE_BENEFICIARY") || authorities.contains("BENEFICIARY");
        boolean isFieldOfficer = authorities.contains("ROLE_FIELD_OFFICER") || authorities.contains("FIELD_OFFICER");
        boolean isDistrictOfficer = authorities.contains("ROLE_DISTRICT_OFFICER") || authorities.contains("DISTRICT_OFFICER");

        if (isBeneficiary && !isFinance && !isAdmin && !isFieldOfficer && !isDistrictOfficer) {
            Beneficiary beneficiary = beneficiaryService.getBeneficiaryByEmail(username).orElse(null);
            if (beneficiary == null || application.getBeneficiary() == null || !application.getBeneficiary().getId().equals(beneficiary.getId())) {
                log.warn("Unauthorized milestone schedule access attempt by beneficiary user: {} for applicationId: {}",
                        username, applicationId);
                redirectAttributes.addFlashAttribute("errorMessage", "Unauthorized access to application milestone schedule.");
                return "redirect:/beneficiary/dashboard";
            }
        } else if (!isFinance && !isAdmin && !isBeneficiary && !isFieldOfficer && !isDistrictOfficer) {
            log.warn("Unauthorized milestone schedule access attempt by user: {} with authorities: {} for applicationId: {}",
                    username, authorities, applicationId);
            redirectAttributes.addFlashAttribute("errorMessage", "Unauthorized access to milestone schedule.");
            return "redirect:/beneficiary/dashboard";
        }

        Disbursement disbursement = disbursementService.getDisbursementByApplicationId(applicationId);

        BigDecimal totalSanctioned = (disbursement != null && disbursement.getTotalSanctionedAmount() != null)
                ? disbursement.getTotalSanctionedAmount()
                : (application.getSanctionedAmount() != null ? application.getSanctionedAmount() : BigDecimal.ZERO);

        BigDecimal totalDisbursed = (disbursement != null && disbursement.getDisbursedAmount() != null)
                ? disbursement.getDisbursedAmount()
                : BigDecimal.ZERO;

        BigDecimal remainingAmount = totalSanctioned.subtract(totalDisbursed);
        if (remainingAmount.compareTo(BigDecimal.ZERO) < 0) remainingAmount = BigDecimal.ZERO;

        List<Milestone> milestones = (disbursement != null && disbursement.getMilestones() != null)
                ? new ArrayList<>(disbursement.getMilestones())
                : java.util.Collections.emptyList();

        List<DisbursementRelease> releaseHistory = disbursementService.getReleaseHistoryForApplication(applicationId);

        model.addAttribute("application", application);
        model.addAttribute("disbursement", disbursement);
        model.addAttribute("milestones", milestones);
        model.addAttribute("releaseHistory", releaseHistory);
        model.addAttribute("totalSanctioned", totalSanctioned);
        model.addAttribute("totalDisbursed", totalDisbursed);
        model.addAttribute("remainingAmount", remainingAmount);
        model.addAttribute("isFinanceApprover", isFinance);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("isBeneficiary", isBeneficiary);

        return "finance/milestone-schedule";
    }

    @PostMapping("/finance/approve")
    public String approveFinance(@RequestParam("applicationId") Long applicationId,
                                 @RequestParam("action") String action,
                                 @RequestParam(value = "remarks", required = false) String remarks,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        VerificationActionDto actionDto = new VerificationActionDto();
        actionDto.setApplicationId(applicationId);
        actionDto.setAction(action);
        actionDto.setRemarks(remarks != null ? remarks : "Finance Approval Sanctioned");

        try {
            verificationService.processVerification(authentication.getName(), "FINANCE_APPROVER", actionDto);
            redirectAttributes.addFlashAttribute("successMessage", "Finance authorization completed successfully.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Finance approval failed: " + ex.getMessage());
        }

        return "redirect:/finance/dashboard?sanctioned=true";
    }

    @PostMapping("/finance/configure-plan")
    public String configureDisbursementPlan(@RequestParam("applicationId") Long applicationId,
                                            @RequestParam("calculationType") String calculationType,
                                            @RequestParam("stageNames") List<String> stageNames,
                                            @RequestParam(value = "percentages", required = false) List<Integer> percentages,
                                            @RequestParam(value = "amounts", required = false) List<BigDecimal> amounts,
                                            @RequestParam(value = "dueDates", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) List<LocalDate> dueDates,
                                            @RequestParam(value = "descriptions", required = false) List<String> descriptions,
                                            @RequestParam(value = "complianceConditions", required = false) List<String> complianceConditions,
                                            Authentication authentication,
                                            RedirectAttributes redirectAttributes) {
        try {
            DisbursementPlanDto planDto = new DisbursementPlanDto();
            planDto.setApplicationId(applicationId);
            planDto.setCalculationType(calculationType);

            List<StageConfigDto> stages = new ArrayList<>();
            for (int i = 0; i < stageNames.size(); i++) {
                StageConfigDto stage = new StageConfigDto();
                stage.setStageNumber(i + 1);
                stage.setStageName(stageNames.get(i));
                if (percentages != null && i < percentages.size()) stage.setPercentage(percentages.get(i));
                if (amounts != null && i < amounts.size()) stage.setAmount(amounts.get(i));
                if (dueDates != null && i < dueDates.size()) stage.setDueDate(dueDates.get(i));
                if (descriptions != null && i < descriptions.size()) stage.setDescription(descriptions.get(i));
                if (complianceConditions != null && i < complianceConditions.size()) stage.setComplianceCondition(complianceConditions.get(i));
                stages.add(stage);
            }
            planDto.setStages(stages);

            disbursementService.createOrUpdateCustomPlan(planDto, null);
            redirectAttributes.addFlashAttribute("successMessage", "Disbursement plan configured successfully.");
        } catch (Exception ex) {
            log.error("Failed to configure disbursement plan for app ID: {}", applicationId, ex);
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to save plan: " + ex.getMessage());
        }

        return "redirect:/disbursements/schedule/" + applicationId;
    }

    @PostMapping("/finance/release-milestone")
    public String releaseMilestone(@RequestParam("milestoneId") Long milestoneId,
                                  @RequestParam(value = "applicationId", required = false) Long applicationId,
                                  @RequestParam(value = "releaseAmount", required = false) BigDecimal releaseAmount,
                                  @RequestParam(value = "transactionReference", required = false) String transactionReference,
                                  @RequestParam(value = "paymentMethod", required = false, defaultValue = "DBT_BANK_TRANSFER") String paymentMethod,
                                  @RequestParam(value = "remarks", required = false) String remarks,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        try {
            StageReleaseDto releaseDto = new StageReleaseDto();
            releaseDto.setMilestoneId(milestoneId);
            releaseDto.setApplicationId(applicationId);
            releaseDto.setReleaseAmount(releaseAmount);
            releaseDto.setTransactionReference(transactionReference);
            releaseDto.setPaymentMethod(paymentMethod);
            releaseDto.setRemarks(remarks);

            String username = (authentication != null) ? authentication.getName() : "FINANCE_OFFICER";
            disbursementService.releaseMilestone(releaseDto, username);

            redirectAttributes.addFlashAttribute("successMessage", "Grant stage installment released successfully into beneficiary account.");
        } catch (Exception ex) {
            log.error("Failed to release milestone ID: {}", milestoneId, ex);
            redirectAttributes.addFlashAttribute("errorMessage", "Release failed: " + ex.getMessage());
        }

        if (applicationId != null) {
            return "redirect:/disbursements/schedule/" + applicationId + "?released=true";
        }
        return "redirect:/finance/dashboard?released=true";
    }

    @PostMapping("/finance/hold-stage")
    public String holdOrResumeStage(@RequestParam("milestoneId") Long milestoneId,
                                    @RequestParam("applicationId") Long applicationId,
                                    @RequestParam("action") String action, // HOLD or UNHOLD
                                    @RequestParam(value = "remarks", required = false) String remarks,
                                    Authentication authentication,
                                    RedirectAttributes redirectAttributes) {
        try {
            String username = (authentication != null) ? authentication.getName() : "FINANCE_OFFICER";
            if ("HOLD".equalsIgnoreCase(action)) {
                disbursementService.holdMilestone(milestoneId, remarks, username);
                redirectAttributes.addFlashAttribute("successMessage", "Stage placed ON HOLD successfully.");
            } else {
                disbursementService.unholdMilestone(milestoneId, username);
                redirectAttributes.addFlashAttribute("successMessage", "Stage status resumed to PENDING.");
            }
        } catch (Exception ex) {
            log.error("Failed to update stage hold status for milestone ID: {}", milestoneId, ex);
            redirectAttributes.addFlashAttribute("errorMessage", "Action failed: " + ex.getMessage());
        }

        return "redirect:/disbursements/schedule/" + applicationId;
    }

    @PostMapping("/compliance/submit")
    public String submitComplianceProof(@RequestParam(value = "milestoneId", required = false) Long milestoneId,
                                        @RequestParam(value = "applicationId", required = false) Long applicationId,
                                        @RequestParam(value = "file", required = false) org.springframework.web.multipart.MultipartFile file,
                                        @RequestParam(value = "beneficiaryRemarks", required = false) String beneficiaryRemarks,
                                        Authentication authentication,
                                        RedirectAttributes redirectAttributes) {
        try {
            if (milestoneId == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Milestone ID is missing.");
                return "redirect:/beneficiary/dashboard";
            }
            if (applicationId == null) {
                Milestone milestone = milestoneRepository.findById(milestoneId).orElse(null);
                if (milestone != null && milestone.getDisbursement() != null && milestone.getDisbursement().getApplication() != null) {
                    applicationId = milestone.getDisbursement().getApplication().getId();
                } else {
                    redirectAttributes.addFlashAttribute("errorMessage", "Application ID is missing.");
                    return "redirect:/beneficiary/dashboard";
                }
            }
            if (file == null || file.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Uploaded file is empty or missing.");
                return "redirect:/disbursements/schedule/" + applicationId;
            }

            String username = (authentication != null) ? authentication.getName() : "ANONYMOUS";
            disbursementService.submitComplianceProof(milestoneId, applicationId, file, beneficiaryRemarks, username);
            redirectAttributes.addFlashAttribute("successMessage", "Term document submitted successfully for verification.");
            return "redirect:/disbursements/schedule/" + applicationId;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            log.warn("Validation error submitting compliance proof for milestone ID {}: {}", milestoneId, ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/disbursements/schedule/" + (applicationId != null ? applicationId : "");
        } catch (org.springframework.security.access.AccessDeniedException ex) {
            log.warn("Access denied submitting compliance proof for milestone ID {}: {}", milestoneId, ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Access Denied: " + ex.getMessage());
            return "redirect:/beneficiary/dashboard";
        } catch (Exception ex) {
            log.error("Failed to submit compliance proof for milestone ID: {}", milestoneId, ex);
            redirectAttributes.addFlashAttribute("errorMessage", "Upload failed: " + ex.getMessage());
            return "redirect:/disbursements/schedule/" + (applicationId != null ? applicationId : "");
        }
    }

    @PostMapping("/compliance/verify")
    public String verifyCompliance(@RequestParam("milestoneId") Long milestoneId,
                                   @RequestParam("applicationId") Long applicationId,
                                   @RequestParam("action") String action,
                                   @RequestParam(value = "remarks", required = false) String remarks,
                                   Authentication authentication,
                                   RedirectAttributes redirectAttributes) {
        try {
            String officerEmail = (authentication != null) ? authentication.getName() : "OFFICER";
            disbursementService.verifyCompliance(milestoneId, action, remarks, officerEmail);
            if ("VERIFY".equalsIgnoreCase(action) || "VERIFIED".equalsIgnoreCase(action)) {
                redirectAttributes.addFlashAttribute("successMessage", "Compliance verified successfully.");
            } else {
                redirectAttributes.addFlashAttribute("successMessage", "Compliance proof rejected and re-upload requested.");
            }
        } catch (Exception ex) {
            log.error("Failed to verify compliance for milestone ID: {}", milestoneId, ex);
            redirectAttributes.addFlashAttribute("errorMessage", "Verification action failed: " + ex.getMessage());
        }
        return "redirect:/disbursements/schedule/" + applicationId;
    }
}
