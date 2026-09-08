package com.example.digital_subsidy.controller;

import com.example.digital_subsidy.dto.OfficerRegistrationDto;
import com.example.digital_subsidy.model.Officer;
import com.example.digital_subsidy.repository.BeneficiaryRepository;
import com.example.digital_subsidy.repository.RegionRepository;
import com.example.digital_subsidy.service.OfficerService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin")
public class AdminOfficerController {

    private final OfficerService officerService;
    private final BeneficiaryRepository beneficiaryRepository;
    private final RegionRepository regionRepository;

    public AdminOfficerController(OfficerService officerService,
                                  BeneficiaryRepository beneficiaryRepository,
                                  RegionRepository regionRepository) {
        this.officerService = officerService;
        this.beneficiaryRepository = beneficiaryRepository;
        this.regionRepository = regionRepository;
    }

    @GetMapping("/officers")
    public String listOfficers(Model model) {
        model.addAttribute("officers", officerService.getAllOfficers());
        model.addAttribute("officerDto", new OfficerRegistrationDto());
        model.addAttribute("states", regionRepository.findDistinctStateNames());
        return "admin/officers";
    }

    @PostMapping("/officers")
    public String createOfficer(@Valid @ModelAttribute("officerDto") OfficerRegistrationDto dto,
                                BindingResult bindingResult,
                                Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("officers", officerService.getAllOfficers());
            model.addAttribute("states", regionRepository.findDistinctStateNames());
            return "admin/officers";
        }
        try {
            officerService.createOfficer(dto);
            return "redirect:/admin/officers?success=created";
        } catch (Exception ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("officers", officerService.getAllOfficers());
            model.addAttribute("states", regionRepository.findDistinctStateNames());
            return "admin/officers";
        }
    }

    @GetMapping("/beneficiaries")
    public String listBeneficiaries(Model model) {
        model.addAttribute("beneficiaries", beneficiaryRepository.findAll());
        return "admin/beneficiaries";
    }
}
