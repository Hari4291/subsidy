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
    private final DisbursementService disbursementService;
    private final DocumentService documentService;
    private final AuditLogger auditLogger;

    public VerificationService(ApplicationRepository applicationRepository,
                               VerificationRepository verificationRepository,
                               UserRepository userRepository,
                               com.example.digital_subsidy.repository.RequiredDocumentRepository requiredDocumentRepository,
                               DisbursementService disbursementService,
                               DocumentService documentService,
                               AuditLogger auditLogger) {
        this.applicationRepository = applicationRepository;
        this.verificationRepository = verificationRepository;
        this.userRepository = userRepository;
        this.requiredDocumentRepository = requiredDocumentRepository;
        this.disbursementService = disbursementService;
        this.documentService = documentService;
        this.auditLogger = auditLogger;
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
        if ("APPROVE".equalsIgnoreCase(dto.getAction())) {
            if ("FIELD_OFFICER".equalsIgnoreCase(officerRole) || "ROLE_FIELD_OFFICER".equalsIgnoreCase(officerRole)) {
                app.setCurrentStage("DISTRICT_REVIEW");
                app.setStatus("PENDING");
            } else if ("DISTRICT_OFFICER".equalsIgnoreCase(officerRole) || "ROLE_DISTRICT_OFFICER".equalsIgnoreCase(officerRole)) {
                app.setCurrentStage("FINANCE_APPROVAL");
                app.setStatus("PENDING");
            } else if ("FINANCE_APPROVER".equalsIgnoreCase(officerRole) || "ROLE_FINANCE_APPROVER".equalsIgnoreCase(officerRole)) {
                app.setCurrentStage("DISBURSEMENT");
                app.setStatus("APPROVED");
                disbursementService.initializeDisbursement(app, officer);
            }
        } else if ("REJECT".equalsIgnoreCase(dto.getAction())) {
            app.setStatus("REJECTED");
            app.setCurrentStage("REJECTED");
            app.setRejectionReason(dto.getRemarks() != null ? dto.getRemarks() :
                    "Application rejected during " + officerRole + " review");
        } else if ("REQUEST_RE_VERIFICATION".equalsIgnoreCase(dto.getAction()) || "RE_VERIFICATION_REQUESTED".equalsIgnoreCase(dto.getAction())) {
            app.setStatus("RE_VERIFICATION_REQUIRED");
            app.setCurrentStage("FIELD_VERIFICATION");
        }

        logger.info("[VERIFICATION] App ID: {} | App Number: {} | Officer: {} | Role: {} | Action: {} | New Stage: {} | New Status: {}",
                app.getId(), app.getApplicationNumber(), officerEmail, officerRole,
                dto.getAction(), app.getCurrentStage(), app.getStatus());

        app.setUpdatedAt(LocalDateTime.now());
        Application savedApp = applicationRepository.save(app);
        logger.info("[VERIFICATION SAVED] App ID: {} | Persisted Stage: {} | Persisted Status: {}",
                savedApp.getId(), savedApp.getCurrentStage(), savedApp.getStatus());

        auditLogger.log(officerRole + "_" + dto.getAction(), "Application", app.getId(),
                "Verification processed by " + officerEmail + ". Action: " + dto.getAction() + ", Remarks: " + dto.getRemarks());

        return savedVer;
    }
}
