package com.example.digital_subsidy.service;

import com.example.digital_subsidy.dto.EligibilityCriteriaDto;
import com.example.digital_subsidy.dto.RequiredDocumentDto;
import com.example.digital_subsidy.dto.SchemeConfigDto;
import com.example.digital_subsidy.exception.ResourceNotFoundException;
import com.example.digital_subsidy.model.EligibilityCriteria;
import com.example.digital_subsidy.model.RequiredDocument;
import com.example.digital_subsidy.model.Scheme;
import com.example.digital_subsidy.repository.EligibilityCriteriaRepository;
import com.example.digital_subsidy.repository.RequiredDocumentRepository;
import com.example.digital_subsidy.repository.SchemeRepository;
import com.example.digital_subsidy.util.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SchemeService {

    private final SchemeRepository schemeRepository;
    private final EligibilityCriteriaRepository criteriaRepository;
    private final RequiredDocumentRepository requiredDocumentRepository;
    private final com.example.digital_subsidy.repository.DisbursementMilestoneRepository milestoneRepository;
    private final AuditLogger auditLogger;

    public SchemeService(SchemeRepository schemeRepository,
                         EligibilityCriteriaRepository criteriaRepository,
                         RequiredDocumentRepository requiredDocumentRepository,
                         com.example.digital_subsidy.repository.DisbursementMilestoneRepository milestoneRepository,
                         AuditLogger auditLogger) {
        this.schemeRepository = schemeRepository;
        this.criteriaRepository = criteriaRepository;
        this.requiredDocumentRepository = requiredDocumentRepository;
        this.milestoneRepository = milestoneRepository;
        this.auditLogger = auditLogger;
    }

    public List<Scheme> getAllActiveSchemes() {
        return schemeRepository.findByStatus("ACTIVE");
    }

    public List<Scheme> getAllSchemes() {
        return schemeRepository.findAll();
    }

    public Scheme getSchemeById(Long id) {
        return schemeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scheme not found with ID: " + id));
    }

    /**
     * Fetches scheme + all related collections within one transaction to avoid
     * LazyInitializationException when the controller accesses these lists.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSchemeConfigData(Long id) {
        Scheme scheme = getSchemeById(id);
        List<EligibilityCriteria> criteriaList = criteriaRepository.findBySchemeIdOrderByDisplayOrderAsc(id);
        List<RequiredDocument> docList = requiredDocumentRepository.findBySchemeId(id);
        List<com.example.digital_subsidy.model.DisbursementMilestone> milestoneList = milestoneRepository.findBySchemeIdOrderByDisplayOrderAsc(id);

        int totalWeight = criteriaList.stream()
                .filter(EligibilityCriteria::isActive)
                .mapToInt(c -> c.getWeightPoints() != null ? c.getWeightPoints() : 0)
                .sum();

        int totalMilestonePercentage = milestoneList.stream()
                .filter(com.example.digital_subsidy.model.DisbursementMilestone::isActive)
                .mapToInt(m -> m.getPercentage() != null ? m.getPercentage() : 0)
                .sum();

        List<String> activationErrors = validateSchemeForActivation(id);

        Map<String, Object> data = new HashMap<>();
        data.put("scheme", scheme);
        data.put("criteriaList", criteriaList);
        data.put("docList", docList);
        data.put("milestoneList", milestoneList);
        data.put("totalWeight", totalWeight);
        data.put("totalMilestonePercentage", totalMilestonePercentage);
        data.put("activationErrors", activationErrors);
        data.put("categoryOptions", getAvailableCategories());
        return data;
    }

    public List<String> getAvailableCategories() {
        return List.of(
            "Education",
            "Agriculture & Farmers",
            "MSME & Business",
            "Employment & Skill Development",
            "Housing",
            "Healthcare",
            "Women Welfare",
            "Child & Family Welfare",
            "Animal Husbandry & Dairy",
            "Fisheries",
            "Renewable Energy",
            "Infrastructure & Rural Development",
            "Social Welfare",
            "Handicrafts & Textiles",
            "Other"
        );
    }

    @Transactional
    public Scheme saveScheme(Scheme scheme, List<EligibilityCriteria> criteria) {
        if (scheme.getRemainingBudget() == null) {
            scheme.setRemainingBudget(scheme.getTotalBudget() != null ? scheme.getTotalBudget() : BigDecimal.ZERO);
        }
        Scheme saved = schemeRepository.save(scheme);

        if (criteria != null && !criteria.isEmpty()) {
            for (EligibilityCriteria c : criteria) {
                c.setScheme(saved);
                criteriaRepository.save(c);
            }
        }

        auditLogger.log("SCHEME_CREATED_OR_UPDATED", "Scheme", saved.getId(), "Scheme name: " + saved.getName());
        return saved;
    }

    @Transactional
    public Scheme saveSchemeConfig(SchemeConfigDto dto, String adminEmail) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("Scheme name is required.");
        }

        if (dto.getMaxAmount() == null || dto.getMaxAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Maximum grant amount must be greater than zero.");
        }

        if (dto.getMinQualifyingScore() == null || dto.getMinQualifyingScore() < 0 || dto.getMinQualifyingScore() > 100) {
            throw new IllegalArgumentException("Minimum qualifying score must be between 0 and 100.");
        }

        if (dto.getStartDate() != null && dto.getEndDate() != null && dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new IllegalArgumentException("Application end date cannot be before start date.");
        }

        Scheme scheme;
        boolean isNew = false;
        if (dto.getId() != null) {
            scheme = getSchemeById(dto.getId());
        } else {
            // Check for duplicate scheme name
            schemeRepository.findAll().stream()
                .filter(s -> s.getName().equalsIgnoreCase(dto.getName().trim()))
                .findFirst()
                .ifPresent(s -> {
                    throw new IllegalArgumentException("A scheme with the name '" + dto.getName().trim() + "' already exists.");
                });
            scheme = new Scheme();
            isNew = true;
        }

        scheme.setName(dto.getName().trim());
        scheme.setCategory(dto.getCategory() != null ? dto.getCategory() : "General");
        scheme.setDescription(dto.getDescription());
        scheme.setMinAmount(dto.getMinAmount() != null ? dto.getMinAmount() : BigDecimal.ZERO);
        scheme.setMaxAmount(dto.getMaxAmount());
        if (dto.getTotalBudget() != null) {
            scheme.setTotalBudget(dto.getTotalBudget());
            if (isNew || scheme.getRemainingBudget() == null) {
                scheme.setRemainingBudget(dto.getTotalBudget());
            }
        } else if (isNew) {
            scheme.setTotalBudget(new BigDecimal("10000000"));
            scheme.setRemainingBudget(new BigDecimal("10000000"));
        }
        scheme.setTargetCategories(dto.getTargetCategories() != null ? dto.getTargetCategories() : "General,OBC,SC,ST");
        scheme.setEligibleStates(dto.getEligibleStates() != null ? dto.getEligibleStates() : "ALL");
        scheme.setMinQualifyingScore(dto.getMinQualifyingScore());
        scheme.setStartDate(dto.getStartDate());
        scheme.setEndDate(dto.getEndDate());

        if (dto.getStatus() != null && !dto.getStatus().isBlank()) {
            scheme.setStatus(dto.getStatus());
        } else if (isNew) {
            scheme.setStatus("DRAFT");
        }

        Scheme saved = schemeRepository.save(scheme);
        auditLogger.log(isNew ? "SCHEME_CREATED" : "SCHEME_UPDATED", "Scheme", saved.getId(),
                "Saved scheme: " + saved.getName() + " | Category: " + saved.getCategory() + " | Admin: " + adminEmail);
        return saved;
    }

    @Transactional
    public EligibilityCriteria saveOrUpdateCriterion(Long schemeId, EligibilityCriteriaDto dto, String adminEmail) {
        Scheme scheme = getSchemeById(schemeId);
        EligibilityCriteria criterion;
        boolean isNew = false;

        if (dto.getId() != null) {
            criterion = criteriaRepository.findById(dto.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Criterion not found with ID: " + dto.getId()));
        } else {
            criterion = new EligibilityCriteria();
            criterion.setScheme(scheme);
            isNew = true;
        }

        String qText = dto.getQuestionText() != null ? dto.getQuestionText().trim() : "";
        criterion.setQuestionText(qText);
        criterion.setDescription(dto.getDescription() != null ? dto.getDescription() : qText);

        String fieldType = dto.getAnswerType() != null ? dto.getAnswerType().toUpperCase() : "TEXT";
        criterion.setAnswerType(fieldType);

        String critType = switch (fieldType) {
            case "NUMBER", "DECIMAL" -> "NUMERIC";
            case "YES_NO" -> "BOOLEAN";
            case "DROPDOWN" -> "CATEGORICAL";
            default -> "TEXT";
        };
        criterion.setCriterionType(critType);

        String cName = dto.getCriterionName();
        if (cName == null || cName.isBlank()) {
            cName = "Q_" + (qText.replaceAll("[^a-zA-Z0-9_]", "_").toUpperCase());
            if (cName.length() > 50) cName = cName.substring(0, 50);
        }
        criterion.setCriterionName(cName);

        criterion.setOperator(dto.getOperator() != null ? dto.getOperator() : "EQUALS");
        criterion.setTargetValue(dto.getTargetValue() != null ? dto.getTargetValue() : "YES");
        criterion.setWeightPoints(dto.getWeightPoints() != null ? dto.getWeightPoints() : 20);
        criterion.setOptions(dto.getOptions());
        criterion.setDisplayOrder(dto.getDisplayOrder() != null ? dto.getDisplayOrder() : 1);
        criterion.setRequired(dto.getRequired() != null ? dto.getRequired() : true);
        criterion.setActive(dto.getActive() != null ? dto.getActive() : true);

        EligibilityCriteria saved = criteriaRepository.save(criterion);
        auditLogger.log(isNew ? "CRITERION_ADDED" : "CRITERION_UPDATED", "EligibilityCriteria", saved.getId(),
                "Criterion: " + saved.getQuestionText() + " | Weight: " + saved.getWeightPoints() + "% | Admin: " + adminEmail);
        return saved;
    }

    @Transactional
    public void toggleCriterionStatus(Long criterionId, String adminEmail) {
        EligibilityCriteria c = criteriaRepository.findById(criterionId)
                .orElseThrow(() -> new ResourceNotFoundException("Criterion not found: " + criterionId));
        boolean newStatus = !c.isActive();
        c.setActive(newStatus);
        criteriaRepository.save(c);
        auditLogger.log(newStatus ? "CRITERION_ENABLED" : "CRITERION_DISABLED", "EligibilityCriteria", c.getId(),
                "Criterion status changed to " + newStatus + " | Admin: " + adminEmail);
    }

    @Transactional
    public RequiredDocument saveOrUpdateDocument(Long schemeId, RequiredDocumentDto dto, String adminEmail) {
        Scheme scheme = getSchemeById(schemeId);
        RequiredDocument doc;
        boolean isNew = false;

        if (dto.getId() != null) {
            doc = requiredDocumentRepository.findById(dto.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Required document not found: " + dto.getId()));
        } else {
            doc = new RequiredDocument();
            doc.setScheme(scheme);
            isNew = true;
        }

        doc.setDocumentName(dto.getDocumentName());
        String docType = dto.getDocumentType();
        if (docType == null || docType.isBlank()) {
            docType = dto.getDocumentName().replaceAll("[^a-zA-Z0-9_]", "_").toUpperCase();
            if (docType.length() > 50) docType = docType.substring(0, 50);
        }
        doc.setDocumentType(docType);
        doc.setRequired(dto.getRequired() != null ? dto.getRequired() : true);
        doc.setAllowedFileTypes(dto.getAllowedFileTypes() != null ? dto.getAllowedFileTypes() : "PDF, JPG, JPEG, PNG");
        doc.setMaxFileSizeMb(dto.getMaxFileSizeMb() != null ? dto.getMaxFileSizeMb() : 5);
        doc.setDisplayOrder(dto.getDisplayOrder() != null ? dto.getDisplayOrder() : 1);
        doc.setActive(dto.getActive() != null ? dto.getActive() : true);

        RequiredDocument saved = requiredDocumentRepository.save(doc);
        auditLogger.log(isNew ? "DOCUMENT_ADDED" : "DOCUMENT_UPDATED", "RequiredDocument", saved.getId(),
                "Document: " + saved.getDocumentName() + " | Required: " + saved.isRequired() + " | Admin: " + adminEmail);
        return saved;
    }

    @Transactional
    public void toggleDocumentStatus(Long documentId, String adminEmail) {
        RequiredDocument rd = requiredDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
        boolean newStatus = !rd.isActive();
        rd.setActive(newStatus);
        requiredDocumentRepository.save(rd);
        auditLogger.log(newStatus ? "DOCUMENT_ENABLED" : "DOCUMENT_DISABLED", "RequiredDocument", rd.getId(),
                "Document status changed to " + newStatus + " | Admin: " + adminEmail);
    }

    @Transactional
    public com.example.digital_subsidy.model.DisbursementMilestone saveOrUpdateMilestone(Long schemeId, com.example.digital_subsidy.dto.DisbursementMilestoneDto dto, String adminEmail) {
        Scheme scheme = getSchemeById(schemeId);
        com.example.digital_subsidy.model.DisbursementMilestone milestone;
        boolean isNew = false;

        if (dto.getId() != null) {
            milestone = milestoneRepository.findById(dto.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Milestone not found: " + dto.getId()));
        } else {
            milestone = new com.example.digital_subsidy.model.DisbursementMilestone();
            milestone.setScheme(scheme);
            isNew = true;
        }

        if (dto.getMilestoneName() == null || dto.getMilestoneName().isBlank()) {
            throw new IllegalArgumentException("Milestone name is required.");
        }
        if (dto.getPercentage() == null || dto.getPercentage() <= 0 || dto.getPercentage() > 100) {
            throw new IllegalArgumentException("Milestone percentage must be between 1 and 100.");
        }

        milestone.setMilestoneName(dto.getMilestoneName().trim());
        milestone.setPercentage(dto.getPercentage());
        milestone.setDisplayOrder(dto.getDisplayOrder() != null ? dto.getDisplayOrder() : 1);
        milestone.setActive(dto.getActive() != null ? dto.getActive() : true);
        milestone.setDescription(dto.getDescription());

        com.example.digital_subsidy.model.DisbursementMilestone saved = milestoneRepository.save(milestone);
        auditLogger.log(isNew ? "MILESTONE_ADDED" : "MILESTONE_UPDATED", "DisbursementMilestone", saved.getId(),
                "Milestone: " + saved.getMilestoneName() + " | " + saved.getPercentage() + "% | Admin: " + adminEmail);
        return saved;
    }

    @Transactional
    public void toggleMilestoneStatus(Long milestoneId, String adminEmail) {
        com.example.digital_subsidy.model.DisbursementMilestone m = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found: " + milestoneId));
        boolean newStatus = !m.isActive();
        m.setActive(newStatus);
        milestoneRepository.save(m);
        auditLogger.log(newStatus ? "MILESTONE_ENABLED" : "MILESTONE_DISABLED", "DisbursementMilestone", m.getId(),
                "Milestone status changed to " + newStatus + " | Admin: " + adminEmail);
    }

    @Transactional
    public void deleteMilestone(Long milestoneId, String adminEmail) {
        com.example.digital_subsidy.model.DisbursementMilestone m = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found: " + milestoneId));
        milestoneRepository.delete(m);
        auditLogger.log("MILESTONE_DELETED", "DisbursementMilestone", milestoneId,
                "Milestone deleted: " + m.getMilestoneName() + " | Admin: " + adminEmail);
    }

    public List<String> validateSchemeForActivation(Long schemeId) {
        List<String> errors = new ArrayList<>();
        Scheme scheme = getSchemeById(schemeId);

        if (scheme.getName() == null || scheme.getName().isBlank()) {
            errors.add("Scheme name cannot be empty.");
        }
        if (scheme.getDescription() == null || scheme.getDescription().isBlank()) {
            errors.add("Scheme description cannot be empty.");
        }
        if (scheme.getMaxAmount() == null || scheme.getMaxAmount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Maximum grant amount must be greater than zero.");
        }
        if (scheme.getMinQualifyingScore() == null || scheme.getMinQualifyingScore() < 0 || scheme.getMinQualifyingScore() > 100) {
            errors.add("Minimum qualifying score must be between 0 and 100.");
        }

        List<EligibilityCriteria> activeCriteria = criteriaRepository.findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(schemeId);
        if (activeCriteria.isEmpty()) {
            errors.add("Scheme must have at least one active eligibility question/criterion.");
        } else {
            int totalWeight = activeCriteria.stream()
                    .mapToInt(c -> c.getWeightPoints() != null ? c.getWeightPoints() : 0)
                    .sum();
            if (totalWeight != 100) {
                errors.add("Active eligibility criteria weights must total exactly 100%. Current total: " + totalWeight + "%.");
            }
        }

        List<RequiredDocument> activeDocs = requiredDocumentRepository.findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(schemeId);
        if (activeDocs.isEmpty()) {
            errors.add("Scheme must have at least one active required supporting document.");
        }

        List<com.example.digital_subsidy.model.DisbursementMilestone> activeMilestones = milestoneRepository.findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(schemeId);
        if (activeMilestones.isEmpty()) {
            errors.add("Scheme must have at least one active disbursement milestone configured.");
        } else {
            int totalMilestonePct = activeMilestones.stream()
                    .mapToInt(m -> m.getPercentage() != null ? m.getPercentage() : 0)
                    .sum();
            if (totalMilestonePct != 100) {
                errors.add("Active disbursement milestone percentages must total exactly 100%. Current total: " + totalMilestonePct + "%.");
            }
        }

        return errors;
    }

    @Transactional
    public Scheme activateOrDeactivateScheme(Long schemeId, String targetStatus, String adminEmail) {
        Scheme scheme = getSchemeById(schemeId);
        if ("ACTIVE".equalsIgnoreCase(targetStatus)) {
            List<String> validationErrors = validateSchemeForActivation(schemeId);
            if (!validationErrors.isEmpty()) {
                throw new IllegalStateException("Activation Failed: " + String.join(" ", validationErrors));
            }
            scheme.setStatus("ACTIVE");
            auditLogger.log("SCHEME_ACTIVATED", "Scheme", scheme.getId(), "Activated scheme: " + scheme.getName() + " | Admin: " + adminEmail);
        } else if ("INACTIVE".equalsIgnoreCase(targetStatus)) {
            scheme.setStatus("INACTIVE");
            auditLogger.log("SCHEME_DEACTIVATED", "Scheme", scheme.getId(), "Deactivated scheme: " + scheme.getName() + " | Admin: " + adminEmail);
        } else {
            scheme.setStatus("DRAFT");
            auditLogger.log("SCHEME_UPDATED", "Scheme", scheme.getId(), "Set scheme status to DRAFT: " + scheme.getName() + " | Admin: " + adminEmail);
        }
        return schemeRepository.save(scheme);
    }

    public long countActiveSchemes() {
        return schemeRepository.countByStatus("ACTIVE");
    }

    public long countDraftSchemes() {
        return schemeRepository.countByStatus("DRAFT");
    }

    public long countTotalCriteria() {
        return criteriaRepository.count();
    }

    public long countTotalDocuments() {
        return requiredDocumentRepository.count();
    }
}

