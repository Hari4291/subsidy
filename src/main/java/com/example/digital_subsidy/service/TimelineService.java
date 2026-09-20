package com.example.digital_subsidy.service;

import com.example.digital_subsidy.dto.TimelineEventDto;
import com.example.digital_subsidy.exception.ResourceNotFoundException;
import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class TimelineService {

    private final ApplicationRepository applicationRepository;
    private final VerificationRepository verificationRepository;
    private final EligibilityScoreRepository eligibilityScoreRepository;
    private final DocumentRepository documentRepository;
    private final ApplicationRestrictionRepository restrictionRepository;
    private final DisbursementRepository disbursementRepository;
    private final AuditLogRepository auditLogRepository;

    public TimelineService(ApplicationRepository applicationRepository,
                           VerificationRepository verificationRepository,
                           EligibilityScoreRepository eligibilityScoreRepository,
                           DocumentRepository documentRepository,
                           ApplicationRestrictionRepository restrictionRepository,
                           DisbursementRepository disbursementRepository,
                           AuditLogRepository auditLogRepository) {
        this.applicationRepository = applicationRepository;
        this.verificationRepository = verificationRepository;
        this.eligibilityScoreRepository = eligibilityScoreRepository;
        this.documentRepository = documentRepository;
        this.restrictionRepository = restrictionRepository;
        this.disbursementRepository = disbursementRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public List<TimelineEventDto> buildApplicationTimeline(Long applicationId) {
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with ID: " + applicationId));

        List<TimelineEventDto> timeline = new ArrayList<>();
        List<Verification> verifications = verificationRepository.findByApplicationOrderByVerifiedAtDesc(app);
        Collections.reverse(verifications); // Chronological order (oldest first)

        EligibilityScore scoreRecord = eligibilityScoreRepository.findByApplication(app).orElse(null);
        List<ApplicationRestriction> restrictions = restrictionRepository.findByApplicationId(app.getId());
        ApplicationRestriction activeRestriction = restrictions.stream()
                .filter(r -> "ACTIVE".equalsIgnoreCase(r.getStatus()) || "EXPIRED".equalsIgnoreCase(r.getStatus()))
                .findFirst().orElse(null);

        Disbursement disbursement = disbursementRepository.findByApplicationIdWithMilestones(app.getId()).orElse(null);
        List<AuditLog> auditLogs = auditLogRepository.findAll().stream()
                .filter(l -> "Application".equalsIgnoreCase(l.getEntityName()) && l.getEntityId() != null && l.getEntityId().equals(app.getId()))
                .sorted(Comparator.comparing(AuditLog::getTimestamp))
                .toList();

        String currentStage = app.getCurrentStage() != null ? app.getCurrentStage().toUpperCase() : "SUBMITTED";
        String currentStatus = app.getStatus() != null ? app.getStatus().toUpperCase() : "PENDING";

        // 1. STAGE: SUBMITTED
        TimelineEventDto eSubmitted = new TimelineEventDto();
        eSubmitted.setStageCode("SUBMITTED");
        eSubmitted.setStageTitle("1. Application Submitted");
        eSubmitted.setStatus("SUBMITTED");
        eSubmitted.setEventDate(app.getCreatedAt());
        eSubmitted.setPerformedByRole("BENEFICIARY");
        eSubmitted.setPerformedByEmail(app.getBeneficiary() != null && app.getBeneficiary().getUser() != null ? app.getBeneficiary().getUser().getEmail() : "Beneficiary");
        eSubmitted.setRemarks("Application #" + app.getApplicationNumber() + " submitted for scheme: " + app.getScheme().getName());
        eSubmitted.setState("COMPLETED");
        eSubmitted.setIconClass("fa-solid fa-paper-plane");
        eSubmitted.setBadgeClass("bg-success");
        timeline.add(eSubmitted);

        // 2. STAGE: ELIGIBILITY_CHECK
        TimelineEventDto eScore = new TimelineEventDto();
        eScore.setStageCode("ELIGIBILITY_CHECK");
        eScore.setStageTitle("2. Automated Eligibility Evaluation");
        eScore.setPerformedByRole("SYSTEM");
        eScore.setPerformedByEmail("automated-engine@gov.in");

        if (scoreRecord != null) {
            eScore.setStatus(scoreRecord.isEligible() ? "ELIGIBLE" : "NEEDS_REVIEW");
            eScore.setEventDate(scoreRecord.getEvaluatedAt() != null ? scoreRecord.getEvaluatedAt() : app.getCreatedAt());
            eScore.setRemarks("Compatibility Score: " + scoreRecord.getTotalScore() + "/100 PTS. Criteria evaluation completed.");
            eScore.setState("COMPLETED");
            eScore.setIconClass("fa-solid fa-calculator");
            eScore.setBadgeClass(scoreRecord.isEligible() ? "bg-success" : "bg-warning text-dark");
        } else {
            eScore.setStatus("PENDING");
            eScore.setState("PENDING");
            eScore.setRemarks("Eligibility score calculation pending.");
            eScore.setIconClass("fa-solid fa-clock");
            eScore.setBadgeClass("bg-secondary");
        }
        timeline.add(eScore);

        // 3. STAGE: FIELD_VERIFICATION (Includes Re-verification loop & Doc Re-uploads)
        TimelineEventDto eField = new TimelineEventDto();
        eField.setStageCode("FIELD_VERIFICATION");
        eField.setStageTitle("3. Field Officer Ground Verification");

        Verification fieldVer = verifications.stream()
                .filter(v -> "FIELD_OFFICER".equalsIgnoreCase(v.getOfficerRole()) || "ROLE_FIELD_OFFICER".equalsIgnoreCase(v.getOfficerRole()))
                .reduce((first, second) -> second) // Get latest field verification
                .orElse(null);

        // Check if beneficiary re-uploaded documents
        AuditLog reuploadLog = auditLogs.stream()
                .filter(l -> "BENEFICIARY_CORRECTION_SUBMITTED".equalsIgnoreCase(l.getAction()) || "DOCUMENT_REUPLOADED".equalsIgnoreCase(l.getAction()))
                .reduce((first, second) -> second)
                .orElse(null);

        if ("RE_VERIFICATION_REQUIRED".equalsIgnoreCase(currentStatus) && "FIELD_VERIFICATION".equalsIgnoreCase(currentStage)) {
            eField.setStatus("RE_VERIFICATION_REQUIRED");
            eField.setState("RE_VERIFICATION_REQUIRED");
            eField.setIconClass("fa-solid fa-rotate-left");
            eField.setBadgeClass("bg-warning text-dark");
            eField.setPerformedByRole("DISTRICT_OFFICER");
            eField.setRemarks("District Officer requested re-verification / corrections: " + (app.getRejectionReason() != null ? app.getRejectionReason() : "Document correction required"));
            eField.setActionUrl("/beneficiary/applications/" + app.getId());
        } else if (reuploadLog != null && fieldVer == null) {
            eField.setStatus("CORRECTION_SUBMITTED");
            eField.setState("CURRENT");
            eField.setEventDate(reuploadLog.getTimestamp());
            eField.setPerformedByRole("BENEFICIARY");
            eField.setRemarks("Beneficiary uploaded replacement documents. Awaiting Field Officer re-inspection.");
            eField.setIconClass("fa-solid fa-cloud-arrow-up");
            eField.setBadgeClass("bg-info text-dark");
        } else if (fieldVer != null) {
            eField.setStatus(fieldVer.getStatus());
            eField.setEventDate(fieldVer.getVerifiedAt());
            eField.setPerformedByRole(fieldVer.getOfficerRole());
            eField.setPerformedByEmail(fieldVer.getOfficerUser() != null ? fieldVer.getOfficerUser().getEmail() : "Field Officer");
            eField.setRemarks(fieldVer.getRemarks());
            eField.setState("COMPLETED");
            eField.setIconClass("fa-solid fa-clipboard-check");
            eField.setBadgeClass("APPROVE".equalsIgnoreCase(fieldVer.getStatus()) ? "bg-success" : "bg-danger");
        } else if ("FIELD_VERIFICATION".equalsIgnoreCase(currentStage)) {
            eField.setStatus("UNDER_INSPECTION");
            eField.setState("CURRENT");
            eField.setRemarks("Assigned for ground physical inspection & document verification.");
            eField.setIconClass("fa-solid fa-spinner fa-spin");
            eField.setBadgeClass("bg-primary");
        } else {
            eField.setStatus("PENDING");
            eField.setState("PENDING");
            eField.setRemarks("Pending field verification assignment.");
            eField.setIconClass("fa-solid fa-clock");
            eField.setBadgeClass("bg-secondary");
        }
        timeline.add(eField);

        // 4. STAGE: DISTRICT_REVIEW
        TimelineEventDto eDistrict = new TimelineEventDto();
        eDistrict.setStageCode("DISTRICT_REVIEW");
        eDistrict.setStageTitle("4. District Officer Sanction & Review");

        Verification doVer = verifications.stream()
                .filter(v -> "DISTRICT_OFFICER".equalsIgnoreCase(v.getOfficerRole()) || "ROLE_DISTRICT_OFFICER".equalsIgnoreCase(v.getOfficerRole()))
                .reduce((first, second) -> second)
                .orElse(null);

        if ("FINAL_REJECTED".equalsIgnoreCase(currentStatus) || "REJECTED".equalsIgnoreCase(currentStatus)) {
            eDistrict.setStatus("FINAL_REJECTED");
            eDistrict.setState("FINAL_REJECTED");
            eDistrict.setEventDate(app.getUpdatedAt());
            eDistrict.setPerformedByRole(doVer != null ? doVer.getOfficerRole() : "DISTRICT_OFFICER");
            eDistrict.setPerformedByEmail(doVer != null && doVer.getOfficerUser() != null ? doVer.getOfficerUser().getEmail() : "District Officer");

            String rejRemarks = "FINAL REJECTION: " + (app.getRejectionReason() != null ? app.getRejectionReason() : "Application rejected");
            if (activeRestriction != null) {
                String allowedStr = activeRestriction.getReapplicationAllowedAt() != null ?
                        activeRestriction.getReapplicationAllowedAt().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy")) : "30 days";
                rejRemarks += " | Reapplication restricted for " + activeRestriction.getCooldownPeriodDays() + " days (until " + allowedStr + ").";
                eDistrict.setExtraDetails(activeRestriction);
            }
            eDistrict.setRemarks(rejRemarks);
            eDistrict.setIconClass("fa-solid fa-circle-xmark");
            eDistrict.setBadgeClass("bg-danger");
        } else if (doVer != null) {
            eDistrict.setStatus(doVer.getStatus());
            eDistrict.setEventDate(doVer.getVerifiedAt());
            eDistrict.setPerformedByRole(doVer.getOfficerRole());
            eDistrict.setPerformedByEmail(doVer.getOfficerUser() != null ? doVer.getOfficerUser().getEmail() : "District Officer");

            String remarksStr = doVer.getRemarks();
            if (app.getSanctionedAmount() != null) {
                remarksStr = "Sanctioned Grant: ₹" + app.getSanctionedAmount() + " | Remarks: " + remarksStr;
            }
            eDistrict.setRemarks(remarksStr);
            eDistrict.setState("COMPLETED");
            eDistrict.setIconClass("fa-solid fa-building-user");
            eDistrict.setBadgeClass("APPROVE".equalsIgnoreCase(doVer.getStatus()) ? "bg-success" : "bg-warning text-dark");
        } else if ("DISTRICT_REVIEW".equalsIgnoreCase(currentStage)) {
            eDistrict.setStatus("UNDER_REVIEW");
            eDistrict.setState("CURRENT");
            eDistrict.setRemarks("Application submitted to District Collectorate Desk for review.");
            eDistrict.setIconClass("fa-solid fa-spinner fa-spin");
            eDistrict.setBadgeClass("bg-primary");
        } else {
            eDistrict.setStatus("PENDING");
            eDistrict.setState("PENDING");
            eDistrict.setRemarks("Awaiting District review completion.");
            eDistrict.setIconClass("fa-solid fa-clock");
            eDistrict.setBadgeClass("bg-secondary");
        }
        timeline.add(eDistrict);

        // Stop timeline if Final Rejected
        if ("FINAL_REJECTED".equalsIgnoreCase(currentStatus) || "REJECTED".equalsIgnoreCase(currentStatus)) {
            return timeline;
        }

        // 5. STAGE: FINANCE_APPROVAL
        TimelineEventDto eFinance = new TimelineEventDto();
        eFinance.setStageCode("FINANCE_APPROVAL");
        eFinance.setStageTitle("5. Finance Treasury Sanction Approval");

        Verification finVer = verifications.stream()
                .filter(v -> "FINANCE_APPROVER".equalsIgnoreCase(v.getOfficerRole()) || "ROLE_FINANCE_APPROVER".equalsIgnoreCase(v.getOfficerRole()))
                .reduce((first, second) -> second)
                .orElse(null);

        if (finVer != null) {
            eFinance.setStatus(finVer.getStatus());
            eFinance.setEventDate(finVer.getVerifiedAt());
            eFinance.setPerformedByRole(finVer.getOfficerRole());
            eFinance.setPerformedByEmail(finVer.getOfficerUser() != null ? finVer.getOfficerUser().getEmail() : "Finance Approver");
            eFinance.setRemarks(finVer.getRemarks());
            eFinance.setState("COMPLETED");
            eFinance.setIconClass("fa-solid fa-sack-dollar");
            eFinance.setBadgeClass("APPROVE".equalsIgnoreCase(finVer.getStatus()) ? "bg-success" : "bg-danger");
        } else if ("FINANCE_APPROVAL".equalsIgnoreCase(currentStage)) {
            eFinance.setStatus("UNDER_FINANCE_REVIEW");
            eFinance.setState("CURRENT");
            eFinance.setRemarks("Forwarded to Finance Treasury for budget allocation approval.");
            eFinance.setIconClass("fa-solid fa-spinner fa-spin");
            eFinance.setBadgeClass("bg-primary");
        } else {
            eFinance.setStatus("PENDING");
            eFinance.setState("PENDING");
            eFinance.setRemarks("Awaiting Finance Treasury approval.");
            eFinance.setIconClass("fa-solid fa-clock");
            eFinance.setBadgeClass("bg-secondary");
        }
        timeline.add(eFinance);

        // 6. STAGE: DISBURSEMENT & COMPLIANCE
        TimelineEventDto eDisb = new TimelineEventDto();
        eDisb.setStageCode("DISBURSEMENT");
        eDisb.setStageTitle("6. Fund Disbursement & Compliance Milestones");

        if (disbursement != null) {
            eDisb.setStatus(disbursement.getStatus());
            eDisb.setEventDate(disbursement.getCreatedAt());
            eDisb.setPerformedByRole("FINANCE_TREASURY");
            eDisb.setRemarks("Total Sanctioned: ₹" + (disbursement.getTotalSanctionedAmount() != null ? disbursement.getTotalSanctionedAmount() : app.getSanctionedAmount()) +
                    " | Released: ₹" + (disbursement.getDisbursedAmount() != null ? disbursement.getDisbursedAmount() : "0"));

            if ("COMPLETED".equalsIgnoreCase(disbursement.getStatus()) || "COMPLETED".equalsIgnoreCase(app.getStatus())) {
                eDisb.setState("COMPLETED");
                eDisb.setIconClass("fa-solid fa-circle-check");
                eDisb.setBadgeClass("bg-success");
            } else {
                eDisb.setState("CURRENT");
                eDisb.setIconClass("fa-solid fa-hand-holding-dollar");
                eDisb.setBadgeClass("bg-info text-dark");
            }
        } else if ("DISBURSEMENT".equalsIgnoreCase(currentStage) || "APPROVED".equalsIgnoreCase(currentStatus)) {
            eDisb.setStatus("APPROVED_SCHEDULED");
            eDisb.setState("CURRENT");
            eDisb.setRemarks("Sanction approved. Milestone disbursement schedule initialized.");
            eDisb.setIconClass("fa-solid fa-coins");
            eDisb.setBadgeClass("bg-success");
        } else {
            eDisb.setStatus("PENDING");
            eDisb.setState("PENDING");
            eDisb.setRemarks("Disbursement will commence following financial sanction approval.");
            eDisb.setIconClass("fa-solid fa-clock");
            eDisb.setBadgeClass("bg-secondary");
        }
        timeline.add(eDisb);

        return timeline;
    }
}
