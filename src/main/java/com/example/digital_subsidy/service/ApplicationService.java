package com.example.digital_subsidy.service;

import com.example.digital_subsidy.dto.ApplicationSubmissionDto;
import com.example.digital_subsidy.dto.EligibilityCheckRequestDto;
import com.example.digital_subsidy.dto.EligibilityCheckResponseDto;
import com.example.digital_subsidy.exception.ResourceNotFoundException;
import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.ApplicationEligibilityAnswerRepository;
import com.example.digital_subsidy.repository.ApplicationRepository;
import com.example.digital_subsidy.repository.EligibilityCriteriaRepository;
import com.example.digital_subsidy.repository.EligibilityScoreRepository;
import com.example.digital_subsidy.repository.SchemeRepository;
import com.example.digital_subsidy.util.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final SchemeRepository schemeRepository;
    private final EligibilityScoreRepository eligibilityScoreRepository;
    private final EligibilityService eligibilityService;
    private final com.example.digital_subsidy.repository.DocumentRepository documentRepository;
    private final ApplicationEligibilityAnswerRepository answerRepository;
    private final EligibilityCriteriaRepository criteriaRepository;
    private final com.example.digital_subsidy.repository.ApplicationRestrictionRepository applicationRestrictionRepository;
    private final AuditLogger auditLogger;
    private final NotificationService notificationService;
    private final PriorityService priorityService;

    public ApplicationService(ApplicationRepository applicationRepository,
                              SchemeRepository schemeRepository,
                              EligibilityScoreRepository eligibilityScoreRepository,
                              EligibilityService eligibilityService,
                              com.example.digital_subsidy.repository.DocumentRepository documentRepository,
                              ApplicationEligibilityAnswerRepository answerRepository,
                              EligibilityCriteriaRepository criteriaRepository,
                              com.example.digital_subsidy.repository.ApplicationRestrictionRepository applicationRestrictionRepository,
                              AuditLogger auditLogger,
                              NotificationService notificationService,
                              PriorityService priorityService) {
        this.applicationRepository = applicationRepository;
        this.schemeRepository = schemeRepository;
        this.eligibilityScoreRepository = eligibilityScoreRepository;
        this.eligibilityService = eligibilityService;
        this.documentRepository = documentRepository;
        this.answerRepository = answerRepository;
        this.criteriaRepository = criteriaRepository;
        this.applicationRestrictionRepository = applicationRestrictionRepository;
        this.auditLogger = auditLogger;
        this.notificationService = notificationService;
        this.priorityService = priorityService;
    }

    @Transactional
    public List<com.example.digital_subsidy.model.ApplicationRestriction> getActiveRestrictions(Long beneficiaryId, Long schemeId) {
        List<com.example.digital_subsidy.model.ApplicationRestriction> activeRestrictions = 
                applicationRestrictionRepository.findActiveRestrictions(beneficiaryId, schemeId);

        List<com.example.digital_subsidy.model.ApplicationRestriction> validActive = new java.util.ArrayList<>();
        for (com.example.digital_subsidy.model.ApplicationRestriction r : activeRestrictions) {
            if (r.isActive()) {
                validActive.add(r);
            } else if ("ACTIVE".equalsIgnoreCase(r.getStatus()) && r.getReapplicationAllowedAt() != null && !LocalDateTime.now().isBefore(r.getReapplicationAllowedAt())) {
                r.setStatus("EXPIRED");
                applicationRestrictionRepository.save(r);
                auditLogger.log("COOLDOWN_EXPIRED", "ApplicationRestriction", r.getId(),
                        "Cooldown expired for restriction #" + r.getId() + " on Scheme ID " + schemeId);
            }
        }

        if (validActive.isEmpty()) {
            // Check all applications for this beneficiary & scheme
            List<Application> existingApps = applicationRepository.findByBeneficiaryId(beneficiaryId);
            for (Application app : existingApps) {
                if (app.getScheme() != null && app.getScheme().getId().equals(schemeId) &&
                        ("FINAL_REJECTED".equalsIgnoreCase(app.getStatus()) || "REJECTED".equalsIgnoreCase(app.getStatus()))) {
                    
                    // If a restriction record already exists for this application in DB, skip fallback creation
                    List<com.example.digital_subsidy.model.ApplicationRestriction> existingRes =
                            applicationRestrictionRepository.findByApplicationId(app.getId());
                    if (!existingRes.isEmpty()) {
                        continue;
                    }

                    Scheme scheme = app.getScheme();
                    int cooldownDays = (scheme != null && scheme.getRejectionCooldownDays() != null)
                            ? scheme.getRejectionCooldownDays() : 30;
                    
                    if (cooldownDays <= 0) {
                        continue;
                    }

                    LocalDateTime rejectionTime = app.getUpdatedAt() != null ? app.getUpdatedAt() : app.getCreatedAt();
                    LocalDateTime allowedTime = rejectionTime.plusDays(cooldownDays);
                    
                    if (LocalDateTime.now().isBefore(allowedTime)) {
                        com.example.digital_subsidy.model.ApplicationRestriction restriction = new com.example.digital_subsidy.model.ApplicationRestriction();
                        restriction.setBeneficiary(app.getBeneficiary());
                        restriction.setApplication(app);
                        restriction.setScheme(scheme);
                        restriction.setRejectionReason(app.getRejectionReason() != null ? app.getRejectionReason() : "Application final rejected");
                        restriction.setRejectedBy(app.getSanctionedBy() != null ? app.getSanctionedBy() : "Officer");
                        restriction.setRejectedRole("OFFICER");
                        restriction.setRejectedAt(rejectionTime);
                        restriction.setCooldownPeriodDays(cooldownDays);
                        restriction.setReapplicationAllowedAt(allowedTime);
                        restriction.setStatus("ACTIVE");
                        restriction.setCreatedAt(rejectionTime);

                        applicationRestrictionRepository.save(restriction);
                        validActive.add(restriction);
                        break;
                    }
                }
            }
        }
        return validActive;
    }

    @Transactional
    public Application submitApplication(Beneficiary beneficiary, ApplicationSubmissionDto dto) {
        Scheme scheme = schemeRepository.findById(dto.getSchemeId())
                .orElseThrow(() -> new ResourceNotFoundException("Scheme not found with ID: " + dto.getSchemeId()));

        // ── 1. Reapplication Restriction Check ──
        List<com.example.digital_subsidy.model.ApplicationRestriction> activeRestrictions = 
                getActiveRestrictions(beneficiary.getId(), scheme.getId());
        
        for (com.example.digital_subsidy.model.ApplicationRestriction r : activeRestrictions) {
            if (r.getReapplicationAllowedAt() != null && LocalDateTime.now().isBefore(r.getReapplicationAllowedAt())) {
                String allowedDateStr = r.getReapplicationAllowedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm"));
                auditLogger.log("REAPPLICATION_BLOCKED", "ApplicationRestriction", r.getId(),
                        "Reapplication blocked for Beneficiary #" + beneficiary.getId() + " on Scheme '" + scheme.getName() + "' until " + allowedDateStr);
                throw new IllegalStateException(
                        "Your previous application for this scheme was rejected. Reapplication is restricted until " + allowedDateStr + 
                        ". Rejection Reason: " + r.getRejectionReason());
            } else {
                r.setStatus("EXPIRED");
                applicationRestrictionRepository.save(r);
                auditLogger.log("COOLDOWN_EXPIRED", "ApplicationRestriction", r.getId(),
                        "Cooldown expired for restriction #" + r.getId() + " on Scheme '" + scheme.getName() + "'");
            }
        }

        // ── 2. Duplicate Active Application Check ──
        List<Application> existingApps = applicationRepository.findByBeneficiaryId(beneficiary.getId());
        boolean hasActiveApp = existingApps.stream().anyMatch(app -> 
                app.getScheme().getId().equals(scheme.getId()) &&
                !"REJECTED".equalsIgnoreCase(app.getStatus()) &&
                !"FINAL_REJECTED".equalsIgnoreCase(app.getStatus()) &&
                !"REJECTED".equalsIgnoreCase(app.getCurrentStage()) &&
                !"COMPLETED".equalsIgnoreCase(app.getStatus()));
        
        if (hasActiveApp) {
            throw new IllegalStateException("An active application already exists for this scheme.");
        }

        // Evaluate eligibility score automatically upon submission
        EligibilityCheckRequestDto evalReq = new EligibilityCheckRequestDto();
        evalReq.setSchemeId(scheme.getId());
        evalReq.setAnnualIncome(dto.getAnnualIncome());
        evalReq.setRequiredDocsUploaded(true);
        // Carry criterion answers into the evaluation for accurate re-scoring
        if (dto.getCriterionAnswers() != null) {
            evalReq.setCriterionAnswers(dto.getCriterionAnswers());
        }

        EligibilityCheckResponseDto evalRes = eligibilityService.evaluateEligibility(beneficiary, evalReq);

        Application application = new Application();
        application.setApplicationNumber("APP-" + System.currentTimeMillis() % 1000000);
        application.setBeneficiary(beneficiary);
        application.setScheme(scheme);
        application.setRequestedAmount(dto.getRequestedAmount());
        application.setEligibilityScore(evalRes.getTotalScore());
        application.setCurrentStage("FIELD_VERIFICATION");
        application.setStatus("PENDING");
        application.setCreatedAt(LocalDateTime.now());
        application.setUpdatedAt(LocalDateTime.now());

        Application savedApp = applicationRepository.save(application);

        // Save Eligibility Score record
        EligibilityScore scoreRecord = new EligibilityScore();
        scoreRecord.setApplication(savedApp);
        scoreRecord.setTotalScore(evalRes.getTotalScore());
        scoreRecord.setEligible(evalRes.isEligible());
        scoreRecord.setPassedCriteriaJson(evalRes.getStatusMessage());
        eligibilityScoreRepository.save(scoreRecord);

        // ── Calculate Configurable Priority Score & Level ──
        savedApp = priorityService.calculatePriority(savedApp);

        // ── Save answer snapshots (historical record for Field Officers) ──
        Map<Long, String> answers = dto.getCriterionAnswers();
        Map<Long, String> questionSnapshots = dto.getCriterionQuestionSnapshots();
        Map<Long, Integer> displayOrders = dto.getCriterionDisplayOrders();

        if (answers != null && !answers.isEmpty()) {
            for (Map.Entry<Long, String> entry : answers.entrySet()) {
                Long criterionId = entry.getKey();
                String submittedAnswer = entry.getValue();
                if (submittedAnswer == null || submittedAnswer.isBlank()) continue;

                EligibilityCriteria liveC = criteriaRepository.findById(criterionId).orElse(null);
                String questionText = (questionSnapshots != null) ? questionSnapshots.get(criterionId) : null;
                if (questionText == null || questionText.isBlank()) {
                    questionText = (liveC != null) ? liveC.getQuestionText() : "Question #" + criterionId;
                }

                Integer displayOrder = (displayOrders != null) ? displayOrders.get(criterionId) : null;

                // Find criterion result from evaluation response to get pass/fail and earned score
                String evalResultStr = "PASS";
                Integer scoreContrib = 0;
                if (evalRes != null && evalRes.getCriterionResults() != null) {
                    for (var res : evalRes.getCriterionResults()) {
                        if (res.getCriterionName() != null && liveC != null && res.getCriterionName().equalsIgnoreCase(liveC.getCriterionName())) {
                            evalResultStr = res.isPassed() ? "PASS" : "FAIL";
                            scoreContrib = res.getEarnedPoints();
                            break;
                        }
                    }
                }

                ApplicationEligibilityAnswer ans = new ApplicationEligibilityAnswer();
                ans.setApplication(savedApp);
                ans.setSchemeId(scheme.getId());
                ans.setCriterionId(criterionId);
                ans.setQuestionTextSnapshot(questionText);
                ans.setSubmittedAnswer(submittedAnswer);
                ans.setDisplayOrder(displayOrder);
                ans.setEvaluationResult(evalResultStr);
                ans.setScoreContribution(scoreContrib);
                answerRepository.save(ans);
            }
        }

        // ── Link unlinked beneficiary documents to this application ──
        // Documents uploaded during the eligibility check have application_id = null.
        // Link only the latest ACTIVE unlinked doc per documentType; archive older unlinked duplicates.
        List<Document> beneficiaryDocs = documentRepository.findByBeneficiaryId(beneficiary.getId());
        Map<String, Document> latestUnlinkedMap = new java.util.LinkedHashMap<>();
        for (Document doc : beneficiaryDocs) {
            if (doc.getApplication() == null && "ACTIVE".equalsIgnoreCase(doc.getStatus())) {
                String type = doc.getDocumentType() != null ? doc.getDocumentType().toUpperCase() : "OTHER";
                Document prev = latestUnlinkedMap.get(type);
                if (prev != null) {
                    prev.setStatus("ARCHIVED");
                    documentRepository.save(prev);
                }
                latestUnlinkedMap.put(type, doc);
            }
        }
        for (Document doc : latestUnlinkedMap.values()) {
            doc.setApplication(savedApp);
            documentRepository.save(doc);
        }

        auditLogger.log("APPLICATION_SUBMITTED", "Application", savedApp.getId(),
                "Application submitted: " + savedApp.getApplicationNumber() + " for Scheme: " + scheme.getName());

        // Notify Beneficiary
        notificationService.createNotification(beneficiary.getUser(), savedApp, "APP_SUBMITTED",
                "Application Submitted Successfully",
                "Your application #" + savedApp.getApplicationNumber() + " for scheme '" + scheme.getName() + "' was submitted and queued for Field Verification.",
                "MEDIUM", "/beneficiary/applications/" + savedApp.getId(), "NOTIF_SUBMITTED_" + savedApp.getId());

        // Notify Field Officers in District/State
        notificationService.notifyRoleUsers("FIELD_OFFICER", beneficiary.getState(), beneficiary.getDistrict(), savedApp,
                "ASSIGNED_TO_FO", "New Application Submitted for Field Verification",
                "Application #" + savedApp.getApplicationNumber() + " (" + scheme.getName() + ") submitted in " + beneficiary.getDistrict() + " district.",
                "MEDIUM", "/field-officer/dashboard", "NOTIF_FO_ASSIGNED_" + savedApp.getId());

        return savedApp;
    }

    public List<Application> getApplicationsForBeneficiary(Long beneficiaryId) {
        return applicationRepository.findByBeneficiaryIdOrderByIdDesc(beneficiaryId);
    }

    public List<Application> getApplicationsByStage(String stage) {
        return applicationRepository.findByCurrentStage(stage);
    }

    public List<Application> getAllApplications() {
        return applicationRepository.findAll();
    }

    public Application getApplicationById(Long id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with ID: " + id));
    }

    public Application findApplicationByIdOrNumber(String identifier) {
        if (identifier == null || identifier.isBlank() || "null".equalsIgnoreCase(identifier) || "undefined".equalsIgnoreCase(identifier)) {
            return null;
        }
        try {
            Long id = Long.parseLong(identifier.trim());
            Optional<Application> appOpt = applicationRepository.findById(id);
            if (appOpt.isPresent()) {
                return appOpt.get();
            }
        } catch (NumberFormatException ignored) {
        }
        return applicationRepository.findByApplicationNumber(identifier.trim()).orElse(null);
    }

    public long countTotalApplications() {
        return applicationRepository.count();
    }

    public long countByStatus(String status) {
        return applicationRepository.countByStatus(status);
    }

    @Transactional
    public void reuploadDocuments(Application app,
                                  org.springframework.web.multipart.MultipartFile aadhaarFile,
                                  org.springframework.web.multipart.MultipartFile passbookFile,
                                  DocumentService documentService) throws Exception {
        boolean updated = false;
        if (aadhaarFile != null && !aadhaarFile.isEmpty()) {
            documentService.replaceDocument(app.getBeneficiary(), app, "AADHAAR", aadhaarFile);
            updated = true;
        }
        if (passbookFile != null && !passbookFile.isEmpty()) {
            documentService.replaceDocument(app.getBeneficiary(), app, "BANK_PASSBOOK", passbookFile);
            updated = true;
        }
        if (updated) {
            app.setStatus("PENDING");
            app.setCurrentStage("FIELD_VERIFICATION");
            app.setUpdatedAt(LocalDateTime.now());
            applicationRepository.save(app);

            auditLogger.log("BENEFICIARY_CORRECTION_SUBMITTED", "Application", app.getId(),
                    "Beneficiary submitted corrections for Application: " + app.getApplicationNumber());
            auditLogger.log("DOCUMENT_REUPLOADED", "Application", app.getId(),
                    "Beneficiary uploaded replacement documents for Application: " + app.getApplicationNumber());

            // Notify Beneficiary
            notificationService.createNotification(app.getBeneficiary().getUser(), app, "BENEFICIARY_CORRECTION_SUBMITTED",
                    "Replacement Documents Uploaded",
                    "Replacement documents for Application #" + app.getApplicationNumber() + " were uploaded and queued for Field Re-verification.",
                    "MEDIUM", "/beneficiary/applications/" + app.getId(), "NOTIF_REUPLOAD_BEN_" + app.getId() + "_" + System.currentTimeMillis());

            // Notify Field Officer
            notificationService.notifyRoleUsers("FIELD_OFFICER", app.getBeneficiary().getState(), app.getBeneficiary().getDistrict(), app,
                    "BENEFICIARY_CORRECTION_SUBMITTED", "Beneficiary Uploaded Replacement Documents",
                    "Beneficiary uploaded replacement documents for Application #" + app.getApplicationNumber() + ".",
                    "HIGH", "/field-officer/verification/" + app.getId(), "NOTIF_REUPLOAD_FO_" + app.getId() + "_" + System.currentTimeMillis());
        }
    }
}
