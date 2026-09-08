package com.example.digital_subsidy.controller;

import com.example.digital_subsidy.dto.DisbursementMilestoneDto;
import com.example.digital_subsidy.dto.EligibilityCriteriaDto;
import com.example.digital_subsidy.dto.RequiredDocumentDto;
import com.example.digital_subsidy.dto.SchemeConfigDto;
import com.example.digital_subsidy.model.DisbursementMilestone;
import com.example.digital_subsidy.model.EligibilityCriteria;
import com.example.digital_subsidy.model.RequiredDocument;
import com.example.digital_subsidy.model.Scheme;
import com.example.digital_subsidy.service.SchemeService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/schemes")
public class SchemeController {

    private final SchemeService schemeService;

    public SchemeController(SchemeService schemeService) {
        this.schemeService = schemeService;
    }

    @GetMapping
    public String listSchemes(Model model) {
        List<Scheme> allSchemes = schemeService.getAllSchemes();
        model.addAttribute("schemes", allSchemes);

        Map<String, Object> schemeMetrics = new HashMap<>();
        schemeMetrics.put("totalSchemes", allSchemes.size());
        schemeMetrics.put("activeSchemes", schemeService.countActiveSchemes());
        schemeMetrics.put("draftSchemes", schemeService.countDraftSchemes());
        schemeMetrics.put("totalCriteria", schemeService.countTotalCriteria());
        schemeMetrics.put("totalDocuments", schemeService.countTotalDocuments());
        model.addAttribute("schemeMetrics", schemeMetrics);

        return "admin/schemes-list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        SchemeConfigDto configDto = new SchemeConfigDto();
        configDto.setStatus("DRAFT");
        configDto.setMinQualifyingScore(60);
        model.addAttribute("schemeDto", configDto);
        model.addAttribute("categoryOptions", schemeService.getAvailableCategories());
        model.addAttribute("isNew", true);
        model.addAttribute("step", 1);
        return "admin/scheme-config";
    }

    @PostMapping("/save-basic")
    public String saveBasicDetails(@ModelAttribute("schemeDto") SchemeConfigDto schemeDto,
                                   Authentication authentication,
                                   RedirectAttributes redirectAttributes) {
        String adminEmail = authentication != null ? authentication.getName() : "admin@gov.in";
        try {
            Scheme savedScheme = schemeService.saveSchemeConfig(schemeDto, adminEmail);
            redirectAttributes.addFlashAttribute("successMessage", "Scheme basic details saved successfully.");
            return "redirect:/admin/schemes/" + savedScheme.getId() + "/config?step=2";
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            if (schemeDto.getId() != null) {
                return "redirect:/admin/schemes/" + schemeDto.getId() + "/config?step=1";
            }
            return "redirect:/admin/schemes/new";
        }
    }

    @GetMapping("/{id}/config")
    public String showSchemeConfigWizard(@PathVariable("id") Long id,
                                         @RequestParam(value = "step", defaultValue = "1") int step,
                                         Model model) {
        Map<String, Object> configData = schemeService.getSchemeConfigData(id);
        Scheme scheme = (Scheme) configData.get("scheme");
        @SuppressWarnings("unchecked")
        List<EligibilityCriteria> criteriaList = (List<EligibilityCriteria>) configData.get("criteriaList");
        @SuppressWarnings("unchecked")
        List<RequiredDocument> docList = (List<RequiredDocument>) configData.get("docList");
        @SuppressWarnings("unchecked")
        List<DisbursementMilestone> milestoneList = (List<DisbursementMilestone>) configData.get("milestoneList");
        int totalWeight = (int) configData.get("totalWeight");
        int totalMilestonePercentage = (int) configData.get("totalMilestonePercentage");
        @SuppressWarnings("unchecked")
        List<String> activationErrors = (List<String>) configData.get("activationErrors");
        @SuppressWarnings("unchecked")
        List<String> categoryOptions = (List<String>) configData.get("categoryOptions");

        SchemeConfigDto schemeDto = new SchemeConfigDto();
        schemeDto.setId(scheme.getId());
        schemeDto.setName(scheme.getName());
        schemeDto.setCategory(scheme.getCategory());
        schemeDto.setDescription(scheme.getDescription());
        schemeDto.setMinAmount(scheme.getMinAmount());
        schemeDto.setMaxAmount(scheme.getMaxAmount());
        schemeDto.setTotalBudget(scheme.getTotalBudget());
        schemeDto.setRemainingBudget(scheme.getRemainingBudget());
        schemeDto.setTargetCategories(scheme.getTargetCategories());
        schemeDto.setEligibleStates(scheme.getEligibleStates());
        schemeDto.setMinQualifyingScore(scheme.getMinQualifyingScore());
        schemeDto.setStartDate(scheme.getStartDate());
        schemeDto.setEndDate(scheme.getEndDate());
        schemeDto.setStatus(scheme.getStatus());

        model.addAttribute("scheme", scheme);
        model.addAttribute("schemeDto", schemeDto);
        model.addAttribute("criteriaList", criteriaList);
        model.addAttribute("docList", docList);
        model.addAttribute("milestoneList", milestoneList);
        model.addAttribute("totalWeight", totalWeight);
        model.addAttribute("totalMilestonePercentage", totalMilestonePercentage);
        model.addAttribute("activationErrors", activationErrors);
        model.addAttribute("categoryOptions", categoryOptions);
        model.addAttribute("step", step);
        model.addAttribute("newCriteriaDto", new EligibilityCriteriaDto());
        model.addAttribute("newDocDto", new RequiredDocumentDto());
        model.addAttribute("newMilestoneDto", new DisbursementMilestoneDto());

        return "admin/scheme-config";
    }

