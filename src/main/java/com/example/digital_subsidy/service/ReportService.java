package com.example.digital_subsidy.service;

import com.example.digital_subsidy.dto.*;
import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final SchemeRepository schemeRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final ApplicationRepository applicationRepository;
    private final DisbursementRepository disbursementRepository;
    private final MilestoneRepository milestoneRepository;
    private final DisbursementReleaseRepository releaseRepository;
    private final FundUtilizationRepository utilizationRepository;
    private final FundUtilizationService fundUtilizationService;

    public ReportService(SchemeRepository schemeRepository,
                         BeneficiaryRepository beneficiaryRepository,
                         ApplicationRepository applicationRepository,
                         DisbursementRepository disbursementRepository,
                         MilestoneRepository milestoneRepository,
                         DisbursementReleaseRepository releaseRepository,
                         FundUtilizationRepository utilizationRepository,
                         FundUtilizationService fundUtilizationService) {
        this.schemeRepository = schemeRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.applicationRepository = applicationRepository;
        this.disbursementRepository = disbursementRepository;
        this.milestoneRepository = milestoneRepository;
        this.releaseRepository = releaseRepository;
        this.utilizationRepository = utilizationRepository;
        this.fundUtilizationService = fundUtilizationService;
    }

    public Map<String, Object> getSystemStatistics() {
        Map<String, Object> stats = new HashMap<>();

        long activeSchemes = schemeRepository.countByStatus("ACTIVE");
        long registeredBeneficiaries = beneficiaryRepository.count();
        long applicationsProcessed = applicationRepository.count();
        long approvedApplications = applicationRepository.countByStatus("APPROVED");
        long pendingApplications = applicationRepository.countByStatus("PENDING");
        long rejectedApplications = applicationRepository.countByStatus("REJECTED");

        BigDecimal totalApprovedGrant = applicationRepository.sumTotalApprovedGrantAmount();
        BigDecimal totalDisbursed = disbursementRepository.sumTotalDisbursedAmount();

        stats.put("activeSchemes", activeSchemes);
        stats.put("registeredBeneficiaries", registeredBeneficiaries);
        stats.put("applicationsProcessed", applicationsProcessed);
        stats.put("approvedApplications", approvedApplications);
        stats.put("pendingApplications", pendingApplications);
        stats.put("rejectedApplications", rejectedApplications);
        stats.put("totalApprovedGrant", totalApprovedGrant != null ? totalApprovedGrant : BigDecimal.ZERO);
        stats.put("totalDisbursed", totalDisbursed != null ? totalDisbursed : BigDecimal.ZERO);

        return stats;
    }

    @Transactional(readOnly = true)
    public List<SchemeFundUtilizationDto> generateFundUtilizationReport(Long schemeId, String category, String state, String district) {
        List<SchemeFundUtilizationDto> list = fundUtilizationService.getSchemeWiseFundUtilization(category, state);
        if (schemeId != null) {
            list = list.stream().filter(s -> schemeId.equals(s.getSchemeId())).collect(Collectors.toList());
        }
        return list;
    }

    @Transactional(readOnly = true)
    public List<RegionalFundReportDto> generateRegionalFundReport(String state, String district, Long schemeId) {
        List<Application> apps = applicationRepository.findAll();
        if (state != null && !state.isBlank()) {
            apps = apps.stream()
                    .filter(a -> a.getBeneficiary() != null && state.equalsIgnoreCase(a.getBeneficiary().getState()))
                    .collect(Collectors.toList());
        }
        if (district != null && !district.isBlank()) {
            apps = apps.stream()
                    .filter(a -> a.getBeneficiary() != null && district.equalsIgnoreCase(a.getBeneficiary().getDistrict()))
                    .collect(Collectors.toList());
        }
        if (schemeId != null) {
            apps = apps.stream()
                    .filter(a -> a.getScheme() != null && schemeId.equals(a.getScheme().getId()))
                    .collect(Collectors.toList());
        }

        Map<String, List<Application>> grouped = apps.stream()
                .filter(a -> a.getBeneficiary() != null && a.getBeneficiary().getState() != null && a.getBeneficiary().getDistrict() != null && a.getScheme() != null)
                .collect(Collectors.groupingBy(a -> a.getBeneficiary().getState() + "|" + a.getBeneficiary().getDistrict() + "|" + a.getScheme().getName()));

        List<RegionalFundReportDto> list = new ArrayList<>();
        for (Map.Entry<String, List<Application>> entry : grouped.entrySet()) {
            String[] parts = entry.getKey().split("\\|");
            String st = parts[0];
            String dist = parts[1];
            String schemeName = parts[2];
            List<Application> appGroup = entry.getValue();

            RegionalFundReportDto dto = new RegionalFundReportDto();
            dto.setState(st);
            dto.setDistrict(dist);
            dto.setSchemeName(schemeName);
            dto.setApplicationCount(appGroup.size());

            BigDecimal sanctioned = BigDecimal.ZERO;
            for (Application a : appGroup) {
                if ("APPROVED".equalsIgnoreCase(a.getStatus()) || "COMPLETED".equalsIgnoreCase(a.getCurrentStage()) || "DISBURSEMENT".equalsIgnoreCase(a.getCurrentStage())) {
                    if (a.getSanctionedAmount() != null) {
                        sanctioned = sanctioned.add(a.getSanctionedAmount());
                    }
                }
            }
            dto.setSanctionedAmount(sanctioned);

            BigDecimal released = releaseRepository.findAll().stream()
                    .filter(r -> "SUCCESS".equalsIgnoreCase(r.getStatus()))
                    .filter(r -> r.getApplication() != null && r.getApplication().getBeneficiary() != null && r.getApplication().getScheme() != null)
                    .filter(r -> st.equalsIgnoreCase(r.getApplication().getBeneficiary().getState()) && dist.equalsIgnoreCase(r.getApplication().getBeneficiary().getDistrict()) && schemeName.equalsIgnoreCase(r.getApplication().getScheme().getName()))
                    .map(DisbursementRelease::getReleasedAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            dto.setReleasedAmount(released);

            BigDecimal utilized = utilizationRepository.sumTotalUtilizedAmountByStateAndDistrict(st, dist);
            if (utilized == null || (utilized.compareTo(BigDecimal.ZERO) == 0 && released.compareTo(BigDecimal.ZERO) > 0)) {
                utilized = released;
            }
            dto.setUtilizedAmount(utilized);

            BigDecimal remaining = sanctioned.subtract(released);
            if (remaining.compareTo(BigDecimal.ZERO) < 0) remaining = BigDecimal.ZERO;
            dto.setRemainingAmount(remaining);

            double pct = 0.0;
            if (sanctioned.compareTo(BigDecimal.ZERO) > 0) {
                pct = utilized.multiply(new BigDecimal("100")).divide(sanctioned, 4, RoundingMode.HALF_UP).doubleValue();
            }
            dto.setUtilizationPercentage(pct);

            list.add(dto);
        }
        return list;
    }

    @Transactional(readOnly = true)
    public List<DisbursementReportDto> generateDisbursementReport(Long schemeId, String state, String district, String status) {
        List<Disbursement> disbursements = disbursementRepository.findAllWithDetails();
        if (schemeId != null) {
            disbursements = disbursements.stream()
                    .filter(d -> d.getApplication() != null && d.getApplication().getScheme() != null && schemeId.equals(d.getApplication().getScheme().getId()))
                    .collect(Collectors.toList());
        }
        if (state != null && !state.isBlank()) {
            disbursements = disbursements.stream()
                    .filter(d -> d.getApplication() != null && d.getApplication().getBeneficiary() != null && state.equalsIgnoreCase(d.getApplication().getBeneficiary().getState()))
                    .collect(Collectors.toList());
        }
        if (district != null && !district.isBlank()) {
            disbursements = disbursements.stream()
                    .filter(d -> d.getApplication() != null && d.getApplication().getBeneficiary() != null && district.equalsIgnoreCase(d.getApplication().getBeneficiary().getDistrict()))
                    .collect(Collectors.toList());
        }
        if (status != null && !status.isBlank()) {
            disbursements = disbursements.stream()
                    .filter(d -> status.equalsIgnoreCase(d.getStatus()))
                    .collect(Collectors.toList());
        }

        List<DisbursementReportDto> list = new ArrayList<>();
        for (Disbursement d : disbursements) {
            Application app = d.getApplication();
            DisbursementReportDto dto = new DisbursementReportDto();
            dto.setApplicationId(app != null ? app.getId() : null);
            dto.setApplicationNumber(app != null ? app.getApplicationNumber() : "N/A");
            dto.setBeneficiaryName(app != null && app.getBeneficiary() != null && app.getBeneficiary().getUser() != null ? app.getBeneficiary().getUser().getFullName() : "N/A");
            dto.setBeneficiaryIdentity(app != null && app.getBeneficiary() != null ? (app.getBeneficiary().getIdentityType() + ": " + app.getBeneficiary().getIdentityNumber()) : "N/A");
            dto.setSchemeName(app != null && app.getScheme() != null ? app.getScheme().getName() : "N/A");
            dto.setSanctionedAmount(d.getTotalSanctionedAmount());
            dto.setReleasedAmount(d.getDisbursedAmount());
            dto.setRemainingAmount(d.getRemainingAmount());
            dto.setDisbursementStatus(d.getStatus());

            List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(d);
            dto.setTotalTerms(milestones.size());
            int relTerms = (int) milestones.stream().filter(m -> "RELEASED".equalsIgnoreCase(m.getCompletionStatus()) || "COMPLETED".equalsIgnoreCase(m.getCompletionStatus())).count();
            dto.setReleasedTerms(relTerms);
            dto.setPendingTerms(milestones.size() - relTerms);

            Milestone nextPending = milestones.stream()
                    .filter(m -> !"RELEASED".equalsIgnoreCase(m.getCompletionStatus()) && !"COMPLETED".equalsIgnoreCase(m.getCompletionStatus()))
                    .findFirst()
                    .orElse(null);
            dto.setNextPendingTerm(nextPending != null ? nextPending.getMilestoneName() : "All Terms Released");

            list.add(dto);
        }
        return list;
    }

    @Transactional(readOnly = true)
    public List<ComplianceReportDto> generateComplianceReport(Long schemeId, String state, String district, String complianceStatus) {
        List<Milestone> milestones = milestoneRepository.findAll();

        if (schemeId != null) {
            milestones = milestones.stream()
                    .filter(m -> m.getDisbursement() != null && m.getDisbursement().getApplication() != null && m.getDisbursement().getApplication().getScheme() != null && schemeId.equals(m.getDisbursement().getApplication().getScheme().getId()))
                    .collect(Collectors.toList());
        }
        if (state != null && !state.isBlank()) {
            milestones = milestones.stream()
                    .filter(m -> m.getDisbursement() != null && m.getDisbursement().getApplication() != null && m.getDisbursement().getApplication().getBeneficiary() != null && state.equalsIgnoreCase(m.getDisbursement().getApplication().getBeneficiary().getState()))
                    .collect(Collectors.toList());
        }
        if (district != null && !district.isBlank()) {
            milestones = milestones.stream()
                    .filter(m -> m.getDisbursement() != null && m.getDisbursement().getApplication() != null && m.getDisbursement().getApplication().getBeneficiary() != null && district.equalsIgnoreCase(m.getDisbursement().getApplication().getBeneficiary().getDistrict()))
                    .collect(Collectors.toList());
        }
        if (complianceStatus != null && !complianceStatus.isBlank()) {
            milestones = milestones.stream()
                    .filter(m -> complianceStatus.equalsIgnoreCase(m.getComplianceVerificationStatus()))
                    .collect(Collectors.toList());
        }

        List<ComplianceReportDto> list = new ArrayList<>();
        for (Milestone m : milestones) {
            Application app = m.getDisbursement() != null ? m.getDisbursement().getApplication() : null;
            ComplianceReportDto dto = new ComplianceReportDto();
            dto.setMilestoneId(m.getId());
            dto.setApplicationId(app != null ? app.getId() : null);
            dto.setApplicationNumber(app != null ? app.getApplicationNumber() : "N/A");
            dto.setBeneficiaryName(app != null && app.getBeneficiary() != null && app.getBeneficiary().getUser() != null ? app.getBeneficiary().getUser().getFullName() : "N/A");
            dto.setSchemeName(app != null && app.getScheme() != null ? app.getScheme().getName() : "N/A");
            dto.setMilestoneName(m.getMilestoneName());
            dto.setStageNumber(m.getStageNumber() != null ? m.getStageNumber() : 1);
            dto.setDueDate(m.getDueDate());
            dto.setComplianceStatus(m.getComplianceVerificationStatus() != null ? m.getComplianceVerificationStatus() : "PENDING");
            dto.setVerificationStatus(m.getCompletionStatus() != null ? m.getCompletionStatus() : "PENDING");
            dto.setOverdueFlag(Boolean.TRUE.equals(m.getOverdueFlag()));
            dto.setVerifiedBy(m.getVerifiedBy() != null ? m.getVerifiedBy() : "N/A");
            dto.setVerifiedDate(m.getVerifiedDate());

            list.add(dto);
        }
        return list;
    }

    @Transactional(readOnly = true)
    public ApplicationLifecycleReportDto generateApplicationLifecycleReport(String state, String district, Long schemeId) {
        List<Application> apps = applicationRepository.findAll();

        if (state != null && !state.isBlank()) {
            apps = apps.stream()
                    .filter(a -> a.getBeneficiary() != null && state.equalsIgnoreCase(a.getBeneficiary().getState()))
                    .collect(Collectors.toList());
        }
        if (district != null && !district.isBlank()) {
            apps = apps.stream()
                    .filter(a -> a.getBeneficiary() != null && district.equalsIgnoreCase(a.getBeneficiary().getDistrict()))
                    .collect(Collectors.toList());
        }
        if (schemeId != null) {
            apps = apps.stream()
                    .filter(a -> a.getScheme() != null && schemeId.equals(a.getScheme().getId()))
                    .collect(Collectors.toList());
        }

        ApplicationLifecycleReportDto dto = new ApplicationLifecycleReportDto();
        dto.setTotalApplications(apps.size());

        for (Application a : apps) {
            String stage = a.getCurrentStage() != null ? a.getCurrentStage().toUpperCase() : "";
            String status = a.getStatus() != null ? a.getStatus().toUpperCase() : "";

            if ("SUBMITTED".equals(stage) || "SUBMITTED".equals(status)) dto.setSubmittedCount(dto.getSubmittedCount() + 1);
            if ("ELIGIBILITY_CHECK".equals(stage)) dto.setEligibilityCheckCount(dto.getEligibilityCheckCount() + 1);
            if ("ELIGIBLE".equals(stage) || "ELIGIBLE".equals(status)) dto.setEligibleCount(dto.getEligibleCount() + 1);
            if ("FIELD_VERIFICATION".equals(stage)) dto.setFieldVerificationCount(dto.getFieldVerificationCount() + 1);
            if ("DISTRICT_REVIEW".equals(stage)) dto.setDistrictReviewCount(dto.getDistrictReviewCount() + 1);
            if ("FINANCE_APPROVAL".equals(stage)) dto.setFinanceApprovalCount(dto.getFinanceApprovalCount() + 1);
            if ("APPROVED".equals(status)) dto.setApprovedCount(dto.getApprovedCount() + 1);
            if ("DISBURSEMENT".equals(stage)) dto.setDisbursementCount(dto.getDisbursementCount() + 1);
            if ("COMPLIANCE".equals(stage)) dto.setComplianceCount(dto.getComplianceCount() + 1);
            if ("COMPLETED".equals(stage) || "COMPLETED".equals(status)) dto.setCompletedCount(dto.getCompletedCount() + 1);
            if ("REJECTED".equals(status) || "REJECTED".equals(stage)) dto.setRejectedCount(dto.getRejectedCount() + 1);
            if ("RE_VERIFICATION_REQUIRED".equals(status) || "RE_VERIFICATION_REQUIRED".equals(stage)) dto.setReVerificationRequiredCount(dto.getReVerificationRequiredCount() + 1);
        }

        return dto;
    }

    public byte[] exportReportToCsv(String reportType, List<?> dataList) {
        StringBuilder sb = new StringBuilder();

        if ("FUND_UTILIZATION".equalsIgnoreCase(reportType)) {
            sb.append("Scheme Name,Category,Allocated Amount,Sanctioned Amount,Released Amount,Utilized Amount,Remaining Amount,Application Count,Utilization Percentage\n");
            for (Object obj : dataList) {
                if (obj instanceof SchemeFundUtilizationDto s) {
                    sb.append("\"").append(s.getSchemeName()).append("\",");
                    sb.append("\"").append(s.getCategory()).append("\",");
                    sb.append(s.getAllocatedAmount()).append(",");
                    sb.append(s.getSanctionedAmount()).append(",");
                    sb.append(s.getReleasedAmount()).append(",");
                    sb.append(s.getUtilizedAmount()).append(",");
                    sb.append(s.getRemainingAmount()).append(",");
                    sb.append(s.getApplicationCount()).append(",");
                    sb.append(s.getUtilizationPercentage()).append("%\n");
                }
            }
        } else if ("REGIONAL_FUND".equalsIgnoreCase(reportType)) {
            sb.append("State,District,Scheme Name,Application Count,Sanctioned Amount,Released Amount,Utilized Amount,Remaining Amount,Utilization Percentage\n");
            for (Object obj : dataList) {
                if (obj instanceof RegionalFundReportDto r) {
                    sb.append("\"").append(r.getState()).append("\",");
                    sb.append("\"").append(r.getDistrict()).append("\",");
                    sb.append("\"").append(r.getSchemeName()).append("\",");
                    sb.append(r.getApplicationCount()).append(",");
                    sb.append(r.getSanctionedAmount()).append(",");
                    sb.append(r.getReleasedAmount()).append(",");
                    sb.append(r.getUtilizedAmount()).append(",");
                    sb.append(r.getRemainingAmount()).append(",");
                    sb.append(r.getUtilizationPercentage()).append("%\n");
                }
            }
        } else if ("DISBURSEMENT".equalsIgnoreCase(reportType)) {
            sb.append("Application Number,Beneficiary,Scheme,Sanctioned Amount,Released Amount,Remaining Amount,Total Terms,Released Terms,Pending Terms,Next Pending Term,Status\n");
            for (Object obj : dataList) {
                if (obj instanceof DisbursementReportDto d) {
                    sb.append("\"").append(d.getApplicationNumber()).append("\",");
                    sb.append("\"").append(d.getBeneficiaryName()).append("\",");
                    sb.append("\"").append(d.getSchemeName()).append("\",");
                    sb.append(d.getSanctionedAmount()).append(",");
                    sb.append(d.getReleasedAmount()).append(",");
                    sb.append(d.getRemainingAmount()).append(",");
                    sb.append(d.getTotalTerms()).append(",");
                    sb.append(d.getReleasedTerms()).append(",");
                    sb.append(d.getPendingTerms()).append(",");
                    sb.append("\"").append(d.getNextPendingTerm()).append("\",");
                    sb.append("\"").append(d.getDisbursementStatus()).append("\"\n");
                }
            }
        } else if ("COMPLIANCE".equalsIgnoreCase(reportType)) {
            sb.append("Application Number,Beneficiary,Scheme,Milestone,Stage,Due Date,Compliance Status,Verification Status,Overdue Flag,Verified By,Verified Date\n");
            for (Object obj : dataList) {
                if (obj instanceof ComplianceReportDto c) {
                    sb.append("\"").append(c.getApplicationNumber()).append("\",");
                    sb.append("\"").append(c.getBeneficiaryName()).append("\",");
                    sb.append("\"").append(c.getSchemeName()).append("\",");
                    sb.append("\"").append(c.getMilestoneName()).append("\",");
                    sb.append(c.getStageNumber()).append(",");
                    sb.append(c.getDueDate()).append(",");
                    sb.append("\"").append(c.getComplianceStatus()).append("\",");
                    sb.append("\"").append(c.getVerificationStatus()).append("\",");
                    sb.append(c.isOverdueFlag()).append(",");
                    sb.append("\"").append(c.getVerifiedBy()).append("\",");
                    sb.append(c.getVerifiedDate()).append("\n");
                }
            }
        } else {
            sb.append("Report Type,Generated At\n");
            sb.append("\"").append(reportType).append("\",\"").append(java.time.LocalDateTime.now()).append("\"\n");
        }

        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public byte[] exportReportToPdf(String reportType, List<?> dataList) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                contentStream.beginText();
                contentStream.newLineAtOffset(50, 780);
                contentStream.showText("Government Digital Subsidy Platform - " + reportType.toUpperCase() + " REPORT");
                contentStream.endText();

                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                contentStream.beginText();
                contentStream.newLineAtOffset(50, 760);
                contentStream.showText("Generated Date: " + java.time.LocalDateTime.now().toString());
                contentStream.endText();

                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 11);
                contentStream.beginText();
                contentStream.newLineAtOffset(50, 730);
                contentStream.showText("Total Records: " + (dataList != null ? dataList.size() : 0));
                contentStream.endText();

                int y = 700;
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                if (dataList != null) {
                    for (int i = 0; i < Math.min(dataList.size(), 30); i++) {
                        Object item = dataList.get(i);
                        contentStream.beginText();
                        contentStream.newLineAtOffset(50, y);
                        contentStream.showText((i + 1) + ". " + item.toString().substring(0, Math.min(item.toString().length(), 90)));
                        contentStream.endText();
                        y -= 20;
                    }
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
