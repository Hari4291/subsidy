package com.example.digital_subsidy.controller;

import com.example.digital_subsidy.dto.VerificationActionDto;
import com.example.digital_subsidy.model.Application;
import com.example.digital_subsidy.model.Beneficiary;
import com.example.digital_subsidy.model.Disbursement;
import com.example.digital_subsidy.service.ApplicationService;
import com.example.digital_subsidy.service.BeneficiaryService;
import com.example.digital_subsidy.service.DisbursementService;
import com.example.digital_subsidy.service.VerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class DisbursementController {

    private static final Logger log = LoggerFactory.getLogger(DisbursementController.class);

    private final ApplicationService applicationService;
    private final VerificationService verificationService;
    private final DisbursementService disbursementService;
    private final BeneficiaryService beneficiaryService;

    public DisbursementController(ApplicationService applicationService,
                                  VerificationService verificationService,
                                  DisbursementService disbursementService,
                                  BeneficiaryService beneficiaryService) {
        this.applicationService = applicationService;
        this.verificationService = verificationService;
        this.disbursementService = disbursementService;
        this.beneficiaryService = beneficiaryService;
    }

    @GetMapping("/finance/dashboard")
    public String financeDashboard(Model model) {
        List<Application> pendingFinanceApps = applicationService.getApplicationsByStage("FINANCE_APPROVAL");
        List<Application> approvedApps = applicationService.getApplicationsByStage("DISBURSEMENT");

        model.addAttribute("pendingApplications", pendingFinanceApps);
        model.addAttribute("approvedApplications", approvedApps);
        model.addAttribute("totalDisbursed", disbursementService.getTotalDisbursedAmount());
        model.addAttribute("actionDto", new VerificationActionDto());
        return "finance/dashboard";
    }

    @GetMapping("/disbursements/schedule/{applicationId}")
    public String viewMilestoneSchedule(@PathVariable("applicationId") Long applicationId,
                                        Authentication authentication,
                                        Model model) {
        String username = (authentication != null) ? authentication.getName() : "ANONYMOUS";
        Set<String> authorities = (authentication != null) ? authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet()) : Set.of();

        log.info("Accessing milestone schedule for applicationId: {}, username: {}, resolved authorities: {}",
                applicationId, username, authorities);

        // Fetch application (throws ResourceNotFoundException -> 404 error page if app ID is invalid)
        Application application = applicationService.getApplicationById(applicationId);

        boolean isFinance = authorities.contains("ROLE_FINANCE_APPROVER");
        boolean isAdmin = authorities.contains("ROLE_ADMIN");
        boolean isBeneficiary = authorities.contains("ROLE_BENEFICIARY");

        if (isBeneficiary && !isFinance && !isAdmin) {
            Beneficiary beneficiary = beneficiaryService.getBeneficiaryByEmail(username)
                    .orElseThrow(() -> new AccessDeniedException("Beneficiary profile not found"));
            if (!application.getBeneficiary().getId().equals(beneficiary.getId())) {
                log.warn("Unauthorized milestone schedule access attempt by beneficiary user: {} for applicationId: {}",
                        username, applicationId);
                throw new AccessDeniedException("Unauthorized access to application milestone schedule");
            }
        } else if (!isFinance && !isAdmin) {
            log.warn("Unauthorized milestone schedule access attempt by user: {} with authorities: {} for applicationId: {}",
                    username, authorities, applicationId);
            throw new AccessDeniedException("Unauthorized access to milestone schedule");
        }

        Disbursement disbursement = disbursementService.getDisbursementByApplicationId(applicationId);

        java.math.BigDecimal totalSanctioned = (disbursement != null && disbursement.getTotalSanctionedAmount() != null)
                ? disbursement.getTotalSanctionedAmount()
                : (application.getRequestedAmount() != null ? application.getRequestedAmount() : java.math.BigDecimal.ZERO);

        java.math.BigDecimal totalDisbursed = (disbursement != null && disbursement.getDisbursedAmount() != null)
                ? disbursement.getDisbursedAmount()
                : java.math.BigDecimal.ZERO;

        java.math.BigDecimal remainingAmount = totalSanctioned.subtract(totalDisbursed);

        List<com.example.digital_subsidy.model.Milestone> milestones = (disbursement != null && disbursement.getMilestones() != null)
                ? new java.util.ArrayList<>(disbursement.getMilestones())
                : java.util.Collections.emptyList();

        model.addAttribute("application", application);
        model.addAttribute("disbursement", disbursement);
        model.addAttribute("milestones", milestones);
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
                                 Authentication authentication) {
        VerificationActionDto actionDto = new VerificationActionDto();
        actionDto.setApplicationId(applicationId);
        actionDto.setAction(action);
        actionDto.setRemarks(remarks != null ? remarks : "Finance Approval Sanctioned");

        verificationService.processVerification(authentication.getName(), "FINANCE_APPROVER", actionDto);
        return "redirect:/finance/dashboard?sanctioned=true";
    }

    @PostMapping("/finance/release-milestone")
    public String releaseMilestone(@RequestParam("milestoneId") Long milestoneId,
                                  @RequestParam(value = "applicationId", required = false) Long applicationId) {
        disbursementService.releaseMilestone(milestoneId);
        if (applicationId != null) {
            return "redirect:/disbursements/schedule/" + applicationId + "?released=true";
        }
        return "redirect:/finance/dashboard?released=true";
    }
}
