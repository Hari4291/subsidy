package com.example.digital_subsidy.controller;

import com.example.digital_subsidy.model.Application;
import com.example.digital_subsidy.model.Beneficiary;
import com.example.digital_subsidy.model.Disbursement;
import com.example.digital_subsidy.model.EligibilityScore;
import com.example.digital_subsidy.model.Verification;
import com.example.digital_subsidy.repository.EligibilityScoreRepository;
import com.example.digital_subsidy.repository.VerificationRepository;
import com.example.digital_subsidy.service.ApplicationService;
import com.example.digital_subsidy.service.BeneficiaryService;
import com.example.digital_subsidy.service.DisbursementService;
import com.example.digital_subsidy.service.SchemeService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;

import com.example.digital_subsidy.service.DocumentService;

@Controller
@RequestMapping("/beneficiary")
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;
    private final ApplicationService applicationService;
    private final SchemeService schemeService;
    private final DisbursementService disbursementService;
    private final VerificationRepository verificationRepository;
    private final EligibilityScoreRepository eligibilityScoreRepository;
    private final DocumentService documentService;
    private final com.example.digital_subsidy.repository.ApplicationRestrictionRepository restrictionRepository;
    private final com.example.digital_subsidy.service.GrievanceService grievanceService;

    public BeneficiaryController(BeneficiaryService beneficiaryService,
                                 ApplicationService applicationService,
                                 SchemeService schemeService,
                                 DisbursementService disbursementService,
                                 VerificationRepository verificationRepository,
                                 EligibilityScoreRepository eligibilityScoreRepository,
                                 DocumentService documentService,
                                 com.example.digital_subsidy.repository.ApplicationRestrictionRepository restrictionRepository,
                                 com.example.digital_subsidy.service.GrievanceService grievanceService) {
        this.beneficiaryService = beneficiaryService;
        this.applicationService = applicationService;
        this.schemeService = schemeService;
        this.disbursementService = disbursementService;
        this.verificationRepository = verificationRepository;
        this.eligibilityScoreRepository = eligibilityScoreRepository;
        this.documentService = documentService;
        this.restrictionRepository = restrictionRepository;
        this.grievanceService = grievanceService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        String email = authentication.getName();
        Beneficiary beneficiary = beneficiaryService.getBeneficiaryByEmail(email).orElseThrow();

        List<Application> applications = applicationService.getApplicationsForBeneficiary(beneficiary.getId());
        List<com.example.digital_subsidy.model.Grievance> myGrievances = grievanceService.getGrievancesForBeneficiary(beneficiary.getId());

        long totalApps = applications.size();
        long approvedApps = applications.stream().filter(a -> "APPROVED".equalsIgnoreCase(a.getStatus())).count();
        long pendingApps = applications.stream().filter(a -> "PENDING".equalsIgnoreCase(a.getStatus())).count();
        long rejectedApps = applications.stream().filter(a -> "REJECTED".equalsIgnoreCase(a.getStatus())).count();

        BigDecimal totalReceived = applications.stream()
                .filter(a -> "APPROVED".equalsIgnoreCase(a.getStatus()))
                .map(Application::getRequestedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Application latestApplication = applications.isEmpty() ? null : applications.get(0);

        model.addAttribute("beneficiary", beneficiary);
        model.addAttribute("applications", applications);
        model.addAttribute("myGrievances", myGrievances);
        model.addAttribute("latestApplication", latestApplication);
        model.addAttribute("totalApps", totalApps);
        model.addAttribute("approvedApps", approvedApps);
        model.addAttribute("pendingApps", pendingApps);
        model.addAttribute("rejectedApps", rejectedApps);
        model.addAttribute("totalReceived", totalReceived);
        model.addAttribute("recommendedSchemes", schemeService.getAllActiveSchemes());

        return "beneficiary/dashboard";
    }

    @GetMapping("/profile")
    public String profile(Authentication authentication, Model model) {
        String email = authentication.getName();
        Beneficiary beneficiary = beneficiaryService.getBeneficiaryByEmail(email).orElseThrow();
        model.addAttribute("beneficiary", beneficiary);
        return "beneficiary/profile";
    }

    @GetMapping("/applications")
    public String listApplications(Authentication authentication, Model model) {
        String email = authentication.getName();
        Beneficiary beneficiary = beneficiaryService.getBeneficiaryByEmail(email).orElseThrow();
        model.addAttribute("applications", applicationService.getApplicationsForBeneficiary(beneficiary.getId()));
        return "beneficiary/applications";
    }

    @GetMapping("/applications/{identifier}")
    public String viewApplicationDetails(@PathVariable("identifier") String identifier,
                                         @RequestParam(value = "submitted", required = false) Boolean submitted,
                                         Authentication authentication,
                                         Model model,
                                         org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        String username = (authentication != null) ? authentication.getName() : "";
        Application application = applicationService.findApplicationByIdOrNumber(identifier);

        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isBeneficiary = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_BENEFICIARY"));

        Beneficiary beneficiary = beneficiaryService.getBeneficiaryByEmail(username).orElse(null);

        if (application == null && isBeneficiary && beneficiary != null) {
            List<Application> ownApps = applicationService.getApplicationsForBeneficiary(beneficiary.getId());
            if (!ownApps.isEmpty()) {
                return "redirect:/beneficiary/applications/" + ownApps.get(0).getId();
            }
        }

        if (application == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Application not found.");
            return "redirect:/beneficiary/dashboard";
        }

        Long id = application.getId();

        Long appBeneficiaryId = (application.getBeneficiary() != null) ? application.getBeneficiary().getId() : null;
        Long authBeneficiaryId = (beneficiary != null) ? beneficiary.getId() : null;

        org.slf4j.LoggerFactory.getLogger(BeneficiaryController.class).info(
                "Accessing application details - App ID: {}, App Beneficiary ID: {}, Auth Beneficiary ID: {}, username: {}, isAdmin: {}, isBeneficiary: {}",
                id, appBeneficiaryId, authBeneficiaryId, username, isAdmin, isBeneficiary);

        if (!isAdmin) {
            if (!isBeneficiary || authBeneficiaryId == null || !authBeneficiaryId.equals(appBeneficiaryId)) {
                org.slf4j.LoggerFactory.getLogger(BeneficiaryController.class).warn(
                        "Access denied for user {} accessing application {}", username, id);
                if (beneficiary != null) {
                    List<Application> ownApps = applicationService.getApplicationsForBeneficiary(beneficiary.getId());
                    if (!ownApps.isEmpty()) {
                        return "redirect:/beneficiary/applications/" + ownApps.get(0).getId();
                    }
                }
                throw new org.springframework.security.access.AccessDeniedException("Unauthorized access to application details");
            }
        }

        Disbursement disbursement = disbursementService.getDisbursementByApplicationId(id);
        List<Verification> verifications = verificationRepository.findByApplicationOrderByVerifiedAtDesc(application);
        EligibilityScore eligibilityScore = eligibilityScoreRepository.findByApplication(application).orElse(null);

        List<com.example.digital_subsidy.model.Document> docs = documentService.getDocumentsByApplication(id);
        if (docs == null || docs.isEmpty()) {
            if (application.getBeneficiary() != null) {
                docs = documentService.getDocumentsByBeneficiary(application.getBeneficiary().getId());
            }
        }

        com.example.digital_subsidy.model.Document aadhaarDoc = docs != null ? docs.stream()
                .filter(d -> "AADHAAR".equalsIgnoreCase(d.getDocumentType()) && "ACTIVE".equals(d.getStatus()))
                .findFirst().orElse(null) : null;

        com.example.digital_subsidy.model.Document passbookDoc = docs != null ? docs.stream()
                .filter(d -> "BANK_PASSBOOK".equalsIgnoreCase(d.getDocumentType()) && "ACTIVE".equals(d.getStatus()))
                .findFirst().orElse(null) : null;

        List<com.example.digital_subsidy.model.ApplicationRestriction> appRestrictions = restrictionRepository.findByApplicationId(id);
        com.example.digital_subsidy.model.ApplicationRestriction activeRestriction = appRestrictions.stream()
                .filter(r -> "ACTIVE".equalsIgnoreCase(r.getStatus()))
                .findFirst().orElse(null);

        model.addAttribute("application", application);
        model.addAttribute("disbursement", disbursement);
        model.addAttribute("verifications", verifications);
        model.addAttribute("eligibilityScore", eligibilityScore);
        model.addAttribute("aadhaarDoc", aadhaarDoc);
        model.addAttribute("passbookDoc", passbookDoc);
        model.addAttribute("restriction", activeRestriction);
        model.addAttribute("submitted", Boolean.TRUE.equals(submitted));
        return "beneficiary/application-details";
    }

    @org.springframework.web.bind.annotation.PostMapping("/applications/{id}/reupload")
    public String handleDocumentReupload(@PathVariable("id") Long id,
                                         @RequestParam(value = "docAadhaarFile", required = false) org.springframework.web.multipart.MultipartFile docAadhaarFile,
                                         @RequestParam(value = "docBankPassbookFile", required = false) org.springframework.web.multipart.MultipartFile docBankPassbookFile,
                                         Authentication authentication,
                                         org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        String email = authentication.getName();
        Beneficiary beneficiary = beneficiaryService.getBeneficiaryByEmail(email).orElseThrow();
        Application application = applicationService.getApplicationById(id);

        if (!application.getBeneficiary().getId().equals(beneficiary.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Unauthorized access");
        }

        try {
            applicationService.reuploadDocuments(application, docAadhaarFile, docBankPassbookFile, documentService);
            redirectAttributes.addFlashAttribute("successMessage", "Replacement documents uploaded successfully! Application has been resubmitted for Field Verification.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to upload replacement documents: " + ex.getMessage());
        }

        return "redirect:/beneficiary/applications/" + id;
    }

    @GetMapping("/disbursements")
    public String viewDisbursements(Authentication authentication, Model model) {
        String email = authentication.getName();
        Beneficiary beneficiary = beneficiaryService.getBeneficiaryByEmail(email).orElseThrow();
        List<Application> apps = applicationService.getApplicationsForBeneficiary(beneficiary.getId());
        List<Disbursement> disbursements = apps.stream()
                .map(a -> disbursementService.getDisbursementByApplicationId(a.getId()))
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());

        model.addAttribute("beneficiary", beneficiary);
        model.addAttribute("disbursements", disbursements);
        return "beneficiary/disbursements";
    }

    @GetMapping("/notifications")
    public String viewNotifications() {
        return "redirect:/notifications";
    }
}
