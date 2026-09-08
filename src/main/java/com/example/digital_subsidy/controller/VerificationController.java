package com.example.digital_subsidy.controller;

import com.example.digital_subsidy.dto.VerificationActionDto;
import com.example.digital_subsidy.model.Application;
import com.example.digital_subsidy.model.Document;
import com.example.digital_subsidy.repository.ApplicationEligibilityAnswerRepository;
import com.example.digital_subsidy.repository.OfficerRepository;
import com.example.digital_subsidy.repository.RequiredDocumentRepository;
import com.example.digital_subsidy.repository.VerificationRepository;
import com.example.digital_subsidy.service.ApplicationService;
import com.example.digital_subsidy.service.DocumentService;
import com.example.digital_subsidy.service.VerificationService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class VerificationController {

    private static final org.slf4j.Logger logger =
            org.slf4j.LoggerFactory.getLogger(VerificationController.class);

    private final ApplicationService applicationService;
    private final VerificationService verificationService;
    private final DocumentService documentService;
    private final OfficerRepository officerRepository;
    private final VerificationRepository verificationRepository;
    private final ApplicationEligibilityAnswerRepository answerRepository;
    private final RequiredDocumentRepository requiredDocumentRepository;

    public VerificationController(ApplicationService applicationService,
                                  VerificationService verificationService,
                                  DocumentService documentService,
                                  OfficerRepository officerRepository,
                                  VerificationRepository verificationRepository,
                                  ApplicationEligibilityAnswerRepository answerRepository,
                                  RequiredDocumentRepository requiredDocumentRepository) {
        this.applicationService = applicationService;
        this.verificationService = verificationService;
        this.documentService = documentService;
        this.officerRepository = officerRepository;
        this.verificationRepository = verificationRepository;
        this.answerRepository = answerRepository;
        this.requiredDocumentRepository = requiredDocumentRepository;
    }

    // ═══════════════════════════════════════════════════════
    //  FIELD OFFICER
    // ═══════════════════════════════════════════════════════

    @GetMapping("/field-officer/dashboard")
    public String fieldOfficerDashboard(Model model) {
        List<Application> pendingApps = applicationService.getApplicationsByStage("FIELD_VERIFICATION");
        model.addAttribute("pendingApplications", pendingApps);
        model.addAttribute("actionDto", new VerificationActionDto());
        return "field-officer/dashboard";
    }

    @GetMapping("/field-officer/verification/{id}")
    public String fieldOfficerVerificationDetails(
            @PathVariable("id") Long id,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Application app = applicationService.getApplicationById(id);

            // ── All documents for THIS application only ──
            List<Document> applicationDocs = documentService.getDocumentsByApplication(id);

            // ── Submitted eligibility answers (historical snapshot) ──
            var eligibilityAnswers = answerRepository
                    .findByApplicationIdOrderByDisplayOrderAscIdAsc(id);

            // ── Required docs configured for the scheme (for "missing" section) ──
            var requiredDocs = requiredDocumentRepository
                    .findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(app.getScheme().getId());

            VerificationActionDto actionDto = new VerificationActionDto();
            actionDto.setApplicationId(app.getId());

            model.addAttribute("app", app);
            model.addAttribute("beneficiary", app.getBeneficiary());
            model.addAttribute("scheme", app.getScheme());
            model.addAttribute("applicationDocs", applicationDocs);
            model.addAttribute("eligibilityAnswers", eligibilityAnswers);
            model.addAttribute("requiredDocs", requiredDocs);
            model.addAttribute("actionDto", actionDto);
            return "field-officer/verification-details";
        } catch (Exception e) {
            logger.error("Error loading Field Officer verification for application {}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Unable to load verification details for application ID: " + id);
            return "redirect:/field-officer/dashboard";
        }
    }

    // ═══════════════════════════════════════════════════════
    //  DISTRICT OFFICER
    // ═══════════════════════════════════════════════════════

    @GetMapping("/district-officer/dashboard")
    public String districtOfficerDashboard(Authentication authentication, Model model) {
        List<Application> pendingApps = applicationService.getApplicationsByStage("DISTRICT_REVIEW");
        if (authentication != null) {
            String email = authentication.getName();
            com.example.digital_subsidy.model.Officer officer =
                    officerRepository.findByUserEmail(email).orElse(null);
            logger.info("[DISTRICT DASHBOARD] Officer: {} | State: {} | Total DISTRICT_REVIEW apps: {}",
                    email,
                    officer != null ? officer.getState() : "N/A",
                    pendingApps.size());
            if (officer != null && officer.getState() != null && !officer.getState().isBlank()) {
                pendingApps = pendingApps.stream()
                        .filter(a -> a.getBeneficiary() != null &&
                                officer.getState().equalsIgnoreCase(a.getBeneficiary().getState()))
                        .collect(java.util.stream.Collectors.toList());
            }
            logger.info("[DISTRICT DASHBOARD] Applications after state filter: {}", pendingApps.size());
        }
        model.addAttribute("pendingApplications", pendingApps);
        model.addAttribute("actionDto", new VerificationActionDto());
        return "district-officer/dashboard";
    }

    @GetMapping("/district-officer/review/{id}")
    public String districtOfficerReviewDetails(
            @PathVariable("id") Long id,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Application app = applicationService.getApplicationById(id);
            if (app == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Application not found with ID: " + id);
                return "redirect:/district-officer/dashboard";
            }

            com.example.digital_subsidy.model.Beneficiary beneficiary = app.getBeneficiary();

            // ── Regional / IDOR auth by STATE ──
            if (authentication != null) {
                String email = authentication.getName();
                com.example.digital_subsidy.model.Officer officer =
                        officerRepository.findByUserEmail(email).orElse(null);
                if (officer != null && officer.getState() != null && beneficiary != null
                        && beneficiary.getState() != null) {
                    if (!officer.getState().equalsIgnoreCase(beneficiary.getState())) {
                        redirectAttributes.addFlashAttribute("errorMessage",
                                "Access Denied: You are only authorized to review applications from state: "
                                        + officer.getState());
                        return "redirect:/district-officer/dashboard";
                    }
                }
            }

            // ── Field Officer verification record ──
            List<com.example.digital_subsidy.model.Verification> verifications =
                    verificationRepository.findByApplicationOrderByVerifiedAtDesc(app);
            com.example.digital_subsidy.model.Verification fieldVerification =
                    (verifications != null) ? verifications.stream()
                            .filter(v -> "FIELD_OFFICER".equalsIgnoreCase(v.getOfficerRole())
                                    || "ROLE_FIELD_OFFICER".equalsIgnoreCase(v.getOfficerRole()))
                            .findFirst().orElse(null) : null;

            // ── All documents for THIS application only ──
            List<Document> applicationDocs = documentService.getDocumentsByApplication(id);

            // ── Submitted eligibility answers (historical snapshot) ──
            var eligibilityAnswers = answerRepository
                    .findByApplicationIdOrderByDisplayOrderAscIdAsc(id);

            VerificationActionDto actionDto = new VerificationActionDto();
            actionDto.setApplicationId(app.getId());

            model.addAttribute("app", app);
            model.addAttribute("beneficiary", beneficiary);
            model.addAttribute("scheme", app.getScheme());
            model.addAttribute("fieldVerification", fieldVerification);
            model.addAttribute("applicationDocs", applicationDocs);
            model.addAttribute("eligibilityAnswers", eligibilityAnswers);
            model.addAttribute("actionDto", actionDto);

            return "district-officer/review-details";
        } catch (Exception e) {
            logger.error("Error loading District Officer review for application {}", id, e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Unable to load review details for application ID: " + id);
            return "redirect:/district-officer/dashboard";
        }
    }

    // ═══════════════════════════════════════════════════════
    //  SHARED VERIFICATION ACTION
    // ═══════════════════════════════════════════════════════

    @PostMapping("/officer/verify")
    public String processVerification(@Valid @ModelAttribute("actionDto") VerificationActionDto actionDto,
                                      @RequestParam("role") String role,
                                      Authentication authentication,
                                      RedirectAttributes redirectAttributes) {
        String officerEmail = authentication.getName();
        try {
            verificationService.processVerification(officerEmail, role, actionDto);
            if ("FIELD_OFFICER".equalsIgnoreCase(role)) {
                redirectAttributes.addFlashAttribute("successMessage", "Field verification completed successfully.");
                return "redirect:/field-officer/dashboard?verified=true";
            } else if ("DISTRICT_OFFICER".equalsIgnoreCase(role)) {
                if ("APPROVE".equalsIgnoreCase(actionDto.getAction())) {
                    redirectAttributes.addFlashAttribute("successMessage",
                            "Application approved and forwarded for Finance Approval.");
                } else if ("REJECT".equalsIgnoreCase(actionDto.getAction())) {
                    redirectAttributes.addFlashAttribute("successMessage", "Application has been rejected.");
                } else {
                    redirectAttributes.addFlashAttribute("successMessage",
                            "Re-verification requested from Field Officer.");
                }
                return "redirect:/district-officer/dashboard?verified=true";
            } else {
                redirectAttributes.addFlashAttribute("successMessage", "Finance approval completed.");
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
