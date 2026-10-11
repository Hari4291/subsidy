package com.example.digital_subsidy.controller;

import com.example.digital_subsidy.dto.TimelineEventDto;
import com.example.digital_subsidy.model.Application;
import com.example.digital_subsidy.model.Beneficiary;
import com.example.digital_subsidy.model.User;
import com.example.digital_subsidy.repository.UserRepository;
import com.example.digital_subsidy.service.ApplicationService;
import com.example.digital_subsidy.service.BeneficiaryService;
import com.example.digital_subsidy.service.TimelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller
public class TrackingController {

    private static final Logger logger = LoggerFactory.getLogger(TrackingController.class);

    private final ApplicationService applicationService;
    private final TimelineService timelineService;
    private final BeneficiaryService beneficiaryService;
    private final UserRepository userRepository;
    private final com.example.digital_subsidy.repository.OfficerRepository officerRepository;

    public TrackingController(ApplicationService applicationService,
                              TimelineService timelineService,
                              BeneficiaryService beneficiaryService,
                              UserRepository userRepository,
                              com.example.digital_subsidy.repository.OfficerRepository officerRepository) {
        this.applicationService = applicationService;
        this.timelineService = timelineService;
        this.beneficiaryService = beneficiaryService;
        this.userRepository = userRepository;
        this.officerRepository = officerRepository;
    }

    @GetMapping({"/applications/{identifier}/tracking", "/beneficiary/applications/{identifier}/tracking"})
    public String viewTracking(@PathVariable("identifier") String identifier,
                               Authentication authentication,
                               Model model,
                               org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        String username = authentication.getName();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isOfficer = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_FIELD_OFFICER")
                        || a.getAuthority().equals("ROLE_DISTRICT_OFFICER")
                        || a.getAuthority().equals("ROLE_FINANCE_APPROVER"));
        boolean isBeneficiary = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_BENEFICIARY"));

        Application application = applicationService.findApplicationByIdOrNumber(identifier);

        // Fallback for null/invalid identifier (e.g., /applications/null/tracking)
        if (application == null && isBeneficiary) {
            Beneficiary beneficiary = beneficiaryService.getBeneficiaryByEmail(username).orElse(null);
            if (beneficiary != null) {
                List<Application> userApps = applicationService.getApplicationsForBeneficiary(beneficiary.getId());
                if (!userApps.isEmpty()) {
                    application = userApps.get(0);
                }
            }
        }

        if (application == null) {
            logger.warn("Application tracking not found for identifier: {} requested by user: {}", identifier, username);
            if (isBeneficiary) {
                redirectAttributes.addFlashAttribute("errorMessage", "No active application found to track.");
                return "redirect:/beneficiary/dashboard";
            } else if (isOfficer) {
                redirectAttributes.addFlashAttribute("errorMessage", "Application not found.");
                return "redirect:/field-officer/dashboard";
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Application not found.");
                return "redirect:/admin/dashboard";
            }
        }

        if (isOfficer && !isAdmin) {
            boolean isFO = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_FIELD_OFFICER"));
            boolean isDO = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_DISTRICT_OFFICER"));
            if (isFO || isDO) {
                com.example.digital_subsidy.model.Officer officer = officerRepository.findByUserEmail(username).orElse(null);
                if (officer != null && officer.getState() != null && application.getBeneficiary() != null && application.getBeneficiary().getState() != null) {
                    if (!officer.getState().equalsIgnoreCase(application.getBeneficiary().getState())) {
                        throw new AccessDeniedException("You are not authorized to view tracking for applications outside your state.");
                    }
                    if (isDO && officer.getDistrict() != null && !officer.getDistrict().isBlank() && application.getBeneficiary().getDistrict() != null) {
                        if (!officer.getDistrict().equalsIgnoreCase(application.getBeneficiary().getDistrict())) {
                            throw new AccessDeniedException("You are not authorized to view tracking for applications outside your district.");
                        }
                    }
                }
            }
        }

        if (!isAdmin && !isOfficer) {
            if (!isBeneficiary) {
                logger.warn("User {} is neither Admin/Officer nor Beneficiary attempting to track app {}", username, application.getId());
                throw new AccessDeniedException("You are not authorized to view this tracking timeline.");
            }

            Beneficiary beneficiary = beneficiaryService.getBeneficiaryByEmail(username).orElse(null);
            Long appBeneficiaryId = (application.getBeneficiary() != null) ? application.getBeneficiary().getId() : null;
            Long authBeneficiaryId = (beneficiary != null) ? beneficiary.getId() : null;

            if (authBeneficiaryId == null || !authBeneficiaryId.equals(appBeneficiaryId)) {
                logger.warn("IDOR attempt: User {} (Beneficiary ID: {}) tried to access application {} (Owner ID: {})",
                        username, authBeneficiaryId, application.getId(), appBeneficiaryId);

                if (beneficiary != null) {
                    List<Application> ownApps = applicationService.getApplicationsForBeneficiary(beneficiary.getId());
                    if (!ownApps.isEmpty()) {
                        return "redirect:/applications/" + ownApps.get(0).getId() + "/tracking";
                    }
                }
                throw new AccessDeniedException("You are not authorized to view tracking for this application.");
            }
        }

        List<TimelineEventDto> timelineEvents = timelineService.buildApplicationTimeline(application.getId());

        model.addAttribute("application", application);
        model.addAttribute("timelineEvents", timelineEvents);
        model.addAttribute("currentStage", application.getCurrentStage() != null ? application.getCurrentStage() : application.getStatus());

        return "beneficiary/tracking";
    }

    public String viewTracking(Long id, Authentication authentication, Model model) {
        return viewTracking(id != null ? String.valueOf(id) : "null", authentication, model, new org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap());
    }

    public String viewTracking(String identifier, Authentication authentication, Model model) {
        return viewTracking(identifier, authentication, model, new org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap());
    }
}
