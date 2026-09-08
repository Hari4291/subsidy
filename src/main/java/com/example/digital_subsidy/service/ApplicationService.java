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

@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final SchemeRepository schemeRepository;
    private final EligibilityScoreRepository eligibilityScoreRepository;
    private final EligibilityService eligibilityService;
    private final com.example.digital_subsidy.repository.DocumentRepository documentRepository;
    private final ApplicationEligibilityAnswerRepository answerRepository;
    private final EligibilityCriteriaRepository criteriaRepository;
    private final AuditLogger auditLogger;

    public ApplicationService(ApplicationRepository applicationRepository,
                              SchemeRepository schemeRepository,
                              EligibilityScoreRepository eligibilityScoreRepository,
                              EligibilityService eligibilityService,
                              com.example.digital_subsidy.repository.DocumentRepository documentRepository,
                              ApplicationEligibilityAnswerRepository answerRepository,
                              EligibilityCriteriaRepository criteriaRepository,
                              AuditLogger auditLogger) {
        this.applicationRepository = applicationRepository;
        this.schemeRepository = schemeRepository;
        this.eligibilityScoreRepository = eligibilityScoreRepository;
        this.eligibilityService = eligibilityService;
        this.documentRepository = documentRepository;
        this.answerRepository = answerRepository;
        this.criteriaRepository = criteriaRepository;
        this.auditLogger = auditLogger;
    }

    @Transactional
    public Application submitApplication(Beneficiary beneficiary, ApplicationSubmissionDto dto) {
        Scheme scheme = schemeRepository.findById(dto.getSchemeId())
                .orElseThrow(() -> new ResourceNotFoundException("Scheme not found with ID: " + dto.getSchemeId()));

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
        // Link only ACTIVE unlinked docs; do NOT touch archived or already-linked docs.
        List<Document> beneficiaryDocs = documentRepository.findByBeneficiaryId(beneficiary.getId());
        for (Document doc : beneficiaryDocs) {
            if (doc.getApplication() == null && "ACTIVE".equals(doc.getStatus())) {
                doc.setApplication(savedApp);
                documentRepository.save(doc);
            }
        }

        auditLogger.log("APPLICATION_SUBMITTED", "Application", savedApp.getId(),
                "Application submitted: " + savedApp.getApplicationNumber() + " for Scheme: " + scheme.getName());

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
            auditLogger.log("DOCUMENT_REUPLOADED", "Application", app.getId(),
                    "Beneficiary uploaded replacement documents for Application: " + app.getApplicationNumber());
        }
    }
}
