package com.example.digital_subsidy.controller;

import com.example.digital_subsidy.repository.AuditLogRepository;
import com.example.digital_subsidy.service.ApplicationService;
import com.example.digital_subsidy.service.BeneficiaryService;
import com.example.digital_subsidy.service.ReportService;
import com.example.digital_subsidy.service.SchemeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final ReportService reportService;
    private final SchemeService schemeService;
    private final ApplicationService applicationService;
    private final AuditLogRepository auditLogRepository;
    private final com.example.digital_subsidy.service.FundUtilizationService fundUtilizationService;

    public DashboardController(ReportService reportService,
                                SchemeService schemeService,
                                ApplicationService applicationService,
                                AuditLogRepository auditLogRepository,
                                com.example.digital_subsidy.service.FundUtilizationService fundUtilizationService) {
        this.reportService = reportService;
        this.schemeService = schemeService;
        this.applicationService = applicationService;
        this.auditLogRepository = auditLogRepository;
        this.fundUtilizationService = fundUtilizationService;
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard(Model model) {
        model.addAttribute("stats", reportService.getSystemStatistics());
        model.addAttribute("schemes", schemeService.getAllSchemes());
        model.addAttribute("recentApplications", applicationService.getAllApplications());
        model.addAttribute("auditLogs", auditLogRepository.findTop50OperationalAuditLogs());

        java.util.Map<String, Object> schemeMetrics = new java.util.HashMap<>();
        schemeMetrics.put("totalSchemes", schemeService.getAllSchemes().size());
        schemeMetrics.put("activeSchemes", schemeService.countActiveSchemes());
        schemeMetrics.put("draftSchemes", schemeService.countDraftSchemes());
        schemeMetrics.put("totalCriteria", schemeService.countTotalCriteria());
        schemeMetrics.put("totalDocuments", schemeService.countTotalDocuments());
        model.addAttribute("schemeMetrics", schemeMetrics);

        model.addAttribute("utilizationSummary", fundUtilizationService.getSystemFundUtilizationSummary(null, null, null));
        model.addAttribute("schemeUtilizations", fundUtilizationService.getSchemeWiseFundUtilization(null, null));
        model.addAttribute("stateUtilizations", fundUtilizationService.getStateWiseFundUtilization());
        model.addAttribute("districtUtilizations", fundUtilizationService.getDistrictWiseFundUtilization(null));

        return "admin/dashboard";
    }
}
