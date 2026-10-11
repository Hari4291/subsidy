package com.example.digital_subsidy.controller;

import com.example.digital_subsidy.dto.BeneficiaryRegistrationDto;
import com.example.digital_subsidy.model.Region;
import com.example.digital_subsidy.repository.RegionRepository;
import com.example.digital_subsidy.service.BeneficiaryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Controller
public class AuthController {

    private final BeneficiaryService beneficiaryService;
    private final RegionRepository regionRepository;

    public AuthController(BeneficiaryService beneficiaryService, RegionRepository regionRepository) {
        this.beneficiaryService = beneficiaryService;
        this.regionRepository = regionRepository;
    }

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("registrationDto", new BeneficiaryRegistrationDto());
        model.addAttribute("states", regionRepository.findDistinctStateNames());
        return "register";
    }

    @PostMapping("/register")
    public String processRegistration(@Valid @ModelAttribute("registrationDto") BeneficiaryRegistrationDto dto,
                                      BindingResult bindingResult,
                                      Model model) {

        // Validate state-district relationship
        if (dto.getState() != null && !dto.getState().isBlank() && dto.getDistrict() != null && !dto.getDistrict().isBlank()) {
            String stateTrim = dto.getState().trim();
            String distTrim = dto.getDistrict().trim();
            List<Region> validDistricts = regionRepository.findByStateNameAndActiveTrueOrderByDistrictName(stateTrim);
            if (!validDistricts.isEmpty()) {
                boolean districtMatches = validDistricts.stream()
                        .anyMatch(r -> r.getDistrictName().equalsIgnoreCase(distTrim));
                if (!districtMatches) {
                    bindingResult.rejectValue("district", "Invalid.district", "Selected district does not belong to " + stateTrim);
                }
            }
        }

        // Custom password mismatch validation
        if (dto.getPassword() != null && dto.getConfirmPassword() != null &&
            !dto.getPassword().equals(dto.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "Mismatch.password", "Password and Confirm Password do not match");
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("states", regionRepository.findDistinctStateNames());
            return "register";
        }

        try {
            beneficiaryService.registerBeneficiary(dto);
            return "redirect:/login?registered=true";
        } catch (Exception ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("states", regionRepository.findDistinctStateNames());
            return "register";
        }
    }

    @GetMapping("/login")
    public String showLoginForm(@RequestParam(value = "error", required = false) String error,
                                @RequestParam(value = "logout", required = false) String logout,
                                @RequestParam(value = "registered", required = false) String registered,
                                jakarta.servlet.http.HttpServletRequest request,
                                Model model) {
        request.getSession(true);
        if (error != null) {
            model.addAttribute("errorMessage", "Invalid email address or password. Please verify credentials.");
        }
        if (logout != null) {
            model.addAttribute("infoMessage", "You have been successfully logged out.");
        }
        if (registered != null) {
            model.addAttribute("successMessage", "Registration successful! Please sign in with your Gmail address to access your portal.");
        }
        return "login";
    }

    @GetMapping("/api/districts/{state}")
    @ResponseBody
    public ResponseEntity<List<String>> getDistrictsByState(@PathVariable("state") String state) {
        String searchState = state != null ? state.trim() : "";
        List<String> districts = regionRepository.findByStateNameAndActiveTrueOrderByDistrictName(searchState)
                .stream()
                .map(Region::getDistrictName)
                .collect(Collectors.toList());
        return ResponseEntity.ok(districts);
    }
}