    @PostMapping("/{id}/criteria/save")
    public String saveCriterion(@PathVariable("id") Long schemeId,
                                @ModelAttribute("newCriteriaDto") EligibilityCriteriaDto criteriaDto,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        String adminEmail = authentication != null ? authentication.getName() : "admin@gov.in";
        try {
            schemeService.saveOrUpdateCriterion(schemeId, criteriaDto, adminEmail);
            redirectAttributes.addFlashAttribute("successMessage", "Eligibility question/criterion saved successfully.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/schemes/" + schemeId + "/config?step=2";
    }

    @PostMapping("/{id}/criteria/{critId}/toggle")
    public String toggleCriterion(@PathVariable("id") Long schemeId,
                                  @PathVariable("critId") Long critId,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        String adminEmail = authentication != null ? authentication.getName() : "admin@gov.in";
        schemeService.toggleCriterionStatus(critId, adminEmail);
        redirectAttributes.addFlashAttribute("successMessage", "Criterion status updated.");
        return "redirect:/admin/schemes/" + schemeId + "/config?step=3";
    }

    @PostMapping("/{id}/documents/save")
    public String saveDocument(@PathVariable("id") Long schemeId,
                               @ModelAttribute("newDocDto") RequiredDocumentDto docDto,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        String adminEmail = authentication != null ? authentication.getName() : "admin@gov.in";
        try {
            schemeService.saveOrUpdateDocument(schemeId, docDto, adminEmail);
            redirectAttributes.addFlashAttribute("successMessage", "Required supporting document saved successfully.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/schemes/" + schemeId + "/config?step=4";
    }

    @PostMapping("/{id}/documents/{docId}/toggle")
    public String toggleDocument(@PathVariable("id") Long schemeId,
                                 @PathVariable("docId") Long docId,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        String adminEmail = authentication != null ? authentication.getName() : "admin@gov.in";
        schemeService.toggleDocumentStatus(docId, adminEmail);
        redirectAttributes.addFlashAttribute("successMessage", "Document status updated.");
        return "redirect:/admin/schemes/" + schemeId + "/config?step=4";
    }

    @PostMapping("/{id}/milestones/save")
    public String saveMilestone(@PathVariable("id") Long schemeId,
                                @ModelAttribute("newMilestoneDto") DisbursementMilestoneDto milestoneDto,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        String adminEmail = authentication != null ? authentication.getName() : "admin@gov.in";
        try {
            schemeService.saveOrUpdateMilestone(schemeId, milestoneDto, adminEmail);
            redirectAttributes.addFlashAttribute("successMessage", "Disbursement milestone saved successfully.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/schemes/" + schemeId + "/config?step=5";
    }

    @PostMapping("/{id}/milestones/{milestoneId}/toggle")
    public String toggleMilestone(@PathVariable("id") Long schemeId,
                                  @PathVariable("milestoneId") Long milestoneId,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        String adminEmail = authentication != null ? authentication.getName() : "admin@gov.in";
        schemeService.toggleMilestoneStatus(milestoneId, adminEmail);
        redirectAttributes.addFlashAttribute("successMessage", "Milestone status updated.");
        return "redirect:/admin/schemes/" + schemeId + "/config?step=5";
    }

    @PostMapping("/{id}/milestones/{milestoneId}/delete")
    public String deleteMilestone(@PathVariable("id") Long schemeId,
                                  @PathVariable("milestoneId") Long milestoneId,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        String adminEmail = authentication != null ? authentication.getName() : "admin@gov.in";
        schemeService.deleteMilestone(milestoneId, adminEmail);
        redirectAttributes.addFlashAttribute("successMessage", "Milestone deleted.");
        return "redirect:/admin/schemes/" + schemeId + "/config?step=5";
    }

    @PostMapping("/{id}/activate")
    public String toggleSchemeActivation(@PathVariable("id") Long schemeId,
                                          @RequestParam("targetStatus") String targetStatus,
                                          Authentication authentication,
                                          RedirectAttributes redirectAttributes) {
        String adminEmail = authentication != null ? authentication.getName() : "admin@gov.in";
        try {
            Scheme updated = schemeService.activateOrDeactivateScheme(schemeId, targetStatus, adminEmail);
            redirectAttributes.addFlashAttribute("successMessage", "Scheme status updated to " + updated.getStatus() + ".");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/schemes/" + schemeId + "/config?step=6";
        }
        return "redirect:/admin/schemes";
    }
}
