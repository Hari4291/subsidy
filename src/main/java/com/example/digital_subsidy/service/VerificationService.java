package com.example.digital_subsidy.service;

import com.example.digital_subsidy.dto.VerificationActionDto;
import com.example.digital_subsidy.exception.ResourceNotFoundException;
import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.ApplicationRepository;
import com.example.digital_subsidy.repository.UserRepository;
import com.example.digital_subsidy.repository.VerificationRepository;
import com.example.digital_subsidy.util.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class VerificationService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(VerificationService.class);

    private final ApplicationRepository applicationRepository;
    private final VerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final com.example.digital_subsidy.repository.RequiredDocumentRepository requiredDocumentRepository;
    private final com.example.digital_subsidy.repository.ApplicationRestrictionRepository applicationRestrictionRepository;
    private final DisbursementService disbursementService;
    private final DocumentService documentService;
    private final AuditLogger auditLogger;
    private final NotificationService notificationService;

    public VerificationService(ApplicationRepository applicationRepository,
                               VerificationRepository verificationRepository,
                               UserRepository userRepository,
                               com.example.digital_subsidy.repository.RequiredDocumentRepository requiredDocumentRepository,
                               com.example.digital_subsidy.repository.ApplicationRestrictionRepository applicationRestrictionRepository,
                               DisbursementService disbursementService,
                               DocumentService documentService,
                               AuditLogger auditLogger,
                               NotificationService notificationService) {
        this.applicationRepository = applicationRepository;
        this.verificationRepository = verificationRepository;
        this.userRepository = userRepository;
        this.requiredDocumentRepository = requiredDocumentRepository;
        this.applicationRestrictionRepository = applicationRestrictionRepository;
        this.disbursementService = disbursementService;
        this.documentService = documentService;
        this.auditLogger = auditLogger;
        this.notificationService = notificationService;
    }

    @Transactional
    public Verification processVerification(String officerEmail, String officerRole, VerificationActionDto dto) {
        Application app = applicationRepository.findById(dto.getApplicationId())
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with ID: " + dto.getApplicationId()));

        User officer = userRepository.findByEmail(officerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Officer user not found with email: " + officerEmail));

        // ── Process per-document verification decisions (Field Officer) ──
        if ("FIELD_OFFICER".equalsIgnoreCase(officerRole) || "ROLE_FIELD_OFFICER".equalsIgnoreCase(officerRole)) {
            Map<Long, String> statuses = dto.getDocumentVerificationStatuses();
            Map<Long, String> remarks = dto.getDocumentVerificationRemarks();

            if (statuses != null && !statuses.isEmpty()) {
                for (Map.Entry<Long, String> entry : statuses.entrySet()) {
                    Long docId = entry.getKey();
                    String status = entry.getValue();
                    if (docId == null || status == null || status.isBlank()) continue;

                    String docRemarks = (remarks != null) ? remarks.get(docId) : null;
                    documentService.updateDocumentVerification(docId, status, docRemarks, officerEmail);
                    auditLogger.log("DOCUMENT_" + status, "Document", docId,
                            "Document #" + docId + " status set to " + status + " by " + officerEmail);
                }
            }

            // ── Mandatory rejection reason check for Field Officer ──
            if ("REJECT".equalsIgnoreCase(dto.getAction()) && (dto.getRemarks() == null || dto.getRemarks().isBlank())) {
                throw new IllegalArgumentException("Rejection reason is mandatory when rejecting an application.");
            }

            // ── Approval validation: ALL configured required documents must be VERIFIED ──
            if ("APPROVE".equalsIgnoreCase(dto.getAction())) {
                List<com.example.digital_subsidy.model.RequiredDocument> reqDocs = requiredDocumentRepository.findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(app.getScheme().getId());
                List<Document> docs = documentService.getDocumentsByApplication(app.getId());

                for (com.example.digital_subsidy.model.RequiredDocument rd : reqDocs) {
                    if (rd.isRequired()) {
                        boolean isVerified = docs.stream()
                                .anyMatch(d -> (d.getDocumentType() != null && d.getDocumentType().equalsIgnoreCase(rd.getDocumentType()))
                                           && "VERIFIED".equalsIgnoreCase(d.getVerificationStatus())
                                           && "ACTIVE".equalsIgnoreCase(d.getStatus()));
                        if (!isVerified) {
                            throw new IllegalArgumentException(
                                    "Cannot approve application: Required document '" + rd.getDocumentName() + "' must be marked as VERIFIED.");
                        }
                    }
                }
            }
        }

        // ── Validation for District Officer ──
        if ("DISTRICT_OFFICER".equalsIgnoreCase(officerRole) || "ROLE_DISTRICT_OFFICER".equalsIgnoreCase(officerRole)) {
            if ("DISBURSEMENT".equalsIgnoreCase(app.getCurrentStage()) || "APPROVED".equalsIgnoreCase(app.getStatus()) || "DISBURSED".equalsIgnoreCase(app.getCurrentStage())) {
                throw new IllegalStateException("District Officer cannot modify application after final approval or disbursement.");
            }
            if ("FINANCE_APPROVAL".equalsIgnoreCase(app.getCurrentStage()) && "APPROVED".equalsIgnoreCase(app.getFinanceApprovalStatus())) {
                throw new IllegalStateException("District Officer cannot modify application after Finance approval.");
            }

            if ("REJECT".equalsIgnoreCase(dto.getAction()) && (dto.getRemarks() == null || dto.getRemarks().isBlank())) {
                throw new IllegalArgumentException("Rejection reason is mandatory when rejecting an application.");
            }
            if (("REQUEST_RE_VERIFICATION".equalsIgnoreCase(dto.getAction()) || "RE_VERIFICATION_REQUESTED".equalsIgnoreCase(dto.getAction()))
                    && (dto.getRemarks() == null || dto.getRemarks().isBlank())) {
                throw new IllegalArgumentException("Remarks are mandatory when requesting re-verification.");
            }
            if ("APPROVE".equalsIgnoreCase(dto.getAction())) {
                List<com.example.digital_subsidy.model.RequiredDocument> reqDocs = requiredDocumentRepository.findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(app.getScheme().getId());
                List<Document> docs = documentService.getDocumentsByApplication(app.getId());

                for (com.example.digital_subsidy.model.RequiredDocument rd : reqDocs) {
                    if (rd.isRequired()) {
                        boolean isVerified = docs.stream()
                                .anyMatch(d -> (d.getDocumentType() != null && d.getDocumentType().equalsIgnoreCase(rd.getDocumentType()))
                                           && "VERIFIED".equalsIgnoreCase(d.getVerificationStatus())
                                           && "ACTIVE".equalsIgnoreCase(d.getStatus()));
                        if (!isVerified) {
                            throw new IllegalArgumentException(
                                    "Cannot approve application: Required document '" + rd.getDocumentName() + "' is not marked as VERIFIED.");
                        }
                    }
                }

                // ── Adjustable Sanction Amount Rules & Validations ──
                java.math.BigDecimal reqAmt = app.getRequestedAmount() != null ? app.getRequestedAmount() : java.math.BigDecimal.ZERO;
                java.math.BigDecimal sancAmt = dto.getSanctionedAmount() != null ? dto.getSanctionedAmount() : reqAmt;

                if (sancAmt.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Sanctioned amount must be greater than zero.");
                }
                if (sancAmt.compareTo(reqAmt) > 0) {
                    throw new IllegalArgumentException("Sanctioned amount (₹" + sancAmt + ") cannot exceed requested amount (₹" + reqAmt + ").");
                }
                if (app.getScheme() != null && app.getScheme().getMaxAmount() != null && sancAmt.compareTo(app.getScheme().getMaxAmount()) > 0) {
                    throw new IllegalArgumentException("Sanctioned amount (₹" + sancAmt + ") cannot exceed scheme maximum limit of ₹" + app.getScheme().getMaxAmount() + ".");
                }

                String reason = dto.getAdjustmentReason() != null ? dto.getAdjustmentReason().trim() : (dto.getRemarks() != null ? dto.getRemarks().trim() : "");
                if (sancAmt.compareTo(reqAmt) < 0 && reason.isBlank()) {
                    throw new IllegalArgumentException("A mandatory adjustment reason must be provided when the sanctioned amount (₹" + sancAmt + ") is less than requested amount (₹" + reqAmt + ").");
                }

                app.setSanctionedAmount(sancAmt);
                app.setAmountAdjustmentReason(sancAmt.compareTo(reqAmt) < 0 ? reason : (!reason.isBlank() ? reason : "Full requested amount sanctioned"));
                app.setSanctionedBy(officerEmail);
                app.setSanctionedDate(LocalDateTime.now());
            }
        }

        // ── Validation & Handling for Finance Approver ──
        if ("FINANCE_APPROVER".equalsIgnoreCase(officerRole) || "ROLE_FINANCE_APPROVER".equalsIgnoreCase(officerRole)) {
            if ("APPROVE".equalsIgnoreCase(dto.getAction())) {
                java.math.BigDecimal sancAmt = app.getSanctionedAmount() != null ? app.getSanctionedAmount() : app.getRequestedAmount();
                if (app.getScheme() != null && app.getScheme().getRemainingBudget() != null && sancAmt.compareTo(app.getScheme().getRemainingBudget()) > 0) {
                    throw new IllegalArgumentException("Cannot approve: Sanctioned amount (₹" + sancAmt + ") exceeds remaining scheme budget of ₹" + app.getScheme().getRemainingBudget() + ".");
                }
                app.setFinanceApprovalStatus("APPROVED");
                app.setFinanceApprovedBy(officerEmail);
                app.setFinanceApprovedDate(LocalDateTime.now());
            } else if ("REJECT".equalsIgnoreCase(dto.getAction())) {
                if (dto.getRemarks() == null || dto.getRemarks().isBlank()) {
                    throw new IllegalArgumentException("Rejection reason is mandatory when rejecting an application.");
                }
                app.setFinanceApprovalStatus("REJECTED");
                app.setFinanceApprovedBy(officerEmail);
                app.setFinanceApprovedDate(LocalDateTime.now());
            }
        }

        Verification verification = new Verification();
        verification.setApplication(app);
        verification.setOfficerUser(officer);
        verification.setOfficerRole(officerRole);
        verification.setStatus(dto.getAction());
        verification.setRemarks(dto.getRemarks());
        verification.setVerifiedAt(LocalDateTime.now());

        Verification savedVer = verificationRepository.save(verification);

        // ── Process Stage & Status transitions ──
        String appNum = app.getApplicationNumber();
        String schemeName = app.getScheme() != null ? app.getScheme().getName() : "Scheme";
        String state = app.getBeneficiary() != null ? app.getBeneficiary().getState() : null;
        String district = app.getBeneficiary() != null ? app.getBeneficiary().getDistrict() : null;
        User beneficiaryUser = app.getBeneficiary() != null ? app.getBeneficiary().getUser() : null;

        if ("APPROVE".equalsIgnoreCase(dto.getAction())) {
            if ("FIELD_OFFICER".equalsIgnoreCase(officerRole) || "ROLE_FIELD_OFFICER".equalsIgnoreCase(officerRole)) {
                app.setCurrentStage("DISTRICT_REVIEW");
                app.setStatus("PENDING");
                auditLogger.log("FIELD_REVERIFICATION_COMPLETED", "Application", app.getId(),
                        "Field Officer " + officerEmail + " completed verification/re-verification.");
                auditLogger.log("SENT_TO_DISTRICT_REVIEW", "Application", app.getId(),
                        "Application #" + app.getApplicationNumber() + " sent to District Review.");

                // Notify Beneficiary
                notificationService.createNotification(beneficiaryUser, app, "FIELD_VERIFIED",
                        "Field Verification Completed",
                        "Field verification for Application #" + appNum + " was completed and forwarded for District Review.",
                        "MEDIUM", "/beneficiary/applications/" + app.getId(), "NOTIF_FIELD_VERIFIED_" + app.getId() + "_" + System.currentTimeMillis());

                // Notify District Officers
                notificationService.notifyRoleUsers("DISTRICT_OFFICER", state, district, app, "DISTRICT_REVIEW_PENDING",
                        "District Review Pending",
                        "Application #" + appNum + " (" + schemeName + ") completed field verification and requires sanction review.",
                        "HIGH", "/officer/verify/" + app.getId(), "NOTIF_DO_REVIEW_PENDING_" + app.getId() + "_" + System.currentTimeMillis());

            } else if ("DISTRICT_OFFICER".equalsIgnoreCase(officerRole) || "ROLE_DISTRICT_OFFICER".equalsIgnoreCase(officerRole)) {
                app.setCurrentStage("FINANCE_APPROVAL");
                app.setStatus("PENDING");

                // Notify Beneficiary
                notificationService.createNotification(beneficiaryUser, app, "DISTRICT_APPROVED",
                        "District Sanction Approved",
                        "District Collectorate sanctioned grant amount (₹" + (app.getSanctionedAmount() != null ? app.getSanctionedAmount() : app.getRequestedAmount()) + ") for Application #" + appNum + ".",
                        "HIGH", "/beneficiary/applications/" + app.getId(), "NOTIF_DISTRICT_APPROVED_" + app.getId() + "_" + System.currentTimeMillis());

                // Notify Finance Approvers
                notificationService.notifyRoleUsers("FINANCE_APPROVER", null, null, app, "FORWARDED_TO_FINANCE",
                        "Finance Approval Pending",
                        "Application #" + appNum + " (" + schemeName + ") sanctioned and forwarded for Treasury budget approval.",
                        "HIGH", "/finance/dashboard", "NOTIF_FINANCE_PENDING_" + app.getId() + "_" + System.currentTimeMillis());

            } else if ("FINANCE_APPROVER".equalsIgnoreCase(officerRole) || "ROLE_FINANCE_APPROVER".equalsIgnoreCase(officerRole)) {
                app.setCurrentStage("DISBURSEMENT");
                app.setStatus("APPROVED");
                disbursementService.initializeDisbursement(app, officer);

                // Notify Beneficiary
                notificationService.createNotification(beneficiaryUser, app, "FINANCE_APPROVED",
                        "Treasury Financial Sanction Granted",
                        "Finance Treasury granted financial approval for Application #" + appNum + ". Grant disbursement initialized.",
                        "HIGH", "/beneficiary/applications/" + app.getId(), "NOTIF_FINANCE_APPROVED_" + app.getId() + "_" + System.currentTimeMillis());
            }
        } else if ("REJECT".equalsIgnoreCase(dto.getAction()) || "FINAL_REJECT".equalsIgnoreCase(dto.getAction()) || "FINAL_REJECTED".equalsIgnoreCase(dto.getAction())) {
            String targetStatus = ("FINAL_REJECT".equalsIgnoreCase(dto.getAction()) || "FINAL_REJECTED".equalsIgnoreCase(dto.getAction())) ? "FINAL_REJECTED" : "REJECTED";
            app.setStatus(targetStatus);
            app.setCurrentStage("REJECTED");
            String reason = dto.getRemarks() != null ? dto.getRemarks().trim() : "Application final rejected during " + officerRole + " review";
            app.setRejectionReason(reason);

            // ── Create Rejection Cooldown Restriction ONLY on FINAL_REJECTED ──
            com.example.digital_subsidy.model.Scheme scheme = app.getScheme();
            int cooldownDays = (scheme != null && scheme.getRejectionCooldownDays() != null) ? scheme.getRejectionCooldownDays() : 30;

            if (cooldownDays > 0 && app.getBeneficiary() != null) {
                com.example.digital_subsidy.model.ApplicationRestriction restriction = new com.example.digital_subsidy.model.ApplicationRestriction();
                restriction.setBeneficiary(app.getBeneficiary());
                restriction.setApplication(app);
                restriction.setScheme(scheme);
                restriction.setRejectionReason(reason);
                restriction.setRejectedBy(officerEmail);
                restriction.setRejectedRole(officerRole);
                restriction.setRejectedAt(LocalDateTime.now());
                restriction.setCooldownPeriodDays(cooldownDays);
                restriction.setReapplicationAllowedAt(LocalDateTime.now().plusDays(cooldownDays));
                restriction.setStatus("ACTIVE");
                restriction.setCreatedAt(LocalDateTime.now());

                applicationRestrictionRepository.save(restriction);
                logger.info("[COOLDOWN RESTRICTION CREATED] App ID: {} | Beneficiary ID: {} | Cooldown: {} days | Allowed At: {}",
                        app.getId(), app.getBeneficiary().getId(), cooldownDays, restriction.getReapplicationAllowedAt());
                auditLogger.log("FINAL_REJECTION", "Application", app.getId(),
                        "Final rejection processed by " + officerEmail + " (" + officerRole + "). Reason: " + reason);
                auditLogger.log("COOLDOWN_CREATED", "ApplicationRestriction", restriction.getId(),
                        "Rejection restriction created for Beneficiary #" + app.getBeneficiary().getId() +
                        " on Scheme '" + scheme.getName() + "'. Cooldown: " + cooldownDays + " days until " + restriction.getReapplicationAllowedAt());
            }

            // Notify Beneficiary
            notificationService.createNotification(beneficiaryUser, app, "FINAL_REJECTED",
                    "Application Rejected",
                    "Application #" + appNum + " was rejected. Reason: " + reason + " (Reapplication restricted for " + cooldownDays + " days).",
                    "HIGH", "/beneficiary/applications/" + app.getId(), "NOTIF_REJECTED_" + app.getId() + "_" + System.currentTimeMillis());

        } else if ("REQUEST_RE_VERIFICATION".equalsIgnoreCase(dto.getAction()) || "RE_VERIFICATION_REQUESTED".equalsIgnoreCase(dto.getAction()) || "RE_VERIFICATION_REQUIRED".equalsIgnoreCase(dto.getAction())) {
            app.setStatus("RE_VERIFICATION_REQUIRED");
            app.setCurrentStage("FIELD_VERIFICATION");
            String reason = dto.getRemarks() != null ? dto.getRemarks().trim() : "Re-verification requested by " + officerRole;
            app.setRejectionReason(reason);
            // ABSOLUTELY NO COOLDOWN CREATED FOR RE-VERIFICATION
            auditLogger.log("RE_VERIFICATION_REQUESTED", "Application", app.getId(),
                    "Re-verification requested by " + officerEmail + " (" + officerRole + "). Reason: " + reason);

            // Notify Beneficiary
            notificationService.createNotification(beneficiaryUser, app, "RE_VERIFICATION_REQUESTED",
                    "Action Required: Re-Verification Requested",
                    "District Officer requested additional document submission / re-verification for Application #" + appNum + ". Reason: " + reason,
                    "URGENT", "/beneficiary/applications/" + app.getId(), "NOTIF_REVERIFY_BEN_" + app.getId() + "_" + System.currentTimeMillis());

            // Notify Field Officer
            if (app.getAssignedOfficer() != null && app.getAssignedOfficer().getUser() != null) {
                notificationService.createNotification(app.getAssignedOfficer().getUser(), app, "RE_VERIFICATION_REQUESTED",
                        "Re-Verification Assigned",
                        "Application #" + appNum + " returned for re-verification. Reason: " + reason,
                        "HIGH", "/field-officer/verification/" + app.getId(), "NOTIF_REVERIFY_FO_" + app.getId() + "_" + System.currentTimeMillis());
            } else {
                notificationService.notifyRoleUsers("FIELD_OFFICER", state, district, app, "RE_VERIFICATION_REQUESTED",
                        "Re-Verification Assigned",
                        "Application #" + appNum + " returned for re-verification. Reason: " + reason,
                        "HIGH", "/field-officer/dashboard", "NOTIF_REVERIFY_FO_" + app.getId() + "_" + System.currentTimeMillis());
            }
        }

        logger.info("[VERIFICATION] App ID: {} | App Number: {} | Officer: {} | Role: {} | Action: {} | New Stage: {} | New Status: {}",
                app.getId(), app.getApplicationNumber(), officerEmail, officerRole,
                dto.getAction(), app.getCurrentStage(), app.getStatus());

        app.setUpdatedAt(LocalDateTime.now());
        Application savedApp = applicationRepository.save(app);
        logger.info("[VERIFICATION SAVED] App ID: {} | Persisted Stage: {} | Persisted Status: {}",
                savedApp.getId(), savedApp.getCurrentStage(), savedApp.getStatus());

        auditLogger.log(officerRole + "_" + dto.getAction(), "Application", app.getId(),
                "Verification processed by " + officerEmail + ". Action: " + dto.getAction() +
                ", Requested: ₹" + app.getRequestedAmount() +
                ", Sanctioned: ₹" + (app.getSanctionedAmount() != null ? app.getSanctionedAmount() : app.getRequestedAmount()) +
                ", Adjustment Reason: " + app.getAmountAdjustmentReason() +
                ", Remarks: " + dto.getRemarks());

        return savedVer;
    }

    @Transactional
    public void cancelRestriction(Long restrictionId, String cancellationReason, String officerEmail) {
        com.example.digital_subsidy.model.ApplicationRestriction restriction = applicationRestrictionRepository.findById(restrictionId)
                .orElseThrow(() -> new ResourceNotFoundException("Restriction not found with ID: " + restrictionId));
        restriction.setStatus("CANCELLED");
        restriction.setCancellationReason(cancellationReason != null ? cancellationReason : "Overturned by authorized officer");
        restriction.setCancelledBy(officerEmail);
        restriction.setCancelledAt(LocalDateTime.now());
        applicationRestrictionRepository.save(restriction);
        auditLogger.log("RESTRICTION_CANCELLED", "ApplicationRestriction", restrictionId,
                "Restriction #" + restrictionId + " cancelled by " + officerEmail + ". Reason: " + cancellationReason);
    }
}
