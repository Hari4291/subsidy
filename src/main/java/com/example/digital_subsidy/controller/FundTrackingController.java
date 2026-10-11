package com.example.digital_subsidy.controller;

import com.example.digital_subsidy.dto.BeneficiaryFundTrackingDto;
import com.example.digital_subsidy.dto.FundTrackingSummaryDto;
import com.example.digital_subsidy.dto.TermLevelSummaryDto;
import com.example.digital_subsidy.service.FundTrackingService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/finance")
@PreAuthorize("hasAnyRole('FINANCE_APPROVER', 'TREASURY_OFFICER', 'ADMIN')")
public class FundTrackingController {

    private static final Logger log = LoggerFactory.getLogger(FundTrackingController.class);

    private final FundTrackingService fundTrackingService;

    public FundTrackingController(FundTrackingService fundTrackingService) {
        this.fundTrackingService = fundTrackingService;
    }

    @GetMapping({"/api/fund-summary", "/fund-tracking/api/summary"})
    @ResponseBody
    public ResponseEntity<FundTrackingSummaryDto> getFundSummary(
            @RequestParam(value = "schemeId", required = false) Long schemeId,
            @RequestParam(value = "termNumber", required = false) Integer termNumber,
            @RequestParam(value = "status", required = false, defaultValue = "ALL") String status,
            @RequestParam(value = "search", required = false) String search) {

        log.info("[FUND TRACKING API] getFundSummary requested with schemeId={}, termNumber={}, status={}, search={}", schemeId, termNumber, status, search);
        FundTrackingSummaryDto summary = fundTrackingService.getFundTrackingSummary(schemeId, termNumber, status, search);
        return ResponseEntity.ok(summary);
    }

    @GetMapping({"/api/fund-tracking", "/fund-tracking/api/beneficiaries"})
    @ResponseBody
    public ResponseEntity<List<BeneficiaryFundTrackingDto>> getBeneficiaryList(
            @RequestParam(value = "schemeId", required = false) Long schemeId,
            @RequestParam(value = "termNumber", required = false) Integer termNumber,
            @RequestParam(value = "status", required = false, defaultValue = "ALL") String status,
            @RequestParam(value = "search", required = false) String search) {

        log.info("[FUND TRACKING API] getBeneficiaryList requested with schemeId={}, termNumber={}, status={}, search={}", schemeId, termNumber, status, search);
        List<BeneficiaryFundTrackingDto> list = fundTrackingService.getBeneficiaryFundTrackingList(schemeId, termNumber, status, search);
        return ResponseEntity.ok(list);
    }

    @GetMapping({"/api/fund-tracking/{applicationId}", "/fund-tracking/api/detail/{applicationId}"})
    @ResponseBody
    public ResponseEntity<BeneficiaryFundTrackingDto> getBeneficiaryDetail(@PathVariable("applicationId") Long applicationId) {
        log.info("[FUND TRACKING API] getBeneficiaryDetail requested for applicationId={}", applicationId);
        BeneficiaryFundTrackingDto dto = fundTrackingService.getBeneficiaryFundTrackingDetail(applicationId);
        return ResponseEntity.ok(dto);
    }

    @GetMapping({"/api/fund-term-summary", "/fund-tracking/api/term-summary"})
    @ResponseBody
    public ResponseEntity<List<TermLevelSummaryDto>> getTermSummaries(@RequestParam(value = "schemeId", required = false) Long schemeId) {
        log.info("[FUND TRACKING API] getTermSummaries requested for schemeId={}", schemeId);
        List<TermLevelSummaryDto> list = fundTrackingService.getTermLevelSummaries(schemeId);
        return ResponseEntity.ok(list);
    }

    @GetMapping({"/fund-tracking/export/excel", "/export/excel"})
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(value = "schemeId", required = false) Long schemeId,
            @RequestParam(value = "termNumber", required = false) Integer termNumber,
            @RequestParam(value = "status", required = false, defaultValue = "ALL") String status,
            @RequestParam(value = "search", required = false) String search) {

        try {
            log.info("[FUND TRACKING EXPORT] Exporting Excel report with schemeId={}, termNumber={}, status={}, search={}", schemeId, termNumber, status, search);
            byte[] excelBytes = fundTrackingService.exportToExcel(schemeId, termNumber, status, search);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Digital_Subsidy_Fund_Tracking_Report.xlsx");
            headers.setContentLength(excelBytes.length);

            return new ResponseEntity<>(excelBytes, headers, HttpStatus.OK);
        } catch (Exception ex) {
            log.error("Failed to generate Excel report: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping({"/fund-tracking/export/pdf", "/export/pdf"})
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(value = "schemeId", required = false) Long schemeId,
            @RequestParam(value = "termNumber", required = false) Integer termNumber,
            @RequestParam(value = "status", required = false, defaultValue = "ALL") String status,
            @RequestParam(value = "search", required = false) String search) {

        try {
            log.info("[FUND TRACKING EXPORT] Exporting PDF report with schemeId={}, termNumber={}, status={}, search={}", schemeId, termNumber, status, search);
            byte[] pdfBytes = fundTrackingService.exportToPdf(schemeId, termNumber, status, search);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=Digital_Subsidy_Fund_Tracking_Report.pdf");
            headers.setContentLength(pdfBytes.length);

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (Exception ex) {
            log.error("Failed to generate PDF report: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
