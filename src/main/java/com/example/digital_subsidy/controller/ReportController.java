package com.example.digital_subsidy.controller;

import com.example.digital_subsidy.repository.AuditLogRepository;
import com.example.digital_subsidy.service.ReportService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class ReportController {

    private final AuditLogRepository auditLogRepository;
    private final ReportService reportService;

    public ReportController(AuditLogRepository auditLogRepository, ReportService reportService) {
        this.auditLogRepository = auditLogRepository;
        this.reportService = reportService;
    }

    @GetMapping("/audit-logs")
    public String auditLogs(Model model) {
        model.addAttribute("auditLogs", auditLogRepository.findTop50ByOrderByTimestampDesc());
        return "admin/audit-logs";
    }

    @GetMapping("/reports")
    public String reports(Model model) {
        model.addAttribute("stats", reportService.getSystemStatistics());
        return "admin/reports";
    }
}
