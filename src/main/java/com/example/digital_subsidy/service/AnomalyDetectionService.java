package com.example.digital_subsidy.service;

import com.example.digital_subsidy.model.AnomalyFlag;
import com.example.digital_subsidy.model.Application;
import com.example.digital_subsidy.model.Beneficiary;
import com.example.digital_subsidy.model.Document;
import com.example.digital_subsidy.repository.AnomalyFlagRepository;
import com.example.digital_subsidy.repository.BeneficiaryRepository;
import com.example.digital_subsidy.repository.DocumentRepository;
import com.example.digital_subsidy.util.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class AnomalyDetectionService {

    private final AnomalyFlagRepository anomalyFlagRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final DocumentRepository documentRepository;
    private final AuditLogger auditLogger;

    public AnomalyDetectionService(AnomalyFlagRepository anomalyFlagRepository,
                                  BeneficiaryRepository beneficiaryRepository,
                                  DocumentRepository documentRepository,
                                  AuditLogger auditLogger) {
        this.anomalyFlagRepository = anomalyFlagRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.documentRepository = documentRepository;
        this.auditLogger = auditLogger;
    }

    @Transactional
    public void scanAndFlagAnomalies(Application app) {
        if (app == null || app.getBeneficiary() == null) return;

        Beneficiary ben = app.getBeneficiary();
        List<AnomalyFlag> existingFlags = anomalyFlagRepository.findByApplicationId(app.getId());

        // 1. Duplicate Identity Check
        if (ben.getIdentityNumber() != null && !ben.getIdentityNumber().isBlank()) {
            List<Beneficiary> dupIdentities = beneficiaryRepository.findAll().stream()
                    .filter(b -> !b.getId().equals(ben.getId()) && ben.getIdentityNumber().equalsIgnoreCase(b.getIdentityNumber()))
                    .toList();
            if (!dupIdentities.isEmpty() && existingFlags.stream().noneMatch(f -> "DUPLICATE_IDENTITY".equalsIgnoreCase(f.getFlagType()))) {
                AnomalyFlag flag = new AnomalyFlag(app, "DUPLICATE_IDENTITY",
                        "Identical government identity number (" + ben.getIdentityNumber() + ") detected on another registered beneficiary record.", "HIGH");
                anomalyFlagRepository.save(flag);
            }
        }

        // 2. Duplicate Mobile Check
        if (ben.getUser() != null && ben.getUser().getMobileNumber() != null) {
            String mob = ben.getUser().getMobileNumber();
            List<Beneficiary> dupMobiles = beneficiaryRepository.findAll().stream()
                    .filter(b -> !b.getId().equals(ben.getId()) && b.getUser() != null && mob.equalsIgnoreCase(b.getUser().getMobileNumber()))
                    .toList();
            if (!dupMobiles.isEmpty() && existingFlags.stream().noneMatch(f -> "DUPLICATE_MOBILE".equalsIgnoreCase(f.getFlagType()))) {
                AnomalyFlag flag = new AnomalyFlag(app, "DUPLICATE_MOBILE",
                        "Mobile number (" + mob + ") is shared with another beneficiary account.", "MEDIUM");
                anomalyFlagRepository.save(flag);
            }
        }

        // 3. Income and Grant Mismatch Check
        if (app.getRequestedAmount() != null && app.getRequestedAmount().compareTo(new BigDecimal("90000")) >= 0) {
            if (existingFlags.stream().noneMatch(f -> "INCOME_GRANT_MISMATCH".equalsIgnoreCase(f.getFlagType()))) {
                AnomalyFlag flag = new AnomalyFlag(app, "INCOME_GRANT_MISMATCH",
                        "High requested grant amount (₹" + app.getRequestedAmount() + ") requires thorough document and income verification.", "MEDIUM");
                anomalyFlagRepository.save(flag);
            }
        }

        // 4. Repeated Document Re-upload Pattern Check
        List<Document> allDocs = documentRepository.findByApplicationId(app.getId());
        long archivedCount = allDocs.stream().filter(d -> "ARCHIVED".equalsIgnoreCase(d.getStatus())).count();
        if (archivedCount >= 2 && existingFlags.stream().noneMatch(f -> "REPEATED_DOC_REUPLOAD".equalsIgnoreCase(f.getFlagType()))) {
            AnomalyFlag flag = new AnomalyFlag(app, "REPEATED_DOC_REUPLOAD",
                    "Application has " + archivedCount + " archived document re-upload attempts. Verify document consistency.", "LOW");
            anomalyFlagRepository.save(flag);
        }
    }

    public List<AnomalyFlag> getFlagsForDistrict(String state, String district) {
        if (state != null && district != null && !district.isBlank()) {
            return anomalyFlagRepository.findByApplicationBeneficiaryStateAndApplicationBeneficiaryDistrict(state, district);
        } else if (state != null && !state.isBlank()) {
            return anomalyFlagRepository.findByApplicationBeneficiaryState(state);
        }
        return anomalyFlagRepository.findAll();
    }

    public List<AnomalyFlag> getFlagsForApplication(Long applicationId) {
        return anomalyFlagRepository.findByApplicationId(applicationId);
    }

    @Transactional
    public AnomalyFlag resolveFlag(Long flagId, String status, String remarks, String resolvedBy) {
        AnomalyFlag flag = anomalyFlagRepository.findById(flagId).orElse(null);
        if (flag != null) {
            flag.setStatus(status);
            flag.setOfficerRemarks(remarks);
            flag.setResolvedBy(resolvedBy);
            flag.setResolvedAt(LocalDateTime.now());
            AnomalyFlag saved = anomalyFlagRepository.save(flag);
            auditLogger.log("ANOMALY_FLAG_" + status, "AnomalyFlag", flag.getId(),
                    "Anomaly flag #" + flag.getId() + " status set to " + status + " by " + resolvedBy);
            return saved;
        }
        return null;
    }
}
