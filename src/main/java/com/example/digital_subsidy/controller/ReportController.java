package com.example.digital_subsidy.controller;

import com.example.digital_subsidy.dto.*;
import com.example.digital_subsidy.model.Officer;
import com.example.digital_subsidy.model.Scheme;
import com.example.digital_subsidy.repository.AuditLogRepository;
import com.example.digital_subsidy.repository.OfficerRepository;
import com.example.digital_subsidy.repository.SchemeRepository;
import com.example.digital_subsidy.service.ReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class ReportController {

    private final AuditLogRepository auditLogRepository;
    private final ReportService reportService;
    private final SchemeRepository schemeRepository;
    private final OfficerRepository officerRepository;

    public ReportController(AuditLogRepository auditLogRepository,
                            ReportService reportService,
                            SchemeRepository schemeRepository,
                            OfficerRepository officerRepository) {
        this.auditLogRepository = auditLogRepository;
        this.reportService = reportService;
        this.schemeRepository = schemeRepository;
        this.officerRepository = officerRepository;
    }

    @GetMapping("/admin/audit-logs")
    public String auditLogs(Model model, Authentication authentication) {
        checkReportAuthorization(authentication, null, null);
        model.addAttribute("auditLogs", auditLogRepository.findTop50OperationalAuditLogs());
        return "admin/audit-logs";
    }

    @GetMapping({"/reports", "/admin/reports"})
    public String viewReports(@RequestParam(value = "reportType", defaultValue = "FUND_UTILIZATION") String reportType,
                              @RequestParam(value = "schemeId", required = false) Long schemeId,
                              @RequestParam(value = "category", required = false) String category,
                              @RequestParam(value = "state", required = false) String state,
                              @RequestParam(value = "district", required = false) String district,
                              @RequestParam(value = "status", required = false) String status,
                              @RequestParam(value = "complianceStatus", required = false) String complianceStatus,
                              Authentication authentication,
                              Model model) {
        
        // Scope enforce for District Officers
        if (authentication != null) {
            Set<String> roles = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toSet());
            if (roles.contains("ROLE_DISTRICT_OFFICER") && !roles.contains("ROLE_ADMIN") && !roles.contains("ROLE_FINANCE_APPROVER")) {
                Officer officer = officerRepository.findByUserEmail(authentication.getName()).orElse(null);
                if (officer != null) {
                    if (state == null || state.isBlank()) state = officer.getState();
                    if (district == null || district.isBlank()) district = officer.getDistrict();
                }
            }
        }

        checkReportAuthorization(authentication, state, district);

        List<Scheme> schemes = schemeRepository.findAll();
        model.addAttribute("schemes", schemes);
        model.addAttribute("reportType", reportType);
        model.addAttribute("selectedSchemeId", schemeId);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("selectedState", state);
        model.addAttribute("selectedDistrict", district);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedComplianceStatus", complianceStatus);
        model.addAttribute("stats", reportService.getSystemStatistics());

        switch (reportType.toUpperCase()) {
            case "REGIONAL_FUND":
                List<RegionalFundReportDto> regionalReport = reportService.generateRegionalFundReport(state, district, schemeId);
                model.addAttribute("reportData", regionalReport);
                break;
            case "DISBURSEMENT":
                List<DisbursementReportDto> disbursementReport = reportService.generateDisbursementReport(schemeId, state, district, status);
                model.addAttribute("reportData", disbursementReport);
                break;
            case "COMPLIANCE":
                List<ComplianceReportDto> complianceReport = reportService.generateComplianceReport(schemeId, state, district, complianceStatus);
                model.addAttribute("reportData", complianceReport);
                break;
            case "LIFECYCLE":
                ApplicationLifecycleReportDto lifecycleReport = reportService.generateApplicationLifecycleReport(state, district, schemeId);
                model.addAttribute("lifecycleData", lifecycleReport);
                break;
            case "FUND_UTILIZATION":
            default:
                List<SchemeFundUtilizationDto> fundReport = reportService.generateFundUtilizationReport(schemeId, category, state, district);
                model.addAttribute("reportData", fundReport);
                break;
        }

        return "reports/index";
    }

    @GetMapping("/reports/export")
    public ResponseEntity<byte[]> exportReport(@RequestParam(value = "reportType", defaultValue = "FUND_UTILIZATION") String reportType,
                                               @RequestParam(value = "format", defaultValue = "csv") String format,
                                               @RequestParam(value = "schemeId", required = false) Long schemeId,
                                               @RequestParam(value = "category", required = false) String category,
                                               @RequestParam(value = "state", required = false) String state,
                                               @RequestParam(value = "district", required = false) String district,
                                               @RequestParam(value = "status", required = false) String status,
                                               @RequestParam(value = "complianceStatus", required = false) String complianceStatus,
                                               Authentication authentication) throws IOException {

        // Scope enforce for District Officers
        if (authentication != null) {
            Set<String> roles = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toSet());
            if (roles.contains("ROLE_DISTRICT_OFFICER") && !roles.contains("ROLE_ADMIN") && !roles.contains("ROLE_FINANCE_APPROVER")) {
                Officer officer = officerRepository.findByUserEmail(authentication.getName()).orElse(null);
                if (officer != null) {
                    if (state == null || state.isBlank()) state = officer.getState();
                    if (district == null || district.isBlank()) district = officer.getDistrict();
                }
            }
        }

        checkReportAuthorization(authentication, state, district);

        List<?> dataList;
        switch (reportType.toUpperCase()) {
            case "REGIONAL_FUND":
                dataList = reportService.generateRegionalFundReport(state, district, schemeId);
                break;
            case "DISBURSEMENT":
                dataList = reportService.generateDisbursementReport(schemeId, state, district, status);
                break;
            case "COMPLIANCE":
                dataList = reportService.generateComplianceReport(schemeId, state, district, complianceStatus);
                break;
            case "LIFECYCLE":
                dataList = List.of(reportService.generateApplicationLifecycleReport(state, district, schemeId));
                break;
            case "FUND_UTILIZATION":
            default:
                dataList = reportService.generateFundUtilizationReport(schemeId, category, state, district);
                break;
        }

        byte[] content;
        String fileName = reportType.toLowerCase() + "_report." + format.toLowerCase();
        MediaType mediaType;

        if ("pdf".equalsIgnoreCase(format)) {
            content = reportService.exportReportToPdf(reportType, dataList);
            mediaType = MediaType.APPLICATION_PDF;
        } else {
            content = reportService.exportReportToCsv(reportType, dataList);
            mediaType = MediaType.parseMediaType("text/csv");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDispositionFormData("attachment", fileName);
        headers.setContentLength(content.length);

        return new ResponseEntity<>(content, headers, HttpStatus.OK);
    }

    private void checkReportAuthorization(Authentication authentication, String requestedState, String requestedDistrict) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required to access reports.");
        }
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        if (roles.contains("ROLE_BENEFICIARY") || roles.contains("ROLE_FIELD_OFFICER")) {
            throw new AccessDeniedException("Unauthorized: Beneficiaries and Field Officers cannot access administrative reports.");
        }

        if (!roles.contains("ROLE_ADMIN") && !roles.contains("ROLE_FINANCE_APPROVER") && !roles.contains("ROLE_DISTRICT_OFFICER")) {
            throw new AccessDeniedException("Unauthorized role for report access.");
        }

        if (roles.contains("ROLE_DISTRICT_OFFICER") && !roles.contains("ROLE_ADMIN") && !roles.contains("ROLE_FINANCE_APPROVER")) {
            Officer officer = officerRepository.findByUserEmail(authentication.getName()).orElse(null);
            if (officer != null) {
                if (requestedState != null && !requestedState.isBlank() && !requestedState.equalsIgnoreCase(officer.getState())) {
                    throw new AccessDeniedException("IDOR Prevention: District Officer assigned to " + officer.getState() + " cannot access reports for state: " + requestedState);
                }
                if (requestedDistrict != null && !requestedDistrict.isBlank() && !requestedDistrict.equalsIgnoreCase(officer.getDistrict())) {
                    throw new AccessDeniedException("IDOR Prevention: District Officer assigned to " + officer.getDistrict() + " cannot access reports for district: " + requestedDistrict);
                }
            }
        }
    }
}

