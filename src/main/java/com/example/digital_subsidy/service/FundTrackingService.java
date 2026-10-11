package com.example.digital_subsidy.service;

import com.example.digital_subsidy.dto.*;
import com.example.digital_subsidy.exception.ResourceNotFoundException;
import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FundTrackingService {

    private static final Logger log = LoggerFactory.getLogger(FundTrackingService.class);

    private final ApplicationRepository applicationRepository;
    private final DisbursementRepository disbursementRepository;
    private final MilestoneRepository milestoneRepository;
    private final SchemeRepository schemeRepository;

    public FundTrackingService(ApplicationRepository applicationRepository,
                               DisbursementRepository disbursementRepository,
                               MilestoneRepository milestoneRepository,
                               SchemeRepository schemeRepository) {
        this.applicationRepository = applicationRepository;
        this.disbursementRepository = disbursementRepository;
        this.milestoneRepository = milestoneRepository;
        this.schemeRepository = schemeRepository;
    }

    @Transactional(readOnly = true)
    public List<BeneficiaryFundTrackingDto> getBeneficiaryFundTrackingList(Long schemeId, Integer termNumber, String status, String search) {
        List<Disbursement> disbursements = disbursementRepository.findAllWithDetails();
        List<BeneficiaryFundTrackingDto> dtoList = new ArrayList<>();

        String searchLower = search != null ? search.trim().toLowerCase() : "";
        String statusUpper = status != null ? status.trim().toUpperCase() : "ALL";

        for (Disbursement d : disbursements) {
            Application app = d.getApplication();
            if (app == null) continue;

            // Exclude rejected applications from financial tracking
            if ("REJECTED".equalsIgnoreCase(app.getStatus()) || "FINAL_REJECTED".equalsIgnoreCase(app.getStatus())) {
                continue;
            }

            // Scheme filter
            if (schemeId != null && schemeId > 0) {
                if (app.getScheme() == null || !schemeId.equals(app.getScheme().getId())) {
                    continue;
                }
            }

            // Search filter
            if (!searchLower.isBlank()) {
                String appNum = app.getApplicationNumber() != null ? app.getApplicationNumber().toLowerCase() : "";
                String benName = (app.getBeneficiary() != null && app.getBeneficiary().getUser() != null)
                        ? app.getBeneficiary().getUser().getFullName().toLowerCase()
                        : "";
                String schemeName = app.getScheme() != null ? app.getScheme().getName().toLowerCase() : "";

                if (!appNum.contains(searchLower) && !benName.contains(searchLower) && !schemeName.contains(searchLower)) {
                    continue;
                }
            }

            List<Milestone> milestones = (d.getMilestones() != null && !d.getMilestones().isEmpty())
                    ? d.getMilestones()
                    : milestoneRepository.findByDisbursementOrderByIdAsc(d);

            if (milestones == null) milestones = new ArrayList<>();
            List<Milestone> sortedMilestones = new ArrayList<>(milestones);
            sortedMilestones.sort(Comparator.comparing(m -> m.getStageNumber() != null ? m.getStageNumber() : m.getId().intValue()));

            // Term number filter
            if (termNumber != null && termNumber > 0) {
                boolean hasTerm = sortedMilestones.stream().anyMatch(m -> m.getStageNumber() != null && m.getStageNumber().equals(termNumber));
                if (!hasTerm) continue;
            }

            BeneficiaryFundTrackingDto dto = mapToBeneficiaryDto(d, app, sortedMilestones);

            // Status filter
            if (!"ALL".equals(statusUpper)) {
                boolean matchesStatus = switch (statusUpper) {
                    case "SANCTIONED" -> dto.getCompletedTerms() == 0;
                    case "RELEASED" -> dto.getRemainingTerms() == 0 && dto.getCompletedTerms() > 0;
                    case "PARTIALLY_RELEASED" -> dto.getCompletedTerms() > 0 && dto.getRemainingTerms() > 0;
                    case "PENDING_RELEASE" -> sortedMilestones.stream().anyMatch(m -> "VERIFIED".equalsIgnoreCase(m.getComplianceVerificationStatus()) && !"RELEASED".equalsIgnoreCase(m.getCompletionStatus()) && !"COMPLETED".equalsIgnoreCase(m.getCompletionStatus()));
                    case "UPCOMING", "PENDING" -> dto.getRemainingTerms() > 0;
                    case "COMPLETED" -> "COMPLETED".equalsIgnoreCase(dto.getOverallStatus()) || dto.getRemainingTerms() == 0;
                    default -> true;
                };

                if (!matchesStatus) continue;
            }

            dtoList.add(dto);
        }

        return dtoList;
    }

    @Transactional(readOnly = true)
    public FundTrackingSummaryDto getFundTrackingSummary(Long schemeId, Integer termNumber, String status, String search) {
        List<BeneficiaryFundTrackingDto> benList = getBeneficiaryFundTrackingList(schemeId, termNumber, status, search);
        FundTrackingSummaryDto summary = new FundTrackingSummaryDto();

        BigDecimal totalSanctioned = BigDecimal.ZERO;
        BigDecimal totalReleased = BigDecimal.ZERO;
        BigDecimal pendingReleaseAmount = BigDecimal.ZERO;
        Set<Long> uniqueBeneficiaries = new HashSet<>();
        long termsCompleted = 0;
        long termsRemaining = 0;
        long activeApps = 0;

        for (BeneficiaryFundTrackingDto dto : benList) {
            totalSanctioned = totalSanctioned.add(dto.getTotalSanctionedAmount());
            totalReleased = totalReleased.add(dto.getTotalReleasedAmount());
            termsCompleted += dto.getCompletedTerms();
            termsRemaining += dto.getRemainingTerms();

            if (dto.getBeneficiaryId() != null) {
                uniqueBeneficiaries.add(dto.getBeneficiaryId());
            }

            if (!"COMPLETED".equalsIgnoreCase(dto.getOverallStatus())) {
                activeApps++;
            }

            for (TermDetailDto td : dto.getTermDetails()) {
                if ("VERIFIED".equalsIgnoreCase(td.getComplianceVerificationStatus()) && !"RELEASED".equalsIgnoreCase(td.getCompletionStatus()) && !"COMPLETED".equalsIgnoreCase(td.getCompletionStatus())) {
                    pendingReleaseAmount = pendingReleaseAmount.add(td.getMilestoneAmount());
                }
            }
        }

        BigDecimal totalRemaining = totalSanctioned.subtract(totalReleased);

        BigDecimal releasedPercentage = BigDecimal.ZERO;
        BigDecimal remainingPercentage = BigDecimal.ZERO;

        if (totalSanctioned != null && totalSanctioned.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal rel = totalReleased != null ? totalReleased : BigDecimal.ZERO;
            BigDecimal rem = totalSanctioned.subtract(rel);

            releasedPercentage = rel.multiply(new BigDecimal("100"))
                    .divide(totalSanctioned, 2, RoundingMode.HALF_UP);

            remainingPercentage = rem.multiply(new BigDecimal("100"))
                    .divide(totalSanctioned, 2, RoundingMode.HALF_UP);
        }

        summary.setTotalSanctioned(totalSanctioned);
        summary.setTotalReleased(totalReleased);
        summary.setTotalRemaining(totalRemaining);
        summary.setPendingReleaseAmount(pendingReleaseAmount);
        summary.setReleasedPercentage(releasedPercentage);
        summary.setRemainingPercentage(remainingPercentage);
        summary.setTotalBeneficiaries(uniqueBeneficiaries.size());
        summary.setTermsCompleted(termsCompleted);
        summary.setTermsRemaining(termsRemaining);
        summary.setActiveApplications(activeApps);

        return summary;
    }

    @Transactional(readOnly = true)
    public BeneficiaryFundTrackingDto getBeneficiaryFundTrackingDetail(Long applicationId) {
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with ID: " + applicationId));

        Disbursement d = disbursementRepository.findByApplication(app)
                .orElseThrow(() -> new ResourceNotFoundException("Disbursement not found for application ID: " + applicationId));

        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(d);
        milestones.sort(Comparator.comparing(m -> m.getStageNumber() != null ? m.getStageNumber() : m.getId().intValue()));

        return mapToBeneficiaryDto(d, app, milestones);
    }

    @Transactional(readOnly = true)
    public List<TermLevelSummaryDto> getTermLevelSummaries(Long schemeId) {
        List<Disbursement> disbursements = disbursementRepository.findAllWithDetails();
        Map<Integer, TermLevelSummaryDto> summaryMap = new LinkedHashMap<>();

        for (Disbursement d : disbursements) {
            Application app = d.getApplication();
            if (app == null || "REJECTED".equalsIgnoreCase(app.getStatus()) || "FINAL_REJECTED".equalsIgnoreCase(app.getStatus())) {
                continue;
            }

            if (schemeId != null && schemeId > 0 && (app.getScheme() == null || !schemeId.equals(app.getScheme().getId()))) {
                continue;
            }

            List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(d);
            for (Milestone m : milestones) {
                int stage = m.getStageNumber() != null ? m.getStageNumber() : 1;
                TermLevelSummaryDto summary = summaryMap.computeIfAbsent(stage, k -> new TermLevelSummaryDto(k, "Term " + k));

                summary.setApplicationCount(summary.getApplicationCount() + 1);
                summary.setTotalSanctioned(summary.getTotalSanctioned().add(m.getMilestoneAmount() != null ? m.getMilestoneAmount() : BigDecimal.ZERO));

                boolean isReleased = "RELEASED".equalsIgnoreCase(m.getCompletionStatus()) || "COMPLETED".equalsIgnoreCase(m.getCompletionStatus());
                if (isReleased) {
                    BigDecimal released = m.getReleasedAmount() != null && m.getReleasedAmount().compareTo(BigDecimal.ZERO) > 0 ? m.getReleasedAmount() : m.getMilestoneAmount();
                    summary.setTotalReleased(summary.getTotalReleased().add(released));
                    summary.setCompletedCount(summary.getCompletedCount() + 1);
                } else {
                    summary.setTotalPending(summary.getTotalPending().add(m.getMilestoneAmount() != null ? m.getMilestoneAmount() : BigDecimal.ZERO));
                }
            }
        }

        return new ArrayList<>(summaryMap.values());
    }

    private BeneficiaryFundTrackingDto mapToBeneficiaryDto(Disbursement d, Application app, List<Milestone> sortedMilestones) {
        BeneficiaryFundTrackingDto dto = new BeneficiaryFundTrackingDto();
        dto.setApplicationId(app.getId());
        dto.setApplicationNumber(app.getApplicationNumber());

        if (app.getBeneficiary() != null) {
            dto.setBeneficiaryId(app.getBeneficiary().getId());
            if (app.getBeneficiary().getUser() != null) {
                dto.setBeneficiaryName(app.getBeneficiary().getUser().getFullName());
            } else {
                dto.setBeneficiaryName("Beneficiary #" + app.getBeneficiary().getId());
            }
            String idType = app.getBeneficiary().getIdentityType() != null ? app.getBeneficiary().getIdentityType() : "ID";
            String idNum = app.getBeneficiary().getIdentityNumber() != null ? app.getBeneficiary().getIdentityNumber() : "";
            dto.setBeneficiaryIdentity(idType + ": " + idNum);
        } else {
            dto.setBeneficiaryName("N/A");
            dto.setBeneficiaryIdentity("N/A");
        }

        if (app.getScheme() != null) {
            dto.setSchemeId(app.getScheme().getId());
            dto.setSchemeName(app.getScheme().getName());
        } else {
            dto.setSchemeName("N/A");
        }

        BigDecimal sanctioned = d.getTotalSanctionedAmount() != null ? d.getTotalSanctionedAmount() : app.getSanctionedAmount();
        if (sanctioned == null) sanctioned = BigDecimal.ZERO;

        BigDecimal released = d.getDisbursedAmount() != null ? d.getDisbursedAmount() : BigDecimal.ZERO;
        BigDecimal remaining = d.getRemainingAmount();

        dto.setTotalSanctionedAmount(sanctioned);
        dto.setTotalReleasedAmount(released);
        dto.setRemainingAmount(remaining);

        int totalTerms = sortedMilestones.size();
        int completedTerms = 0;
        Milestone currentTerm = null;

        List<TermDetailDto> termDetails = new ArrayList<>();

        for (Milestone m : sortedMilestones) {
            boolean isCompleted = "RELEASED".equalsIgnoreCase(m.getCompletionStatus()) || "COMPLETED".equalsIgnoreCase(m.getCompletionStatus());
            if (isCompleted) {
                completedTerms++;
            } else if (currentTerm == null) {
                currentTerm = m;
            }

            TermDetailDto td = new TermDetailDto();
            td.setMilestoneId(m.getId());
            td.setStageNumber(m.getStageNumber());
            td.setMilestoneName(m.getMilestoneName());
            td.setMilestoneAmount(m.getMilestoneAmount() != null ? m.getMilestoneAmount() : BigDecimal.ZERO);
            td.setReleasedAmount(m.getReleasedAmount() != null ? m.getReleasedAmount() : BigDecimal.ZERO);
            td.setRemainingAmount(td.getMilestoneAmount().subtract(td.getReleasedAmount()));
            td.setCompletionStatus(m.getCompletionStatus());
            td.setComplianceVerificationStatus(m.getComplianceVerificationStatus());
            td.setRequiredDocumentType(m.getRequiredDocumentType());
            td.setDueDate(m.getDueDate());
            td.setReleaseDate(m.getReleaseDate());
            termDetails.add(td);
        }

        dto.setTotalTerms(totalTerms);
        dto.setCompletedTerms(completedTerms);
        dto.setRemainingTerms(totalTerms - completedTerms);
        dto.setTermDetails(termDetails);

        if (currentTerm != null) {
            dto.setCurrentTermName(currentTerm.getMilestoneName());
            dto.setCurrentStageNumber(currentTerm.getStageNumber());
            dto.setCurrentTermStatus(currentTerm.getComplianceVerificationStatus());
            dto.setNextDueDate(currentTerm.getDueDate());
        } else if (!sortedMilestones.isEmpty()) {
            Milestone last = sortedMilestones.get(sortedMilestones.size() - 1);
            dto.setCurrentTermName(last.getMilestoneName());
            dto.setCurrentStageNumber(last.getStageNumber());
            dto.setCurrentTermStatus(last.getCompletionStatus());
            dto.setNextDueDate(last.getDueDate());
        }

        if (completedTerms == totalTerms && totalTerms > 0) {
            dto.setOverallStatus("COMPLETED");
        } else if (completedTerms > 0) {
            dto.setOverallStatus("PARTIALLY_RELEASED");
        } else {
            dto.setOverallStatus("SANCTIONED");
        }

        return dto;
    }

    public byte[] exportToExcel(Long schemeId, Integer termNumber, String status, String search) throws IOException {
        FundTrackingSummaryDto summary = getFundTrackingSummary(schemeId, termNumber, status, search);
        List<BeneficiaryFundTrackingDto> benList = getBeneficiaryFundTrackingList(schemeId, termNumber, status, search);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Workbook wb = workbook;

            // Styles
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle sectionHeaderStyle = wb.createCellStyle();
            Font sectionHeaderFont = wb.createFont();
            sectionHeaderFont.setBold(true);
            sectionHeaderFont.setFontHeightInPoints((short) 12);
            sectionHeaderFont.setColor(IndexedColors.DARK_BLUE.getIndex());
            sectionHeaderStyle.setFont(sectionHeaderFont);

            CellStyle titleStyle = wb.createCellStyle();
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            DataFormat df = wb.createDataFormat();
            CellStyle currencyStyle = wb.createCellStyle();
            currencyStyle.setDataFormat(df.getFormat("₹#,##0.00"));

            CellStyle numberStyle = wb.createCellStyle();
            numberStyle.setDataFormat(df.getFormat("#,##0"));

            // ==========================================
            // SHEET 1: Master Financial Report (All-in-One)
            // ==========================================
            Sheet sheet1 = workbook.createSheet("Comprehensive Financial Summary");
            int r = 0;

            // Report Main Title
            Row rowTitle = sheet1.createRow(r++);
            Cell cellTitle = rowTitle.createCell(0);
            cellTitle.setCellValue("DIGITAL SUBSIDY SYSTEM - COMPREHENSIVE FINANCIAL FUND TRACKING & DISBURSEMENT REPORT");
            cellTitle.setCellStyle(titleStyle);
            sheet1.createRow(r++); // blank row

            // SECTION 1: EXECUTIVE FINANCIAL OVERVIEW
            Row sec1Row = sheet1.createRow(r++);
            Cell sec1Cell = sec1Row.createCell(0);
            sec1Cell.setCellValue("1. EXECUTIVE FINANCIAL OVERVIEW (KPI METRICS)");
            sec1Cell.setCellStyle(sectionHeaderStyle);

            Row hRow1 = sheet1.createRow(r++);
            String[] headers1 = {"KPI Metric", "Value"};
            for (int i = 0; i < headers1.length; i++) {
                Cell c = hRow1.createCell(i);
                c.setCellValue(headers1[i]);
                c.setCellStyle(headerStyle);
            }

            Object[][] summaryData = {
                {"Total Sanctioned Amount", summary.getTotalSanctioned() != null ? summary.getTotalSanctioned().doubleValue() : 0.0, true},
                {"Total Released Amount", summary.getTotalReleased() != null ? summary.getTotalReleased().doubleValue() : 0.0, true},
                {"Total Remaining Amount", summary.getTotalRemaining() != null ? summary.getTotalRemaining().doubleValue() : 0.0, true},
                {"Pending Release Amount", summary.getPendingReleaseAmount() != null ? summary.getPendingReleaseAmount().doubleValue() : 0.0, true},
                {"Total Unique Beneficiaries", summary.getTotalBeneficiaries(), false},
                {"Completed Terms Count", summary.getTermsCompleted(), false},
                {"Remaining Terms Count", summary.getTermsRemaining(), false},
                {"Active Applications Count", summary.getActiveApplications(), false}
            };

            for (Object[] rowData : summaryData) {
                Row row = sheet1.createRow(r++);
                row.createCell(0).setCellValue(String.valueOf(rowData[0]));
                Cell valCell = row.createCell(1);
                boolean isCurrency = (boolean) rowData[2];
                if (isCurrency) {
                    valCell.setCellValue(((Number) rowData[1]).doubleValue());
                    valCell.setCellStyle(currencyStyle);
                } else {
                    valCell.setCellValue(((Number) rowData[1]).longValue());
                    valCell.setCellStyle(numberStyle);
                }
            }

            sheet1.createRow(r++); // blank
            sheet1.createRow(r++); // blank

            // SECTION 2: BENEFICIARY-WISE FUND & TERM SANCTION SUMMARY
            Row sec2Row = sheet1.createRow(r++);
            Cell sec2Cell = sec2Row.createCell(0);
            sec2Cell.setCellValue("2. BENEFICIARY-WISE FUND & TERM SANCTION SUMMARY");
            sec2Cell.setCellStyle(sectionHeaderStyle);

            Row hRow2 = sheet1.createRow(r++);
            String[] headers2 = {
                "Application ID", "Beneficiary Name", "Identity (Aadhaar/ID)", "Scheme Name", 
                "Total Sanctioned Amount (₹)", "Total Released Amount (₹)", "Remaining Balance (₹)", 
                "Total Terms Sanctioned", "Completed Terms", "Remaining Terms", 
                "Current Term Stage", "Current Status", "Next Compliance Due Date", "Overall Status"
            };
            for (int i = 0; i < headers2.length; i++) {
                Cell c = hRow2.createCell(i);
                c.setCellValue(headers2[i]);
                c.setCellStyle(headerStyle);
            }

            for (BeneficiaryFundTrackingDto dto : benList) {
                Row row = sheet1.createRow(r++);
                int col = 0;
                row.createCell(col++).setCellValue(dto.getApplicationNumber());
                row.createCell(col++).setCellValue(dto.getBeneficiaryName());
                row.createCell(col++).setCellValue(dto.getBeneficiaryIdentity());
                row.createCell(col++).setCellValue(dto.getSchemeName());

                Cell sancCell = row.createCell(col++);
                sancCell.setCellValue(dto.getTotalSanctionedAmount().doubleValue());
                sancCell.setCellStyle(currencyStyle);

                Cell relCell = row.createCell(col++);
                relCell.setCellValue(dto.getTotalReleasedAmount().doubleValue());
                relCell.setCellStyle(currencyStyle);

                Cell remCell = row.createCell(col++);
                remCell.setCellValue(dto.getRemainingAmount().doubleValue());
                remCell.setCellStyle(currencyStyle);

                Cell totTermsCell = row.createCell(col++);
                totTermsCell.setCellValue(dto.getTotalTerms());
                totTermsCell.setCellStyle(numberStyle);

                Cell compTermsCell = row.createCell(col++);
                compTermsCell.setCellValue(dto.getCompletedTerms());
                compTermsCell.setCellStyle(numberStyle);

                Cell remTermsCell = row.createCell(col++);
                remTermsCell.setCellValue(dto.getRemainingTerms());
                remTermsCell.setCellStyle(numberStyle);

                row.createCell(col++).setCellValue(dto.getCurrentTermName() != null ? dto.getCurrentTermName() : "N/A");
                row.createCell(col++).setCellValue(dto.getCurrentTermStatus() != null ? dto.getCurrentTermStatus() : "N/A");
                row.createCell(col++).setCellValue(dto.getNextDueDate() != null ? dto.getNextDueDate().toString() : "N/A");
                row.createCell(col++).setCellValue(dto.getOverallStatus());
            }

            sheet1.createRow(r++); // blank
            sheet1.createRow(r++); // blank

            // SECTION 3: DETAILED TERM-BY-TERM SANCTION & DISBURSEMENT BREAKDOWN
            Row sec3Row = sheet1.createRow(r++);
            Cell sec3Cell = sec3Row.createCell(0);
            sec3Cell.setCellValue("3. DETAILED TERM-BY-TERM SANCTION & DISBURSEMENT BREAKDOWN");
            sec3Cell.setCellStyle(sectionHeaderStyle);

            Row hRow3 = sheet1.createRow(r++);
            String[] headers3 = {
                "Application ID", "Beneficiary Name", "Scheme Name", "Term Number", 
                "Term Name / Milestone", "Term Sanctioned Amount (₹)", "Term Released Amount (₹)", 
                "Term Remaining Balance (₹)", "Completion Status", "Verification Status", 
                "Compliance Due Date", "Release Date & Time"
            };
            for (int i = 0; i < headers3.length; i++) {
                Cell c = hRow3.createCell(i);
                c.setCellValue(headers3[i]);
                c.setCellStyle(headerStyle);
            }

            for (BeneficiaryFundTrackingDto dto : benList) {
                for (TermDetailDto td : dto.getTermDetails()) {
                    Row row = sheet1.createRow(r++);
                    int col = 0;
                    row.createCell(col++).setCellValue(dto.getApplicationNumber());
                    row.createCell(col++).setCellValue(dto.getBeneficiaryName());
                    row.createCell(col++).setCellValue(dto.getSchemeName());

                    Cell termNoCell = row.createCell(col++);
                    termNoCell.setCellValue(td.getStageNumber() != null ? td.getStageNumber() : 1);
                    termNoCell.setCellStyle(numberStyle);

                    row.createCell(col++).setCellValue(td.getMilestoneName());

                    Cell termSancCell = row.createCell(col++);
                    termSancCell.setCellValue(td.getMilestoneAmount().doubleValue());
                    termSancCell.setCellStyle(currencyStyle);

                    Cell termRelCell = row.createCell(col++);
                    termRelCell.setCellValue(td.getReleasedAmount().doubleValue());
                    termRelCell.setCellStyle(currencyStyle);

                    Cell termRemCell = row.createCell(col++);
                    termRemCell.setCellValue(td.getRemainingAmount().doubleValue());
                    termRemCell.setCellStyle(currencyStyle);

                    row.createCell(col++).setCellValue(td.getCompletionStatus() != null ? td.getCompletionStatus() : "PENDING");
                    row.createCell(col++).setCellValue(td.getComplianceVerificationStatus() != null ? td.getComplianceVerificationStatus() : "PENDING");
                    row.createCell(col++).setCellValue(td.getDueDate() != null ? td.getDueDate().toString() : "N/A");
                    row.createCell(col++).setCellValue(td.getReleaseDate() != null ? td.getReleaseDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "N/A");
                }
            }

            for (int i = 0; i < headers2.length; i++) {
                sheet1.autoSizeColumn(i);
            }

            // ==========================================
            // SHEET 2: Dedicated Beneficiary Fund Summary
            // ==========================================
            Sheet sheet2 = workbook.createSheet("Beneficiary Fund Summary");
            r = 0;
            Row bhRow2 = sheet2.createRow(r++);
            for (int i = 0; i < headers2.length; i++) {
                Cell c = bhRow2.createCell(i);
                c.setCellValue(headers2[i]);
                c.setCellStyle(headerStyle);
            }

            for (BeneficiaryFundTrackingDto dto : benList) {
                Row row = sheet2.createRow(r++);
                int col = 0;
                row.createCell(col++).setCellValue(dto.getApplicationNumber());
                row.createCell(col++).setCellValue(dto.getBeneficiaryName());
                row.createCell(col++).setCellValue(dto.getBeneficiaryIdentity());
                row.createCell(col++).setCellValue(dto.getSchemeName());

                Cell sancCell = row.createCell(col++);
                sancCell.setCellValue(dto.getTotalSanctionedAmount().doubleValue());
                sancCell.setCellStyle(currencyStyle);

                Cell relCell = row.createCell(col++);
                relCell.setCellValue(dto.getTotalReleasedAmount().doubleValue());
                relCell.setCellStyle(currencyStyle);

                Cell remCell = row.createCell(col++);
                remCell.setCellValue(dto.getRemainingAmount().doubleValue());
                remCell.setCellStyle(currencyStyle);

                Cell totTermsCell = row.createCell(col++);
                totTermsCell.setCellValue(dto.getTotalTerms());
                totTermsCell.setCellStyle(numberStyle);

                Cell compTermsCell = row.createCell(col++);
                compTermsCell.setCellValue(dto.getCompletedTerms());
                compTermsCell.setCellStyle(numberStyle);

                Cell remTermsCell = row.createCell(col++);
                remTermsCell.setCellValue(dto.getRemainingTerms());
                remTermsCell.setCellStyle(numberStyle);

                row.createCell(col++).setCellValue(dto.getCurrentTermName() != null ? dto.getCurrentTermName() : "N/A");
                row.createCell(col++).setCellValue(dto.getCurrentTermStatus() != null ? dto.getCurrentTermStatus() : "N/A");
                row.createCell(col++).setCellValue(dto.getNextDueDate() != null ? dto.getNextDueDate().toString() : "N/A");
                row.createCell(col++).setCellValue(dto.getOverallStatus());
            }

            for (int i = 0; i < headers2.length; i++) {
                sheet2.autoSizeColumn(i);
            }

            // ==========================================
            // SHEET 3: Dedicated Term-wise Breakdown
            // ==========================================
            Sheet sheet3 = workbook.createSheet("Term-wise Breakdown");
            r = 0;
            Row thRow3 = sheet3.createRow(r++);
            for (int i = 0; i < headers3.length; i++) {
                Cell c = thRow3.createCell(i);
                c.setCellValue(headers3[i]);
                c.setCellStyle(headerStyle);
            }

            for (BeneficiaryFundTrackingDto dto : benList) {
                for (TermDetailDto td : dto.getTermDetails()) {
                    Row row = sheet3.createRow(r++);
                    int col = 0;
                    row.createCell(col++).setCellValue(dto.getApplicationNumber());
                    row.createCell(col++).setCellValue(dto.getBeneficiaryName());
                    row.createCell(col++).setCellValue(dto.getSchemeName());

                    Cell termNoCell = row.createCell(col++);
                    termNoCell.setCellValue(td.getStageNumber() != null ? td.getStageNumber() : 1);
                    termNoCell.setCellStyle(numberStyle);

                    row.createCell(col++).setCellValue(td.getMilestoneName());

                    Cell termSancCell = row.createCell(col++);
                    termSancCell.setCellValue(td.getMilestoneAmount().doubleValue());
                    termSancCell.setCellStyle(currencyStyle);

                    Cell termRelCell = row.createCell(col++);
                    termRelCell.setCellValue(td.getReleasedAmount().doubleValue());
                    termRelCell.setCellStyle(currencyStyle);

                    Cell termRemCell = row.createCell(col++);
                    termRemCell.setCellValue(td.getRemainingAmount().doubleValue());
                    termRemCell.setCellStyle(currencyStyle);

                    row.createCell(col++).setCellValue(td.getCompletionStatus() != null ? td.getCompletionStatus() : "PENDING");
                    row.createCell(col++).setCellValue(td.getComplianceVerificationStatus() != null ? td.getComplianceVerificationStatus() : "PENDING");
                    row.createCell(col++).setCellValue(td.getDueDate() != null ? td.getDueDate().toString() : "N/A");
                    row.createCell(col++).setCellValue(td.getReleaseDate() != null ? td.getReleaseDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "N/A");
                }
            }

            for (int i = 0; i < headers3.length; i++) {
                sheet3.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] exportToPdf(Long schemeId, Integer termNumber, String status, String search) throws IOException {
        FundTrackingSummaryDto summary = getFundTrackingSummary(schemeId, termNumber, status, search);
        List<BeneficiaryFundTrackingDto> benList = getBeneficiaryFundTrackingList(schemeId, termNumber, status, search);

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                cs.beginText();
                cs.newLineAtOffset(40, 750);
                cs.showText("DIGITAL SUBSIDY SYSTEM - FINANCIAL FUND TRACKING REPORT");
                cs.endText();

                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                cs.beginText();
                cs.newLineAtOffset(40, 735);
                cs.showText("Generated On: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss")) + " | Role: Finance Approver");
                cs.endText();

                // Section 1: Executive Financial Summary
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 11);
                cs.beginText();
                cs.newLineAtOffset(40, 710);
                cs.showText("1. EXECUTIVE FINANCIAL SUMMARY");
                cs.endText();

                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                int y = 690;
                String[] summaryLines = {
                    "Total Sanctioned: Rs. " + summary.getTotalSanctioned().toPlainString() + "  |  Total Released: Rs. " + summary.getTotalReleased().toPlainString(),
                    "Total Remaining: Rs. " + summary.getTotalRemaining().toPlainString() + "  |  Pending Release: Rs. " + summary.getPendingReleaseAmount().toPlainString(),
                    "Total Beneficiaries: " + summary.getTotalBeneficiaries() + "  |  Active Applications: " + summary.getActiveApplications(),
                    "Completed Terms: " + summary.getTermsCompleted() + "  |  Remaining Terms: " + summary.getTermsRemaining()
                };

                for (String line : summaryLines) {
                    cs.beginText();
                    cs.newLineAtOffset(50, y);
                    cs.showText(line);
                    cs.endText();
                    y -= 15;
                }

                // Section 2: Beneficiary-wise Fund Tracking Table
                y -= 10;
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 11);
                cs.beginText();
                cs.newLineAtOffset(40, y);
                cs.showText("2. BENEFICIARY-WISE FUND TRACKING");
                cs.endText();

                y -= 20;
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 8);
                cs.beginText();
                cs.newLineAtOffset(40, y);
                cs.showText("App ID        Beneficiary              Sanctioned      Released       Remaining      Terms(C/R)   Status");
                cs.endText();

                y -= 12;
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);
                for (BeneficiaryFundTrackingDto dto : benList) {
                    if (y < 50) break; // page limit check for quick PDF preview
                    String name = dto.getBeneficiaryName().length() > 20 ? dto.getBeneficiaryName().substring(0, 18) + ".." : dto.getBeneficiaryName();
                    String line = String.format("%-12s %-22s Rs.%-10.0f Rs.%-10.0f Rs.%-10.0f %d/%d          %s",
                            dto.getApplicationNumber(),
                            name,
                            dto.getTotalSanctionedAmount().doubleValue(),
                            dto.getTotalReleasedAmount().doubleValue(),
                            dto.getRemainingAmount().doubleValue(),
                            dto.getCompletedTerms(),
                            dto.getRemainingTerms(),
                            dto.getOverallStatus());

                    cs.beginText();
                    cs.newLineAtOffset(40, y);
                    cs.showText(line);
                    cs.endText();
                    y -= 14;
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }
}
