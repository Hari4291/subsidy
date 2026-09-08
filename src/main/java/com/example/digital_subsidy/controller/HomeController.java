package com.example.digital_subsidy.controller;

import com.example.digital_subsidy.service.ReportService;
import com.example.digital_subsidy.service.SchemeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class HomeController {

    private final SchemeService schemeService;
    private final ReportService reportService;

    public HomeController(SchemeService schemeService, ReportService reportService) {
        this.schemeService = schemeService;
        this.reportService = reportService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("schemes", schemeService.getAllActiveSchemes());
        model.addAttribute("stats", reportService.getSystemStatistics());
        return "index";
    }

    @GetMapping("/schemes")
    public String publicSchemes(Model model) {
        model.addAttribute("schemes", schemeService.getAllActiveSchemes());
        return "schemes";
    }

    @GetMapping("/schemes/{id}")
    public String schemeDetails(@PathVariable("id") Long id, Model model) {
        model.addAttribute("scheme", schemeService.getSchemeById(id));
        return "scheme-details";
    }

    @GetMapping("/how-it-works")
    public String howItWorks() {
        return "how-it-works";
    }

    @GetMapping("/eligibility")
    public String publicEligibility(Model model) {
        model.addAttribute("schemes", schemeService.getAllActiveSchemes());
        return "eligibility-public";
    }

    @GetMapping("/help")
    public String helpPage() {
        return "help";
    }
}
