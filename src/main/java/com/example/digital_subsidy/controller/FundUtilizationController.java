package com.example.digital_subsidy.controller;

import com.example.digital_subsidy.dto.DistrictFundUtilizationDto;
import com.example.digital_subsidy.dto.FundUtilizationSummaryDto;
import com.example.digital_subsidy.dto.SchemeFundUtilizationDto;
import com.example.digital_subsidy.dto.StateFundUtilizationDto;
import com.example.digital_subsidy.model.Officer;
import com.example.digital_subsidy.repository.OfficerRepository;
import com.example.digital_subsidy.service.FundUtilizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class FundUtilizationController {

    private static final Logger log = LoggerFactory.getLogger(FundUtilizationController.class);

    private final FundUtilizationService fundUtilizationService;
    private final OfficerRepository officerRepository;

    public FundUtilizationController(FundUtilizationService fundUtilizationService,
                                     OfficerRepository officerRepository) {
        this.fundUtilizationService = fundUtilizationService;
        this.officerRepository = officerRepository;
    }

    @GetMapping("/finance/utilization")
    public String viewFinanceFundUtilization(@RequestParam(value = "category", required = false) String category,
                                             @RequestParam(value = "state", required = false) String state,
                                             @RequestParam(value = "district", required = false) String district,
                                             Authentication authentication, Model model) {
        verifyFinancialAccess(authentication, Set.of("ROLE_FINANCE_APPROVER", "ROLE_ADMIN"));

        FundUtilizationSummaryDto summary = fundUtilizationService.getSystemFundUtilizationSummary(category, state, district);
        List<SchemeFundUtilizationDto> schemeWise = fundUtilizationService.getSchemeWiseFundUtilization(category, state);
        List<StateFundUtilizationDto> stateWise = fundUtilizationService.getStateWiseFundUtilization();
        List<DistrictFundUtilizationDto> districtWise = fundUtilizationService.getDistrictWiseFundUtilization(state);

        model.addAttribute("summary", summary);
        model.addAttribute("schemeWise", schemeWise);
        model.addAttribute("stateWise", stateWise);
        model.addAttribute("districtWise", districtWise);
        model.addAttribute("categoryFilter", category);
        model.addAttribute("stateFilter", state);
        model.addAttribute("districtFilter", district);

        return "finance/utilization-overview";
    }

    @GetMapping("/district-officer/utilization")
    public String viewDistrictFundUtilization(Authentication authentication, Model model) {
        verifyFinancialAccess(authentication, Set.of("ROLE_DISTRICT_OFFICER", "ROLE_ADMIN"));

        String email = authentication != null ? authentication.getName() : "";
        Officer officer = officerRepository.findByUserEmail(email).orElse(null);
        String state = officer != null ? officer.getState() : null;
        String district = officer != null ? officer.getDistrict() : null;

        FundUtilizationSummaryDto summary = fundUtilizationService.getSystemFundUtilizationSummary(null, state, district);
        List<SchemeFundUtilizationDto> schemeWise = fundUtilizationService.getSchemeWiseFundUtilization(null, state);
        List<DistrictFundUtilizationDto> districtWise = fundUtilizationService.getDistrictWiseFundUtilization(state);

        model.addAttribute("summary", summary);
        model.addAttribute("schemeWise", schemeWise);
        model.addAttribute("districtWise", districtWise);
        model.addAttribute("officerState", state);
        model.addAttribute("officerDistrict", district);

        return "district-officer/utilization";
    }

    @GetMapping("/api/analytics/fund-utilization")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getFundUtilizationJsonData(@RequestParam(value = "category", required = false) String category,
                                                                         @RequestParam(value = "state", required = false) String state,
                                                                         @RequestParam(value = "district", required = false) String district,
                                                                         Authentication authentication) {
        verifyFinancialAccess(authentication, Set.of("ROLE_FINANCE_APPROVER", "ROLE_ADMIN", "ROLE_DISTRICT_OFFICER"));

        enforceDistrictOfficerScope(authentication, state, district);

        FundUtilizationSummaryDto summary = fundUtilizationService.getSystemFundUtilizationSummary(category, state, district);
        List<SchemeFundUtilizationDto> schemeWise = fundUtilizationService.getSchemeWiseFundUtilization(category, state);
        List<StateFundUtilizationDto> stateWise = fundUtilizationService.getStateWiseFundUtilization();
        List<DistrictFundUtilizationDto> districtWise = fundUtilizationService.getDistrictWiseFundUtilization(state);

        Map<String, Object> response = new HashMap<>();
        response.put("summary", summary);
        response.put("schemeWise", schemeWise);
        response.put("stateWise", stateWise);
        response.put("districtWise", districtWise);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/finance/utilization/record")
    public String recordFundUtilization(@RequestParam("releaseId") Long releaseId,
                                        @RequestParam("utilizedAmount") BigDecimal utilizedAmount,
                                        @RequestParam(value = "remarks", required = false) String remarks,
                                        @RequestParam(value = "redirectUrl", required = false) String redirectUrl,
                                        Authentication authentication,
                                        RedirectAttributes redirectAttributes) {
        verifyFinancialAccess(authentication, Set.of("ROLE_FINANCE_APPROVER", "ROLE_ADMIN", "ROLE_DISTRICT_OFFICER"));

        try {
            String username = (authentication != null) ? authentication.getName() : "OFFICER";
            fundUtilizationService.recordFundUtilization(releaseId, utilizedAmount, remarks, username);
            redirectAttributes.addFlashAttribute("successMessage", "Fund utilization record updated successfully.");
        } catch (Exception ex) {
            log.error("Failed to record fund utilization for release ID: {}", releaseId, ex);
            redirectAttributes.addFlashAttribute("errorMessage", "Utilization update failed: " + ex.getMessage());
        }

        if (redirectUrl != null && !redirectUrl.isBlank()) {
            return "redirect:" + redirectUrl;
        }
        return "redirect:/finance/dashboard";
    }

    private void verifyFinancialAccess(Authentication authentication, Set<String> allowedRoles) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required to access fund analytics.");
        }

        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        boolean isAllowed = roles.stream().anyMatch(allowedRoles::contains);
        if (!isAllowed) {
            log.warn("Access denied for user {} with roles {} attempting to access fund analytics.", authentication.getName(), roles);
            throw new AccessDeniedException("Unauthorized access to financial utilization analytics.");
        }
    }

    private void enforceDistrictOfficerScope(Authentication authentication, String state, String district) {
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        if (roles.contains("ROLE_DISTRICT_OFFICER") && !roles.contains("ROLE_ADMIN") && !roles.contains("ROLE_FINANCE_APPROVER")) {
            Officer officer = officerRepository.findByUserEmail(authentication.getName()).orElse(null);
            if (officer != null) {
                if (state != null && !state.isBlank() && !state.equalsIgnoreCase(officer.getState())) {
                    throw new AccessDeniedException("Unauthorized to access fund analytics for state: " + state);
                }
                if (district != null && !district.isBlank() && !district.equalsIgnoreCase(officer.getDistrict())) {
                    throw new AccessDeniedException("Unauthorized to access fund analytics for district: " + district);
                }
            }
        }
    }
}
