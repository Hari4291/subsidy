package com.example.digital_subsidy.controller;

import com.example.digital_subsidy.dto.ApplicationSubmissionDto;
import com.example.digital_subsidy.model.Application;
import com.example.digital_subsidy.model.Beneficiary;
import com.example.digital_subsidy.model.Scheme;
import com.example.digital_subsidy.service.ApplicationService;
import com.example.digital_subsidy.service.BeneficiaryService;
import com.example.digital_subsidy.service.SchemeService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Controller
public class ApplicationController {

    private final ApplicationService applicationService;
    private final BeneficiaryService beneficiaryService;
    private final SchemeService schemeService;

    public ApplicationController(ApplicationService applicationService,
                                 BeneficiaryService beneficiaryService,
                                 SchemeService schemeService) {
        this.applicationService = applicationService;
        this.beneficiaryService = beneficiaryService;
        this.schemeService = schemeService;
    }

    @GetMapping("/beneficiary/apply")
    public String showApplicationForm(@RequestParam("schemeId") Long schemeId,
                                     Authentication authentication,
                                     Model model) {
        String email = authentication.getName();
        Beneficiary beneficiary = beneficiaryService.getBeneficiaryByEmail(email).orElseThrow();
        Scheme scheme = schemeService.getSchemeById(schemeId);

        ApplicationSubmissionDto dto = new ApplicationSubmissionDto();
        dto.setSchemeId(scheme.getId());
        dto.setRequestedAmount(scheme.getMaxAmount());

        model.addAttribute("dto", dto);
        model.addAttribute("beneficiary", beneficiary);
        model.addAttribute("scheme", scheme);
        return "beneficiary/apply";
    }

    @PostMapping("/beneficiary/apply")
    public String processApplicationSubmission(@Valid @ModelAttribute("dto") ApplicationSubmissionDto dto,
                                               BindingResult bindingResult,
                                               HttpSession session,
                                               Authentication authentication,
                                               Model model) {
        String email = authentication.getName();
        Beneficiary beneficiary = beneficiaryService.getBeneficiaryByEmail(email).orElseThrow();
        Scheme scheme = schemeService.getSchemeById(dto.getSchemeId());

        if (bindingResult.hasErrors()) {
            model.addAttribute("beneficiary", beneficiary);
            model.addAttribute("scheme", scheme);
            return "beneficiary/apply";
        }

        // ── Retrieve criterion answers stored during eligibility check ──
        // EligibilityController saved them in session after the check was run.
        Long schemeId = dto.getSchemeId();
        @SuppressWarnings("unchecked")
        Map<Long, String> criterionAnswers =
                (Map<Long, String>) session.getAttribute("pendingCriterionAnswers_" + schemeId);
        @SuppressWarnings("unchecked")
        Map<Long, String> questionSnapshots =
                (Map<Long, String>) session.getAttribute("pendingQuestionSnapshots_" + schemeId);
        @SuppressWarnings("unchecked")
        Map<Long, Integer> displayOrders =
                (Map<Long, Integer>) session.getAttribute("pendingDisplayOrders_" + schemeId);

        if (criterionAnswers != null) {
            dto.setCriterionAnswers(criterionAnswers);
        }
        if (questionSnapshots != null) {
            dto.setCriterionQuestionSnapshots(questionSnapshots);
        }
        if (displayOrders != null) {
            dto.setCriterionDisplayOrders(displayOrders);
        }

        Application application = applicationService.submitApplication(beneficiary, dto);

        // Clear session answers after successful submission
        session.removeAttribute("pendingCriterionAnswers_" + schemeId);
        session.removeAttribute("pendingQuestionSnapshots_" + schemeId);
        session.removeAttribute("pendingDisplayOrders_" + schemeId);

        return "redirect:/beneficiary/applications/" + application.getId() + "?submitted=true";
    }
}
