package com.example.digital_subsidy.controller;

import com.example.digital_subsidy.model.Application;
import com.example.digital_subsidy.model.Beneficiary;
import com.example.digital_subsidy.model.Grievance;
import com.example.digital_subsidy.model.Officer;
import com.example.digital_subsidy.service.ApplicationService;
import com.example.digital_subsidy.service.BeneficiaryService;
import com.example.digital_subsidy.service.GrievanceService;
import com.example.digital_subsidy.service.OfficerService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class GrievanceController {

    private final GrievanceService grievanceService;
    private final BeneficiaryService beneficiaryService;
    private final ApplicationService applicationService;
    private final OfficerService officerService;

    public GrievanceController(GrievanceService grievanceService,
                               BeneficiaryService beneficiaryService,
                               ApplicationService applicationService,
                               OfficerService officerService) {
        this.grievanceService = grievanceService;
        this.beneficiaryService = beneficiaryService;
        this.applicationService = applicationService;
        this.officerService = officerService;
    }

    // ==========================================
    // BENEFICIARY ENDPOINTS
    // ==========================================

    @GetMapping("/beneficiary/grievances")
    public String showBeneficiaryGrievances(Authentication authentication, Model model) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }
        String email = authentication.getName();
        Beneficiary beneficiary = beneficiaryService.getBeneficiaryByEmail(email).orElseThrow();

        List<Grievance> grievances = grievanceService.getGrievancesForBeneficiary(beneficiary.getId());
        List<Application> applications = applicationService.getApplicationsForBeneficiary(beneficiary.getId());

        model.addAttribute("beneficiary", beneficiary);
        model.addAttribute("grievances", grievances);
        model.addAttribute("applications", applications);

        return "beneficiary/grievances";
    }

    @GetMapping("/beneficiary/grievances/new")
    public String showNewGrievanceForm(Authentication authentication, Model model) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }
        String email = authentication.getName();
        Beneficiary beneficiary = beneficiaryService.getBeneficiaryByEmail(email).orElseThrow();
        List<Application> applications = applicationService.getApplicationsForBeneficiary(beneficiary.getId());

        model.addAttribute("beneficiary", beneficiary);
        model.addAttribute("applications", applications);
        return "beneficiary/grievance-form";
    }

    @PostMapping({"/beneficiary/grievances/submit", "/beneficiary/grievances"})
    public String submitGrievance(@RequestParam(value = "applicationId", required = false) Long applicationId,
                                  @RequestParam(value = "category", required = false) String category,
                                  @RequestParam(value = "subject", required = false) String subject,
                                  @RequestParam(value = "description", required = false) String description,
                                  @RequestParam(value = "supportingDocument", required = false) MultipartFile supportingDocument,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return "redirect:/login";
            }
            String email = authentication.getName();
            Beneficiary beneficiary = beneficiaryService.getBeneficiaryByEmail(email).orElseThrow();

            if (description == null || description.isBlank()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Grievance description is required.");
                return "redirect:/beneficiary/grievances/new";
            }

            // IDOR Protection: If applicationId is provided, verify ownership
            if (applicationId != null) {
                Application app = applicationService.getApplicationById(applicationId);
                if (app == null || !app.getBeneficiary().getId().equals(beneficiary.getId())) {
                    redirectAttributes.addFlashAttribute("errorMessage", "Access Denied: Invalid application selected.");
                    return "redirect:/beneficiary/grievances/new";
                }
            }

            String documentPath = null;
            if (supportingDocument != null && !supportingDocument.isEmpty()) {
                // 5MB Size Validation (5 * 1024 * 1024 bytes)
                if (supportingDocument.getSize() > 5 * 1024 * 1024) {
                    redirectAttributes.addFlashAttribute("errorMessage", "Supporting document size exceeds maximum allowed limit of 5MB.");
                    return "redirect:/beneficiary/grievances/new";
                }
                try {
                    String uploadDir = "uploads/grievances/";
                    Files.createDirectories(Paths.get(uploadDir));
                    String fileName = UUID.randomUUID() + "_" + supportingDocument.getOriginalFilename();
                    Path filePath = Paths.get(uploadDir + fileName);
                    Files.write(filePath, supportingDocument.getBytes());
                    documentPath = "/" + uploadDir + fileName;
                } catch (Exception ex) {
                    org.slf4j.LoggerFactory.getLogger(GrievanceController.class).warn("Failed to save attachment", ex);
                }
            }

            String finalSubject = (subject != null && !subject.isBlank()) ? subject : (category != null ? category.replace("_", " ") : "Grievance");
            Grievance g = grievanceService.submitGrievance(beneficiary, applicationId, category, finalSubject, description, documentPath);

            redirectAttributes.addFlashAttribute("successMessage", "Your grievance has been submitted successfully.");
            return "redirect:/beneficiary/dashboard";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/beneficiary/grievances/new";
        }
    }

    @GetMapping("/beneficiary/grievances/{id}")
    public String viewBeneficiaryGrievanceDetails(@PathVariable("id") Long id, Authentication authentication, Model model) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }
        String email = authentication.getName();
        Beneficiary beneficiary = beneficiaryService.getBeneficiaryByEmail(email).orElseThrow();
        Grievance grievance = grievanceService.getGrievanceById(id);

        if (!grievance.getBeneficiary().getId().equals(beneficiary.getId())) {
            throw new AccessDeniedException("Unauthorized to view this grievance.");
        }

        model.addAttribute("grievance", grievance);
        model.addAttribute("beneficiary", beneficiary);
        return "beneficiary/grievance-details";
    }

    @PostMapping("/beneficiary/grievances/{id}/clarification")
    public String submitBeneficiaryClarification(@PathVariable("id") Long id,
                                                 @RequestParam("clarificationResponse") String clarificationResponse,
                                                 Authentication authentication,
                                                 RedirectAttributes redirectAttributes) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return "redirect:/login";
            }
            String email = authentication.getName();
            Beneficiary beneficiary = beneficiaryService.getBeneficiaryByEmail(email).orElseThrow();

            grievanceService.submitClarificationResponse(id, clarificationResponse, beneficiary);
            redirectAttributes.addFlashAttribute("successMessage", "Clarification response submitted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/beneficiary/grievances/" + id;
    }

    @PostMapping("/beneficiary/grievances/{id}/reopen")
    public String reopenBeneficiaryGrievance(@PathVariable("id") Long id,
                                             @RequestParam("reopenReason") String reopenReason,
                                             Authentication authentication,
                                             RedirectAttributes redirectAttributes) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return "redirect:/login";
            }
            String email = authentication.getName();
            Beneficiary beneficiary = beneficiaryService.getBeneficiaryByEmail(email).orElseThrow();

            grievanceService.reopenGrievance(id, reopenReason, beneficiary);
            redirectAttributes.addFlashAttribute("successMessage", "Grievance reopened successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/beneficiary/grievances/" + id;
    }

    // ==========================================
    // DISTRICT OFFICER ENDPOINTS
    // ==========================================

    @GetMapping({"/district-officer/grievances", "/district_officer/grievances"})
    public String showDistrictGrievances(@RequestParam(value = "status", required = false) String status,
                                         @RequestParam(value = "category", required = false) String category,
                                         @RequestParam(value = "priority", required = false) String priority,
                                         Authentication authentication,
                                         Model model) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }
        String email = authentication.getName();
        Officer officer = officerService.getOfficerByEmail(email).orElse(null);

        String district = officer != null ? officer.getDistrict() : null;
        String state = officer != null ? officer.getState() : null;

        List<Grievance> grievances = grievanceService.getGrievancesForDistrict(district, state, status, category, priority);
        Map<String, Long> metrics = grievanceService.getGrievanceMetrics(district, state);

        model.addAttribute("grievances", grievances);
        model.addAttribute("metrics", metrics);
        model.addAttribute("districtName", district != null ? district : (state != null ? state : "District"));
        model.addAttribute("selectedStatus", status != null ? status : "ALL");
        model.addAttribute("selectedCategory", category != null ? category : "ALL");
        model.addAttribute("selectedPriority", priority != null ? priority : "ALL");

        return "district-officer/grievances";
    }

    @GetMapping({"/district-officer/grievances/{id}", "/district_officer/grievances/{id}"})
    public String viewDistrictGrievanceDetails(@PathVariable("id") Long id, Authentication authentication, Model model) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }
        String email = authentication.getName();
        Officer officer = officerService.getOfficerByEmail(email).orElse(null);

        Grievance grievance = grievanceService.getGrievanceById(id);

        if (officer != null && officer.getDistrict() != null) {
            if (grievance.getBeneficiary() != null && !officer.getDistrict().equalsIgnoreCase(grievance.getBeneficiary().getDistrict())) {
                org.slf4j.LoggerFactory.getLogger(GrievanceController.class).warn("DO {} from {} attempted access to grievance {} in {}",
                        email, officer.getDistrict(), id, grievance.getBeneficiary().getDistrict());
            }
        }

        model.addAttribute("grievance", grievance);
        model.addAttribute("officer", officer);
        return "district-officer/grievance-details";
    }

    @PostMapping({"/district-officer/grievances/{id}/action", "/district-officer/grievance/resolve", "/district_officer/grievances/{id}/action", "/district_officer/grievance/resolve"})
    public String handleDistrictGrievanceAction(@PathVariable(value = "id", required = false) Long pathId,
                                                @RequestParam(value = "grievanceId", required = false) Long paramId,
                                                @RequestParam("status") String status,
                                                @RequestParam(value = "remarks", required = false) String remarks,
                                                Authentication authentication,
                                                RedirectAttributes redirectAttributes) {
        try {
            Long grievanceId = pathId != null ? pathId : paramId;
            if (grievanceId == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Invalid grievance ID.");
                return "redirect:/district-officer/grievances";
            }

            String email = authentication.getName();
            Officer officer = officerService.getOfficerByEmail(email).orElse(null);
            Long officerId = officer != null ? officer.getId() : null;

            grievanceService.updateGrievanceStatus(grievanceId, status, remarks, officerId, email);
            redirectAttributes.addFlashAttribute("successMessage", "Grievance status updated to " + status + " successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/district-officer/grievances";
    }
}
