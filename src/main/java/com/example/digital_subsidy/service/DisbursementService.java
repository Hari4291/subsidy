package com.example.digital_subsidy.service;

import com.example.digital_subsidy.dto.DisbursementPlanDto;
import com.example.digital_subsidy.dto.PartiallyReleasedApplicationDto;
import com.example.digital_subsidy.dto.StageConfigDto;
import com.example.digital_subsidy.dto.StageReleaseDto;
import com.example.digital_subsidy.exception.ResourceNotFoundException;
import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.*;
import com.example.digital_subsidy.util.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class DisbursementService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DisbursementService.class);

    public static final BigDecimal DISBURSEMENT_SINGLE_RELEASE_THRESHOLD = new BigDecimal("40000.00");

    private final DisbursementRepository disbursementRepository;
    private final MilestoneRepository milestoneRepository;
    private final DisbursementMilestoneRepository schemeMilestoneRepository;
    private final SchemeRepository schemeRepository;
    private final ApplicationRepository applicationRepository;
    private final DisbursementReleaseRepository releaseRepository;
    private final AuditLogger auditLogger;
    private final DocumentService documentService;
    private final DocumentRepository documentRepository;
    private final NotificationService notificationService;
    private final BeneficiaryRepository beneficiaryRepository;
    private final OfficerRepository officerRepository;

    public DisbursementService(DisbursementRepository disbursementRepository,
                               MilestoneRepository milestoneRepository,
                               DisbursementMilestoneRepository schemeMilestoneRepository,
                               SchemeRepository schemeRepository,
                               ApplicationRepository applicationRepository,
                               DisbursementReleaseRepository releaseRepository,
                               AuditLogger auditLogger,
                               DocumentService documentService,
                               DocumentRepository documentRepository,
                               NotificationService notificationService,
                               BeneficiaryRepository beneficiaryRepository,
                               OfficerRepository officerRepository) {
        this.disbursementRepository = disbursementRepository;
        this.milestoneRepository = milestoneRepository;
        this.schemeMilestoneRepository = schemeMilestoneRepository;
        this.schemeRepository = schemeRepository;
        this.applicationRepository = applicationRepository;
        this.releaseRepository = releaseRepository;
        this.auditLogger = auditLogger;
        this.documentService = documentService;
        this.documentRepository = documentRepository;
        this.notificationService = notificationService;
        this.beneficiaryRepository = beneficiaryRepository;
        this.officerRepository = officerRepository;
    }

    @Transactional
    public Disbursement initializeDisbursement(Application application, User approver) {
        if (application == null || application.getSanctionedAmount() == null || application.getSanctionedAmount().compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Disbursement initialization rejected: Application {} has no valid final sanctioned amount.",
                    application != null ? application.getApplicationNumber() : "null");
            throw new IllegalArgumentException("Disbursement plan creation rejected: Final sanctioned amount is missing or invalid.");
        }

        BigDecimal total = application.getSanctionedAmount();

        Optional<Disbursement> existingOpt = disbursementRepository.findByApplication(application);
        Disbursement disbursement;
        if (existingOpt.isPresent()) {
            disbursement = existingOpt.get();
        } else {
            disbursement = new Disbursement();
            disbursement.setApplication(application);
            disbursement.setTotalSanctionedAmount(total);
            disbursement.setDisbursedAmount(BigDecimal.ZERO);
            disbursement.setStatus("IN_PROGRESS");
            disbursement.setApprovedBy(approver);
            disbursement = disbursementRepository.save(disbursement);
        }

        disbursement.setTotalSanctionedAmount(total);
        if (approver != null) {
            disbursement.setApprovedBy(approver);
        }

        BigDecimal threshold = DISBURSEMENT_SINGLE_RELEASE_THRESHOLD;

        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        boolean anyReleased = milestones != null && milestones.stream().anyMatch(m ->
                "RELEASED".equalsIgnoreCase(m.getCompletionStatus()) || "COMPLETED".equalsIgnoreCase(m.getCompletionStatus()) ||
                (m.getReleasedAmount() != null && m.getReleasedAmount().compareTo(BigDecimal.ZERO) > 0)
        );

        if (milestones == null || milestones.isEmpty()) {
            if (total.compareTo(threshold) <= 0) {
                String docType = resolveTermDocumentType(application, 1);
                createMilestone(disbursement, 1, "Single-Phase Grant Release (100%)",
                        "Single full grant release tranche for sanctioned amount <= ₹" + threshold.toPlainString(),
                        100, total, LocalDate.now().plusDays(15), "Single-Phase Grant Disbursement Authorization", docType);
            } else {
                createThreePhaseMilestones(disbursement, application, total);
            }
        } else if (!anyReleased) {
            boolean shouldBeSinglePhase = total.compareTo(threshold) <= 0;
            if (shouldBeSinglePhase && (milestones.size() != 1 || milestones.get(0).getMilestoneAmount().compareTo(total) != 0)) {
                milestoneRepository.deleteAll(milestones);
                milestoneRepository.flush();
                if (disbursement.getMilestones() != null) {
                    disbursement.getMilestones().clear();
                }
                String docType = resolveTermDocumentType(application, 1);
                createMilestone(disbursement, 1, "Single-Phase Grant Release (100%)",
                        "Single full grant release tranche for sanctioned amount <= ₹" + threshold.toPlainString(),
                        100, total, LocalDate.now().plusDays(15), "Single-Phase Grant Disbursement Authorization", docType);
            } else if (!shouldBeSinglePhase && milestones.size() == 1) {
                milestoneRepository.deleteAll(milestones);
                milestoneRepository.flush();
                if (disbursement.getMilestones() != null) {
                    disbursement.getMilestones().clear();
                }
                createThreePhaseMilestones(disbursement, application, total);
            } else if (shouldBeSinglePhase && milestones.size() == 1) {
                Milestone m1 = milestones.get(0);
                m1.setMilestoneAmount(total);
                m1.setPercentage(100);
                if (m1.getRequiredDocumentType() == null || m1.getRequiredDocumentType().isBlank()) {
                    m1.setRequiredDocumentType(resolveTermDocumentType(application, 1));
                }
                milestoneRepository.save(m1);
            }
        }

        auditLogger.log("DISBURSEMENT_SCHEDULED", "Disbursement", disbursement.getId(), 
                "Scheduled milestone disbursement plan for Application: " + application.getApplicationNumber());

        return disbursementRepository.save(disbursement);
    }

    public static String getDocumentTypeDisplayName(String docType) {
        if (docType == null || docType.isBlank()) return "Required Document";
        return switch (docType.toUpperCase()) {
            case "INSTALLATION_WORK_ORDER" -> "Installation Work Order / Proforma Invoice";
            case "INSTALLATION_PROGRESS_REPORT" -> "Installation Progress Report / Purchase Invoice";
            case "COMPLETION_CERTIFICATE", "COMMISSIONING_CERTIFICATE", "INSTALLATION_COMPLETION" -> "Installation Completion / Commissioning Certificate";
            case "ADMISSION_PROOF" -> "Admission Proof / Fee Structure";
            case "FEE_PAYMENT_RECEIPT" -> "Fee Payment Receipt";
            case "COURSE_PROGRESS_CERTIFICATE" -> "Course Progress / Completion Certificate";
            case "EQUIPMENT_PURCHASE_INVOICE" -> "Equipment / Purchase Invoice";
            case "UTILIZATION_CERTIFICATE" -> "Utilization / Progress Report";
            case "FINAL_UTILIZATION_CERTIFICATE" -> "Final Utilization Certificate";
            case "AADHAAR" -> "Aadhaar Card";
            case "BANK_PASSBOOK" -> "Bank Passbook";
            case "INCOME_CERTIFICATE" -> "Income Certificate";
            case "ELECTRICITY_BILL" -> "Electricity Bill";
            case "UDYAM_CERTIFICATE" -> "Udyam / Business Registration";
            case "ENTERPRISE_PROOF" -> "Enterprise Proof";
            case "BONAFIDE_CERTIFICATE" -> "Bonafide / Student Certificate";
            default -> docType.replace("_", " ");
        };
    }

    public String resolveTermDocumentType(Application app, int termNumber) {
        if (app == null || app.getScheme() == null) return "UTILIZATION_CERTIFICATE";
        List<DisbursementMilestone> schemeMilestones = schemeMilestoneRepository.findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(app.getScheme().getId());
        if (schemeMilestones != null && !schemeMilestones.isEmpty()) {
            for (DisbursementMilestone sm : schemeMilestones) {
                if (sm.getDisplayOrder() != null && sm.getDisplayOrder() == termNumber && sm.getRequiredDocumentType() != null && !sm.getRequiredDocumentType().isBlank()) {
                    return sm.getRequiredDocumentType();
                }
            }
            if (termNumber <= schemeMilestones.size()) {
                String dt = schemeMilestones.get(termNumber - 1).getRequiredDocumentType();
                if (dt != null && !dt.isBlank()) return dt;
            }
        }
        String category = app.getScheme().getCategory() != null ? app.getScheme().getCategory().toLowerCase() : "";
        String name = app.getScheme().getName() != null ? app.getScheme().getName().toLowerCase() : "";
        if (category.contains("solar") || name.contains("solar") || name.contains("rooftop")) {
            return switch (termNumber) {
                case 1 -> "INSTALLATION_WORK_ORDER";
                case 2 -> "INSTALLATION_PROGRESS_REPORT";
                default -> "COMMISSIONING_CERTIFICATE";
            };
        } else if (category.contains("education") || name.contains("education") || name.contains("student")) {
            return switch (termNumber) {
                case 1 -> "ADMISSION_PROOF";
                case 2 -> "FEE_PAYMENT_RECEIPT";
                default -> "COURSE_PROGRESS_CERTIFICATE";
            };
        } else if (category.contains("msme") || name.contains("msme")) {
            return switch (termNumber) {
                case 1 -> "EQUIPMENT_PURCHASE_INVOICE";
                case 2 -> "UTILIZATION_CERTIFICATE";
                default -> "FINAL_UTILIZATION_CERTIFICATE";
            };
        } else {
            return switch (termNumber) {
                case 1 -> "EQUIPMENT_PURCHASE_INVOICE";
                case 2 -> "UTILIZATION_CERTIFICATE";
                default -> "FINAL_UTILIZATION_CERTIFICATE";
            };
        }
    }

    private void createThreePhaseMilestones(Disbursement disbursement, Application application, BigDecimal total) {
        List<DisbursementMilestone> schemeMilestones = 
                schemeMilestoneRepository.findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(application.getScheme().getId());

        if (schemeMilestones != null && schemeMilestones.size() == 3) {
            BigDecimal allocated = BigDecimal.ZERO;
            for (int i = 0; i < schemeMilestones.size(); i++) {
                DisbursementMilestone sm = schemeMilestones.get(i);
                BigDecimal amt;
                if (i == schemeMilestones.size() - 1) {
                    amt = total.subtract(allocated);
                } else {
                    amt = total.multiply(new BigDecimal(sm.getPercentage())).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                    allocated = allocated.add(amt);
                }
                String name = sm.getMilestoneName() + " (" + sm.getPercentage() + "%)";
                String desc = sm.getDescription() != null ? sm.getDescription() : "Tranche " + (i + 1) + " for " + sm.getMilestoneName();
                String reqDocType = sm.getRequiredDocumentType() != null && !sm.getRequiredDocumentType().isBlank()
                        ? sm.getRequiredDocumentType()
                        : resolveTermDocumentType(application, i + 1);
                createMilestone(disbursement, i + 1, name, desc, sm.getPercentage(), amt, LocalDate.now().plusDays((i + 1) * 30L), "Standard Scheme Verification Required", reqDocType);
            }
        } else {
            BigDecimal m1Amt = total.multiply(new BigDecimal("0.30")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal m2Amt = total.multiply(new BigDecimal("0.40")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal m3Amt = total.subtract(m1Amt).subtract(m2Amt);

            createMilestone(disbursement, 1, "Stage 1 - Initial Sanction (30%)", "First tranche upon sanction approval", 30, m1Amt, LocalDate.now().plusDays(15), "Initial Document Verification", resolveTermDocumentType(application, 1));
            createMilestone(disbursement, 2, "Stage 2 - Implementation Milestone (40%)", "Second tranche upon mid-term compliance proof", 40, m2Amt, LocalDate.now().plusDays(45), "Mid-term Field Utilization Report", resolveTermDocumentType(application, 2));
            createMilestone(disbursement, 3, "Stage 3 - Final Completion (30%)", "Final tranche upon project completion certificate", 30, m3Amt, LocalDate.now().plusDays(90), "Final Utilization Certificate", resolveTermDocumentType(application, 3));
        }
    }

    @Transactional
    public Disbursement createOrUpdateCustomPlan(DisbursementPlanDto planDto, User approver) {
        if (planDto.getApplicationId() == null) {
            throw new IllegalArgumentException("Application ID is required to configure disbursement plan");
        }

        Application application = applicationRepository.findById(planDto.getApplicationId())
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with ID: " + planDto.getApplicationId()));

        BigDecimal sanctionedAmount = application.getSanctionedAmount();
        if (sanctionedAmount == null || sanctionedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("Custom disbursement plan configuration rejected: Application {} has no valid final sanctioned amount.",
                    application.getApplicationNumber());
            throw new IllegalArgumentException("Cannot configure disbursement plan for zero or invalid sanctioned amount");
        }

        List<StageConfigDto> stages = planDto.getStages();
        if (stages == null || stages.isEmpty()) {
            throw new IllegalArgumentException("At least one stage must be defined in the disbursement plan");
        }

        // Backend Validation
        if ("PERCENTAGE".equalsIgnoreCase(planDto.getCalculationType())) {
            int totalPct = stages.stream().mapToInt(s -> s.getPercentage() != null ? s.getPercentage() : 0).sum();
            if (totalPct != 100) {
                throw new IllegalArgumentException("Total stage percentage must equal 100%. Current total: " + totalPct + "%");
            }
        } else {
            BigDecimal totalStageAmt = stages.stream()
                    .map(s -> s.getAmount() != null ? s.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalStageAmt.compareTo(sanctionedAmount) != 0) {
                throw new IllegalArgumentException("Total of fixed stage amounts (₹" + totalStageAmt + ") must equal sanctioned amount (₹" + sanctionedAmount + ")");
            }
        }

        Disbursement disbursement = disbursementRepository.findByApplication(application)
                .orElseGet(() -> {
                    Disbursement d = new Disbursement();
                    d.setApplication(application);
                    d.setTotalSanctionedAmount(sanctionedAmount);
                    d.setDisbursedAmount(BigDecimal.ZERO);
                    d.setStatus("PLANNED");
                    d.setApprovedBy(approver);
                    return disbursementRepository.save(d);
                });

        disbursement.setTotalSanctionedAmount(sanctionedAmount);
        disbursement.setApprovedBy(approver);
        disbursement.setUpdatedAt(LocalDateTime.now());

        // Check if any stage has already been released
        List<Milestone> existingMilestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        boolean anyReleased = existingMilestones.stream().anyMatch(m -> "RELEASED".equalsIgnoreCase(m.getCompletionStatus()) || "COMPLETED".equalsIgnoreCase(m.getCompletionStatus()));

        if (anyReleased) {
            throw new IllegalStateException("Cannot reconfigure disbursement plan after fund release has started");
        }

        // Remove existing unreleased milestones
        if (existingMilestones != null && !existingMilestones.isEmpty()) {
            milestoneRepository.deleteAll(existingMilestones);
            disbursement.getMilestones().clear();
        }

        BigDecimal allocatedTotal = BigDecimal.ZERO;
        for (int i = 0; i < stages.size(); i++) {
            StageConfigDto config = stages.get(i);
            int stageNum = i + 1;
            BigDecimal stageAmt;
            int pct;

            if ("PERCENTAGE".equalsIgnoreCase(planDto.getCalculationType())) {
                pct = config.getPercentage() != null ? config.getPercentage() : 0;
                if (i == stages.size() - 1) {
                    stageAmt = sanctionedAmount.subtract(allocatedTotal);
                } else {
                    stageAmt = sanctionedAmount.multiply(new BigDecimal(pct)).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                    allocatedTotal = allocatedTotal.add(stageAmt);
                }
            } else {
                stageAmt = config.getAmount();
                pct = stageAmt.multiply(new BigDecimal("100")).divide(sanctionedAmount, 0, RoundingMode.HALF_UP).intValue();
            }

            if (stageAmt.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Stage " + stageNum + " amount must be greater than zero");
            }

            String stageName = config.getStageName() != null && !config.getStageName().isBlank() 
                    ? config.getStageName() 
                    : "Stage " + stageNum + " (" + pct + "%)";

            String desc = config.getDescription() != null && !config.getDescription().isBlank() 
                    ? config.getDescription() 
                    : "Stage " + stageNum + " disbursement tranche";

            LocalDate dueDate = config.getDueDate() != null ? config.getDueDate() : LocalDate.now().plusDays((i + 1) * 30L);
            String compliance = config.getComplianceCondition() != null && !config.getComplianceCondition().isBlank()
                    ? config.getComplianceCondition()
                    : "Standard Compliance Verification";

            String reqDocType = resolveTermDocumentType(application, stageNum);
            createMilestone(disbursement, stageNum, stageName, desc, pct, stageAmt, dueDate, compliance, reqDocType);
        }

        disbursement.setStatus("PLANNED");
        Disbursement saved = disbursementRepository.save(disbursement);

        auditLogger.log("DISBURSEMENT_PLAN_CONFIGURED", "Disbursement", saved.getId(),
                "Configured " + stages.size() + "-stage custom plan for Application: " + application.getApplicationNumber());

        return saved;
    }

    private void createMilestone(Disbursement disbursement, int stageNum, String name, String desc, int pct, BigDecimal amount, LocalDate dueDate, String complianceCondition) {
        String reqDocType = (disbursement != null && disbursement.getApplication() != null)
                ? resolveTermDocumentType(disbursement.getApplication(), stageNum)
                : "UTILIZATION_CERTIFICATE";
        createMilestone(disbursement, stageNum, name, desc, pct, amount, dueDate, complianceCondition, reqDocType);
    }

    private void createMilestone(Disbursement disbursement, int stageNum, String name, String desc, int pct, BigDecimal amount, LocalDate dueDate, String complianceCondition, String requiredDocumentType) {
        Milestone milestone = new Milestone();
        milestone.setDisbursement(disbursement);
        milestone.setStageNumber(stageNum);
        milestone.setMilestoneName(name);
        milestone.setDescription(desc);
        milestone.setPercentage(pct);
        milestone.setMilestoneAmount(amount);
        milestone.setDueDate(dueDate);
        milestone.setComplianceCondition(complianceCondition);
        milestone.setRequiredDocumentType(requiredDocumentType);
        milestone.setCompletionStatus("PENDING");
        milestone.setComplianceStatus("COMPLIANT");
        milestone.setComplianceVerificationStatus("PENDING");
        milestone.setRequiredFlag(true);

        Milestone saved = milestoneRepository.save(milestone);
        if (disbursement.getMilestones() != null && !disbursement.getMilestones().contains(saved)) {
            disbursement.getMilestones().add(saved);
        }
    }

    @Transactional
    public Milestone releaseMilestone(Long milestoneId) {
        StageReleaseDto dto = new StageReleaseDto();
        dto.setMilestoneId(milestoneId);
        return releaseMilestone(dto, "FINANCE_OFFICER");
    }

    @Transactional
    public Milestone releaseMilestone(StageReleaseDto releaseDto, String releasedByUsername) {
        if (releaseDto.getMilestoneId() == null) {
            throw new IllegalArgumentException("Milestone ID is required for release");
        }

        Milestone milestone = milestoneRepository.findById(releaseDto.getMilestoneId())
                .orElseThrow(() -> new ResourceNotFoundException("Milestone stage not found with ID: " + releaseDto.getMilestoneId()));

        Disbursement disbursement = milestone.getDisbursement();
        Application application = disbursement.getApplication();

        // 1. Application status validation
        if (!"APPROVED".equalsIgnoreCase(application.getStatus()) && !"DISBURSEMENT".equalsIgnoreCase(application.getCurrentStage())) {
            throw new IllegalStateException("Fund release is only allowed for Approved applications in Disbursement stage");
        }

        // 2. Stage status validation
        if ("RELEASED".equalsIgnoreCase(milestone.getCompletionStatus()) || "COMPLETED".equalsIgnoreCase(milestone.getCompletionStatus())) {
            throw new IllegalStateException("Stage fund has already been released for stage: " + milestone.getMilestoneName());
        }

        if ("ON_HOLD".equalsIgnoreCase(milestone.getCompletionStatus())) {
            throw new IllegalStateException("Cannot release stage while it is ON HOLD. Please remove hold first.");
        }

        // 2.5 Compliance Verification validation: required compliance must be VERIFIED
        if (Boolean.TRUE.equals(milestone.getRequiredFlag()) && milestone.getRequiredDocumentType() != null && !milestone.getRequiredDocumentType().isBlank()) {
            if (!"VERIFIED".equalsIgnoreCase(milestone.getComplianceVerificationStatus())) {
                auditLogger.log("DISBURSEMENT_RELEASE_BLOCKED_COMPLIANCE", "Milestone", milestone.getId(),
                        "Release blocked: required compliance milestone has not been verified for " + milestone.getMilestoneName());
                throw new IllegalStateException("Release blocked: required compliance milestone has not been verified.");
            }
        }

        // 3. Stage sequence validation: previous stage must be RELEASED/COMPLETED
        List<Milestone> allMilestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        allMilestones.sort(Comparator.comparing(m -> m.getStageNumber() != null ? m.getStageNumber() : m.getId().intValue()));

        int currentIndex = allMilestones.indexOf(milestone);
        if (currentIndex > 0) {
            Milestone previousMilestone = allMilestones.get(currentIndex - 1);
            if (!"RELEASED".equalsIgnoreCase(previousMilestone.getCompletionStatus()) && !"COMPLETED".equalsIgnoreCase(previousMilestone.getCompletionStatus())) {
                throw new IllegalStateException("Previous stage (" + previousMilestone.getMilestoneName() + ") must be released/completed before releasing stage " + milestone.getStageNumber());
            }
        }

        // 4. Release amount validation
        BigDecimal releaseAmount = releaseDto.getReleaseAmount() != null && releaseDto.getReleaseAmount().compareTo(BigDecimal.ZERO) > 0
                ? releaseDto.getReleaseAmount()
                : milestone.getMilestoneAmount();

        BigDecimal currentTotalDisbursed = disbursement.getDisbursedAmount() != null ? disbursement.getDisbursedAmount() : BigDecimal.ZERO;
        BigDecimal totalSanctioned = disbursement.getTotalSanctionedAmount();
        BigDecimal newTotalDisbursed = currentTotalDisbursed.add(releaseAmount);

        if (newTotalDisbursed.compareTo(totalSanctioned) > 0) {
            throw new IllegalArgumentException("Release amount (₹" + releaseAmount + ") exceeds remaining sanctioned balance (₹" + disbursement.getRemainingAmount() + ")");
        }

        // Update Milestone entity
        milestone.setCompletionStatus("COMPLETED");
        milestone.setReleasedAmount(releaseAmount);
        milestone.setReleaseDate(LocalDateTime.now());
        if (releaseDto.getRemarks() != null && !releaseDto.getRemarks().isBlank()) {
            milestone.setRemarks(releaseDto.getRemarks());
        }
        Milestone savedMilestone = milestoneRepository.save(milestone);

        // Update Disbursement entity
        disbursement.setDisbursedAmount(newTotalDisbursed);
        disbursement.setUpdatedAt(LocalDateTime.now());

        boolean allCompleted = allMilestones.stream().allMatch(m -> 
                m.getId().equals(milestone.getId()) || "RELEASED".equalsIgnoreCase(m.getCompletionStatus()) || "COMPLETED".equalsIgnoreCase(m.getCompletionStatus()));

        // Activate Next Term
        Milestone nextMilestone = null;
        for (int i = 0; i < allMilestones.size(); i++) {
            if (allMilestones.get(i).getId().equals(milestone.getId()) && i + 1 < allMilestones.size()) {
                nextMilestone = allMilestones.get(i + 1);
                break;
            }
        }

        if (nextMilestone != null) {
            if (!"VERIFIED".equalsIgnoreCase(nextMilestone.getComplianceVerificationStatus())) {
                nextMilestone.setComplianceVerificationStatus("PENDING");
            }
            if (nextMilestone.getDueDate() == null || nextMilestone.getDueDate().isBefore(LocalDate.now())) {
                nextMilestone.setDueDate(LocalDate.now().plusDays(30));
            }
            if (nextMilestone.getRequiredDocumentType() == null || "UTILIZATION_CERTIFICATE".equalsIgnoreCase(nextMilestone.getRequiredDocumentType())) {
                String sName = application.getScheme() != null ? application.getScheme().getName().toLowerCase() : "";
                if (sName.contains("solar")) {
                    nextMilestone.setRequiredDocumentType(nextMilestone.getStageNumber() == 2 ? "INCOME_CERTIFICATE" : "ELECTRICITY_BILL");
                } else if (sName.contains("education")) {
                    nextMilestone.setRequiredDocumentType(nextMilestone.getStageNumber() == 2 ? "INCOME_CERTIFICATE" : "BONAFIDE_CERTIFICATE");
                } else if (sName.contains("msme")) {
                    nextMilestone.setRequiredDocumentType(nextMilestone.getStageNumber() == 2 ? "UDYAM_CERTIFICATE" : "ENTERPRISE_PROOF");
                } else if (sName.contains("krishi") || sName.contains("irrigation") || sName.contains("farmer")) {
                    nextMilestone.setRequiredDocumentType(nextMilestone.getStageNumber() == 2 ? "INCOME_CERTIFICATE" : "LAND_OWNERSHIP");
                }
            }
            if (nextMilestone.getRequiredDocumentType() != null && !nextMilestone.getRequiredDocumentType().isBlank()) {
                nextMilestone.setRequiredFlag(true);
            } else {
                nextMilestone.setRequiredFlag(false);
            }
            milestoneRepository.save(nextMilestone);

            log.info("[TERM 1 RELEASE] application ID: {}, term number: {}, release status: RELEASED, release date: {}",
                    application.getId(), milestone.getStageNumber(), milestone.getReleaseDate());
            log.info("[TERM 2 ACTIVATION] application ID: {}, term number: {}, generated due date: {}, activation status: ACTIVATED",
                    application.getId(), nextMilestone.getStageNumber(), nextMilestone.getDueDate());

            auditLogger.log("TERM_ACTIVATED", "Milestone", nextMilestone.getId(),
                    "Activated Term " + nextMilestone.getStageNumber() + " for Application #" + application.getApplicationNumber());
            auditLogger.log("TERM_DOCUMENT_REQUIRED", "Milestone", nextMilestone.getId(),
                    "Required document '" + nextMilestone.getRequiredDocumentType() + "' for Term " + nextMilestone.getStageNumber());

            if (application.getBeneficiary() != null && application.getBeneficiary().getUser() != null) {
                notificationService.createNotification(
                        application.getBeneficiary().getUser(), application, "TERM_ACTIVATED",
                        "Term " + nextMilestone.getStageNumber() + " Active — Document Required",
                        "Term " + milestone.getStageNumber() + " of your subsidy application has been released successfully. Term " + nextMilestone.getStageNumber() + " is now active. Please upload the required document (" + nextMilestone.getRequiredDocumentType() + ") before " + nextMilestone.getDueDate() + ".",
                        "HIGH", "/disbursements/schedule/" + application.getId(),
                        "NOTIF_TERM_ACTIVATED_" + nextMilestone.getId() + "_" + System.currentTimeMillis()
                );
            }
        }

        if (allCompleted) {
            disbursement.setStatus("COMPLETED");
            application.setCurrentStage("COMPLETED");
            applicationRepository.save(application);

            auditLogger.log("FINAL_DISBURSEMENT_COMPLETED", "Application", application.getId(),
                    "Final grant release completed for Application #" + application.getApplicationNumber());

            if (application.getBeneficiary() != null && application.getBeneficiary().getUser() != null) {
                notificationService.createNotification(
                        application.getBeneficiary().getUser(), application, "FINAL_DISBURSEMENT_COMPLETED",
                        "Subsidy Fully Disbursed",
                        "Application #" + application.getApplicationNumber() + " has completed all staged grant releases. Total disbursed: ₹" + disbursement.getDisbursedAmount(),
                        "HIGH", "/beneficiary/applications/" + application.getId(),
                        "NOTIF_FINAL_DISBURSEMENT_" + application.getId() + "_" + System.currentTimeMillis()
                );
            }
        } else {
            disbursement.setStatus("PARTIALLY_RELEASED");
        }
        disbursementRepository.save(disbursement);

        // Record Disbursement Release Audit Entry
        String txnRef = releaseDto.getTransactionReference() != null && !releaseDto.getTransactionReference().isBlank()
                ? releaseDto.getTransactionReference()
                : "TXN-" + System.currentTimeMillis();

        String paymentMethod = releaseDto.getPaymentMethod() != null ? releaseDto.getPaymentMethod() : "DBT_BANK_TRANSFER";

        DisbursementRelease releaseRecord = new DisbursementRelease(
                disbursement, savedMilestone, application, releaseAmount, releasedByUsername, txnRef, paymentMethod, releaseDto.getRemarks()
        );
        releaseRepository.save(releaseRecord);

        // Update Scheme Remaining Budget
        Scheme scheme = application.getScheme();
        if (scheme != null) {
            BigDecimal currentRemaining = scheme.getRemainingBudget() != null ? scheme.getRemainingBudget() : scheme.getTotalBudget();
            if (currentRemaining != null) {
                scheme.setRemainingBudget(currentRemaining.subtract(releaseAmount));
                schemeRepository.save(scheme);
            }
        }

        auditLogger.log("TERM_RELEASED", "Milestone", milestone.getId(),
                "Released Term " + milestone.getStageNumber() + " amount ₹" + releaseAmount + " (Txn Ref: " + txnRef + ") for App #" + application.getApplicationNumber());

        return savedMilestone;
    }

    @Transactional
    public Milestone holdMilestone(Long milestoneId, String remarks, String username) {
        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone stage not found with ID: " + milestoneId));

        if ("RELEASED".equalsIgnoreCase(milestone.getCompletionStatus()) || "COMPLETED".equalsIgnoreCase(milestone.getCompletionStatus())) {
            throw new IllegalStateException("Cannot hold an already released milestone stage");
        }

        milestone.setCompletionStatus("ON_HOLD");
        milestone.setRemarks(remarks != null ? remarks : "Stage placed on hold by finance officer");
        Milestone saved = milestoneRepository.save(milestone);

        Disbursement disbursement = milestone.getDisbursement();
        disbursement.setStatus("ON_HOLD");
        disbursementRepository.save(disbursement);

        auditLogger.log("MILESTONE_ON_HOLD", "Milestone", milestone.getId(),
                "Placed stage " + milestone.getMilestoneName() + " on hold: " + remarks);

        return saved;
    }

    @Transactional
    public Milestone unholdMilestone(Long milestoneId, String username) {
        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone stage not found with ID: " + milestoneId));

        if (!"ON_HOLD".equalsIgnoreCase(milestone.getCompletionStatus())) {
            throw new IllegalStateException("Stage is not currently ON HOLD");
        }

        milestone.setCompletionStatus("PENDING");
        milestone.setRemarks("Hold removed");
        Milestone saved = milestoneRepository.save(milestone);

        Disbursement disbursement = milestone.getDisbursement();
        disbursement.setStatus(disbursement.getDisbursedAmount().compareTo(BigDecimal.ZERO) > 0 ? "PARTIALLY_RELEASED" : "PLANNED");
        disbursementRepository.save(disbursement);

        auditLogger.log("MILESTONE_HOLD_REMOVED", "Milestone", milestone.getId(),
                "Resumed stage " + milestone.getMilestoneName());

        return saved;
    }

    @Transactional
    public Disbursement getDisbursementByApplicationId(Long applicationId) {
        Application application = applicationRepository.findById(applicationId).orElse(null);
        if (application == null) {
            return null;
        }

        Optional<Disbursement> disbOpt = disbursementRepository.findByApplicationIdWithMilestones(applicationId);
        Disbursement disbursement;

        if (disbOpt.isPresent()) {
            disbursement = disbOpt.get();
        } else if (application.getSanctionedAmount() != null && application.getSanctionedAmount().compareTo(BigDecimal.ZERO) > 0) {
            disbursement = initializeDisbursement(application, null);
        } else {
            return null;
        }

        if (disbursement != null && application.getSanctionedAmount() != null && application.getSanctionedAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal sanctionedAmount = application.getSanctionedAmount();
            BigDecimal threshold = DISBURSEMENT_SINGLE_RELEASE_THRESHOLD;

            List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
            boolean anyReleased = milestones != null && milestones.stream().anyMatch(m ->
                    "RELEASED".equalsIgnoreCase(m.getCompletionStatus()) || "COMPLETED".equalsIgnoreCase(m.getCompletionStatus()) ||
                    (m.getReleasedAmount() != null && m.getReleasedAmount().compareTo(BigDecimal.ZERO) > 0)
            );

            if (!anyReleased) {
                boolean shouldBeSinglePhase = sanctionedAmount.compareTo(threshold) <= 0;
                if (shouldBeSinglePhase && (milestones == null || milestones.size() != 1 || milestones.get(0).getMilestoneAmount().compareTo(sanctionedAmount) != 0)) {
                    if (milestones != null && !milestones.isEmpty()) {
                        milestoneRepository.deleteAll(milestones);
                        milestoneRepository.flush();
                        if (disbursement.getMilestones() != null) {
                            disbursement.getMilestones().clear();
                        }
                    }
                    disbursement.setTotalSanctionedAmount(sanctionedAmount);
                    createMilestone(disbursement, 1, "Single-Phase Grant Release (100%)",
                            "Single full grant release tranche for sanctioned amount <= ₹" + threshold.toPlainString(),
                            100, sanctionedAmount, LocalDate.now().plusDays(15), "Single-Phase Grant Disbursement Authorization");
                    disbursement = disbursementRepository.saveAndFlush(disbursement);
                } else if (!shouldBeSinglePhase && (milestones == null || milestones.size() == 1)) {
                    if (milestones != null && !milestones.isEmpty()) {
                        milestoneRepository.deleteAll(milestones);
                        milestoneRepository.flush();
                        if (disbursement.getMilestones() != null) {
                            disbursement.getMilestones().clear();
                        }
                    }
                    disbursement.setTotalSanctionedAmount(sanctionedAmount);
                    createThreePhaseMilestones(disbursement, application, sanctionedAmount);
                    disbursement = disbursementRepository.saveAndFlush(disbursement);
                }
            }
        }

        return disbursementRepository.findByApplicationIdWithMilestones(applicationId).orElse(disbursement);
    }

    @Transactional(readOnly = true)
    public List<Disbursement> getAllDisbursementsWithDetails() {
        return disbursementRepository.findAllWithDetails();
    }

    @Transactional(readOnly = true)
    public List<DisbursementRelease> getReleaseHistoryForApplication(Long applicationId) {
        return releaseRepository.findByApplicationIdOrderByReleaseDateDesc(applicationId);
    }

    @Transactional
    public Milestone submitComplianceProof(com.example.digital_subsidy.dto.ComplianceSubmissionDto dto, org.springframework.web.multipart.MultipartFile file, String username) throws java.io.IOException {
        if (dto == null) {
            throw new IllegalArgumentException("Compliance submission details are required.");
        }
        return submitComplianceProof(dto.getMilestoneId(), file, dto.getBeneficiaryRemarks(), username);
    }

    @Transactional
    public Milestone verifyCompliance(com.example.digital_subsidy.dto.ComplianceVerificationDto dto, String officerEmail) {
        if (dto == null) {
            throw new IllegalArgumentException("Compliance verification details are required.");
        }
        return verifyCompliance(dto.getMilestoneId(), dto.getAction(), dto.getRemarks(), officerEmail);
    }

    @Transactional
    public Milestone submitComplianceProof(Long milestoneId, MultipartFile file, String beneficiaryRemarks, String username) throws java.io.IOException {
        return submitComplianceProof(milestoneId, null, file, beneficiaryRemarks, username);
    }

    @Transactional
    public Milestone submitComplianceProof(Long milestoneId, Long applicationId, MultipartFile file, String beneficiaryRemarks, String username) throws java.io.IOException {
        if (milestoneId == null) {
            throw new IllegalArgumentException("Milestone ID is required for compliance proof submission.");
        }

        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone stage not found with ID: " + milestoneId));

        Disbursement disbursement = milestone.getDisbursement();
        Application application = disbursement != null ? disbursement.getApplication() : null;

        if (application == null || application.getBeneficiary() == null || application.getBeneficiary().getUser() == null) {
            throw new IllegalStateException("Application or Beneficiary record is invalid.");
        }

        // Validate that milestone belongs to the requested applicationId
        if (applicationId != null && !application.getId().equals(applicationId)) {
            throw new IllegalArgumentException("Milestone ID " + milestoneId + " does not belong to Application ID " + applicationId);
        }

        // Beneficiary authorization & IDOR check
        String beneficiaryEmail = application.getBeneficiary().getUser().getEmail();
        if (!beneficiaryEmail.equalsIgnoreCase(username)) {
            log.warn("IDOR attempt: User {} tried to submit compliance proof for milestone ID {} owned by {}",
                    username, milestoneId, beneficiaryEmail);
            throw new org.springframework.security.access.AccessDeniedException("Unauthorized to submit compliance proof for this application.");
        }

        // 1. Milestone status check (cannot upload for completed/released milestone)
        if ("RELEASED".equalsIgnoreCase(milestone.getCompletionStatus()) || "COMPLETED".equalsIgnoreCase(milestone.getCompletionStatus())) {
            throw new IllegalStateException("Cannot upload compliance document for an already completed or released term.");
        }

        // 2. Active milestone check (cannot upload for locked future milestone)
        List<Milestone> allMilestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        allMilestones.sort(Comparator.comparing(m -> m.getStageNumber() != null ? m.getStageNumber() : m.getId().intValue()));
        int currentIndex = allMilestones.indexOf(milestone);
        if (currentIndex > 0) {
            Milestone previousMilestone = allMilestones.get(currentIndex - 1);
            if (!"RELEASED".equalsIgnoreCase(previousMilestone.getCompletionStatus()) && !"COMPLETED".equalsIgnoreCase(previousMilestone.getCompletionStatus())) {
                throw new IllegalStateException("Cannot upload document for a locked future term. Previous term (" + previousMilestone.getMilestoneName() + ") must be completed first.");
            }
        }

        // File validation using DocumentService
        String docType = milestone.getRequiredDocumentType() != null ? milestone.getRequiredDocumentType() : "UTILIZATION_CERTIFICATE";
        String validationError = documentService.validateDocument(file, docType);
        if (validationError != null) {
            throw new IllegalArgumentException(validationError);
        }

        // Save compliance document with version/history preservation (old active doc archived)
        Document doc = documentService.saveComplianceDocument(application.getBeneficiary(), application, milestone, docType, file);

        milestone.setSubmittedDocument(doc);
        milestone.setSubmittedDate(LocalDateTime.now());
        milestone.setComplianceVerificationStatus("SUBMITTED");
        milestone.setBeneficiaryRemarks(beneficiaryRemarks);
        Milestone saved = milestoneRepository.save(milestone);

        log.info("[TERM 2 SUBMISSION] application ID: {}, term number: {}, document ID: {}, submission status: SUBMITTED",
                application != null ? application.getId() : null, milestone.getStageNumber(), doc.getId());

        auditLogger.log("COMPLIANCE_PROOF_UPLOADED", "Milestone", milestoneId,
                "Uploaded compliance document '" + doc.getFileName() + "' for milestone: " + milestone.getMilestoneName());

        // Notify officers
        notificationService.notifyRoleUsers("FIELD_OFFICER", 
                application.getBeneficiary().getState(), application.getBeneficiary().getDistrict(),
                application, "COMPLIANCE_SUBMITTED", "Compliance Proof Submitted",
                "Beneficiary uploaded compliance proof for milestone '" + milestone.getMilestoneName() + "' on App #" + application.getApplicationNumber(),
                "MEDIUM", "/disbursements/schedule/" + application.getId(),
                "NOTIF_COMPLIANCE_SUBMITTED_" + milestone.getId() + "_" + System.currentTimeMillis());

        return saved;
    }

    @Transactional
    public Milestone verifyCompliance(Long milestoneId, String action, String remarks, String officerEmail) {
        if (milestoneId == null) {
            throw new IllegalArgumentException("Milestone ID is required for compliance verification.");
        }

        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone stage not found with ID: " + milestoneId));

        Application application = milestone.getDisbursement() != null ? milestone.getDisbursement().getApplication() : null;

        log.info("[TERM 2 WORKFLOW] verifyCompliance called: milestoneId={}, currentStatus={}, action={}, officerEmail={}",
                milestoneId, milestone.getComplianceVerificationStatus(), action, officerEmail);

        if ("VERIFY".equalsIgnoreCase(action) || "VERIFIED".equalsIgnoreCase(action) || "FIELD_VERIFIED".equalsIgnoreCase(action) || "DISTRICT_APPROVED".equalsIgnoreCase(action)) {
            String currentStatus = milestone.getComplianceVerificationStatus();
            
            boolean isFieldOfficer = false;
            boolean isDistrictOfficer = false;

            try {
                org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getAuthorities() != null) {
                    isDistrictOfficer = auth.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equalsIgnoreCase("ROLE_DISTRICT_OFFICER") || a.getAuthority().equalsIgnoreCase("DISTRICT_OFFICER"));
                    isFieldOfficer = auth.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equalsIgnoreCase("ROLE_FIELD_OFFICER") || a.getAuthority().equalsIgnoreCase("FIELD_OFFICER"));
                }
            } catch (Exception ignored) {}

            if (officerRepository != null && officerEmail != null) {
                try {
                    Officer off = officerRepository.findByUserEmail(officerEmail)
                            .or(() -> officerRepository.findByUserEmailIgnoreCase(officerEmail))
                            .orElse(null);
                    if (off != null) {
                        if ("DISTRICT_OFFICER".equalsIgnoreCase(off.getOfficerType())) {
                            isDistrictOfficer = true;
                        } else if ("FIELD_OFFICER".equalsIgnoreCase(off.getOfficerType())) {
                            isFieldOfficer = true;
                        }
                    }
                } catch (Exception ignored) {}
            }

            if ("FIELD_VERIFIED".equalsIgnoreCase(action) || isFieldOfficer) {
                milestone.setComplianceVerificationStatus("FIELD_VERIFIED");
                log.info("[TERM 2 WORKFLOW] Milestone ID {} verified by Field Officer -> status set to FIELD_VERIFIED", milestoneId);
            } else if ("DISTRICT_APPROVED".equalsIgnoreCase(action) || "FIELD_VERIFIED".equalsIgnoreCase(currentStatus) || "PENDING_DISTRICT_APPROVAL".equalsIgnoreCase(currentStatus) || isDistrictOfficer) {
                milestone.setComplianceVerificationStatus("VERIFIED");
                log.info("[TERM 2 WORKFLOW] Milestone ID {} verified by District Officer -> status set to VERIFIED", milestoneId);
            } else {
                String lowerEmail = officerEmail != null ? officerEmail.toLowerCase() : "";
                if (lowerEmail.contains("field") || lowerEmail.contains("fo_") || lowerEmail.contains("fo@")) {
                    milestone.setComplianceVerificationStatus("FIELD_VERIFIED");
                    log.info("[TERM 2 WORKFLOW] Milestone ID {} inferred Field Officer -> status set to FIELD_VERIFIED", milestoneId);
                } else {
                    milestone.setComplianceVerificationStatus("VERIFIED");
                    log.info("[TERM 2 WORKFLOW] Milestone ID {} generic officer -> status set to VERIFIED", milestoneId);
                }
            }

            milestone.setVerifiedDate(LocalDateTime.now());
            milestone.setVerifiedBy(officerEmail);
            if (remarks != null && !remarks.isBlank()) {
                milestone.setOfficerRemarks(remarks);
            }

            if (milestone.getSubmittedDocument() != null) {
                documentService.updateDocumentVerification(milestone.getSubmittedDocument().getId(), "VERIFIED", remarks, officerEmail);
            }

            Milestone saved = milestoneRepository.save(milestone);

            auditLogger.log("COMPLIANCE_VERIFIED", "Milestone", milestoneId,
                    "Verified compliance for milestone: " + milestone.getMilestoneName() + " by " + officerEmail + " (New Status: " + saved.getComplianceVerificationStatus() + ")");

            if (application != null && application.getBeneficiary() != null && application.getBeneficiary().getUser() != null) {
                notificationService.createNotification(
                        application.getBeneficiary().getUser(), application, "COMPLIANCE_VERIFIED",
                        "Compliance Verified",
                        "Compliance for milestone '" + milestone.getMilestoneName() + "' has been verified by the authorized officer.",
                        "MEDIUM", "/disbursements/schedule/" + application.getId(),
                        "NOTIF_COMPLIANCE_VERIFIED_" + milestone.getId() + "_" + System.currentTimeMillis()
                );
            }
            return saved;

        } else if ("REJECT".equalsIgnoreCase(action) || "REUPLOAD_REQUIRED".equalsIgnoreCase(action)) {
            if (remarks == null || remarks.isBlank()) {
                throw new IllegalArgumentException("Re-upload reason / remarks are mandatory when rejecting compliance proof.");
            }

            milestone.setComplianceVerificationStatus("REUPLOAD_REQUIRED");
            milestone.setRejectionReason(remarks);
            milestone.setOfficerRemarks(remarks);

            if (milestone.getSubmittedDocument() != null) {
                documentService.updateDocumentVerification(milestone.getSubmittedDocument().getId(), "REJECTED", remarks, officerEmail);
            }

            Milestone saved = milestoneRepository.save(milestone);

            auditLogger.log("COMPLIANCE_REJECTED", "Milestone", milestoneId,
                    "Rejected compliance proof for milestone: " + milestone.getMilestoneName() + ". Reason: " + remarks);

            if (application != null && application.getBeneficiary() != null && application.getBeneficiary().getUser() != null) {
                notificationService.createNotification(
                        application.getBeneficiary().getUser(), application, "COMPLIANCE_REJECTED",
                        "Action Required: Compliance Proof Rejected",
                        "Compliance proof for milestone '" + milestone.getMilestoneName() + "' requires re-upload. Reason: " + remarks,
                        "HIGH", "/disbursements/schedule/" + application.getId(),
                        "NOTIF_COMPLIANCE_REJECTED_" + milestone.getId() + "_" + System.currentTimeMillis()
                );
            }
            return saved;

        } else {
            throw new IllegalArgumentException("Invalid verification action: " + action + ". Supported actions: VERIFY, REUPLOAD_REQUIRED");
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getFinanceDashboardMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        long approvedAppsCount = applicationRepository.countByCurrentStage("DISBURSEMENT");
        long pendingFinanceCount = applicationRepository.countByCurrentStage("FINANCE_APPROVAL");
        
        BigDecimal totalSanctioned = disbursementRepository.sumTotalSanctionedAmount();
        if (totalSanctioned == null) totalSanctioned = BigDecimal.ZERO;

        BigDecimal totalReleased = disbursementRepository.sumTotalDisbursedAmount();
        if (totalReleased == null) totalReleased = BigDecimal.ZERO;

        BigDecimal remainingBalance = totalSanctioned.subtract(totalReleased);
        if (remainingBalance.compareTo(BigDecimal.ZERO) < 0) remainingBalance = BigDecimal.ZERO;

        long activePlansCount = disbursementRepository.countByStatus("PLANNED") + disbursementRepository.countByStatus("PARTIALLY_RELEASED") + disbursementRepository.countByStatus("IN_PROGRESS");
        long completedCount = disbursementRepository.countByStatus("COMPLETED");

        List<Milestone> allMilestones = milestoneRepository.findAll();
        long compliancePendingCount = allMilestones.stream()
                .filter(m -> "PENDING".equalsIgnoreCase(m.getComplianceVerificationStatus()) || "DUE".equalsIgnoreCase(m.getComplianceVerificationStatus()))
                .count();
        long complianceUnderReviewCount = allMilestones.stream()
                .filter(m -> "SUBMITTED".equalsIgnoreCase(m.getComplianceVerificationStatus()) || "UNDER_REVIEW".equalsIgnoreCase(m.getComplianceVerificationStatus()))
                .count();
        long complianceVerifiedCount = allMilestones.stream()
                .filter(m -> "VERIFIED".equalsIgnoreCase(m.getComplianceVerificationStatus()))
                .count();
        long overdueComplianceCount = allMilestones.stream()
                .filter(m -> "OVERDUE".equalsIgnoreCase(m.getComplianceVerificationStatus()) || "NON_COMPLIANT".equalsIgnoreCase(m.getComplianceVerificationStatus()) || Boolean.TRUE.equals(m.getOverdueFlag()))
                .count();
        long releasesBlockedCount = allMilestones.stream()
                .filter(m -> !"RELEASED".equalsIgnoreCase(m.getCompletionStatus()) && !"COMPLETED".equalsIgnoreCase(m.getCompletionStatus()) && !"VERIFIED".equalsIgnoreCase(m.getComplianceVerificationStatus()))
                .count();

        metrics.put("approvedAppsCount", approvedAppsCount);
        metrics.put("pendingFinanceCount", pendingFinanceCount);
        metrics.put("totalSanctionedAmount", totalSanctioned);
        metrics.put("totalReleasedAmount", totalReleased);
        metrics.put("remainingAmount", remainingBalance);
        metrics.put("activePlansCount", activePlansCount);
        metrics.put("completedDisbursementsCount", completedCount);
        metrics.put("compliancePendingCount", compliancePendingCount);
        metrics.put("complianceUnderReviewCount", complianceUnderReviewCount);
        metrics.put("complianceVerifiedCount", complianceVerifiedCount);
        metrics.put("overdueComplianceCount", overdueComplianceCount);
        metrics.put("releasesBlockedCount", releasesBlockedCount);

        return metrics;
    }

    public BigDecimal getTotalDisbursedAmount() {
        BigDecimal sum = disbursementRepository.sumTotalDisbursedAmount();
        return sum != null ? sum : BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public List<Application> getUnreleasedActiveDisbursementApplications() {
        List<Application> disbursementApps = applicationRepository.findByCurrentStage("DISBURSEMENT");
        List<Disbursement> allDisbursements = disbursementRepository.findAllWithDetails();

        Map<Long, Disbursement> disbMap = new HashMap<>();
        for (Disbursement d : allDisbursements) {
            if (d.getApplication() != null) {
                disbMap.put(d.getApplication().getId(), d);
            }
        }

        List<Application> result = new ArrayList<>();
        for (Application app : disbursementApps) {
            Disbursement d = disbMap.get(app.getId());
            if (d == null) {
                result.add(app);
                continue;
            }

            List<Milestone> rawMilestones = (d.getMilestones() != null && !d.getMilestones().isEmpty())
                    ? d.getMilestones()
                    : milestoneRepository.findByDisbursementOrderByIdAsc(d);

            if (rawMilestones == null || rawMilestones.isEmpty()) {
                result.add(app);
                continue;
            }

            long releasedTerms = rawMilestones.stream().filter(m ->
                    "RELEASED".equalsIgnoreCase(m.getCompletionStatus()) || "COMPLETED".equalsIgnoreCase(m.getCompletionStatus())
            ).count();

            long pendingTerms = rawMilestones.size() - releasedTerms;

            if (releasedTerms == 0 && pendingTerms > 0) {
                result.add(app);
            }
        }

        return result;
    }

    @Transactional(readOnly = true)
    public List<PartiallyReleasedApplicationDto> getPartiallyReleasedApplications() {
        List<Disbursement> allDisbursements = disbursementRepository.findAllWithDetails();
        List<PartiallyReleasedApplicationDto> result = new ArrayList<>();

        for (Disbursement d : allDisbursements) {
            List<Milestone> rawMilestones = (d.getMilestones() != null && !d.getMilestones().isEmpty())
                    ? d.getMilestones()
                    : milestoneRepository.findByDisbursementOrderByIdAsc(d);

            if (rawMilestones == null || rawMilestones.isEmpty()) {
                continue;
            }

            List<Milestone> sortedMilestones = new ArrayList<>(rawMilestones);
            sortedMilestones.sort(Comparator.comparing(m -> m.getStageNumber() != null ? m.getStageNumber() : m.getId().intValue()));

            int totalTerms = sortedMilestones.size();
            int releasedTerms = 0;
            int pendingTerms = 0;
            Milestone nextPendingMilestone = null;

            List<PartiallyReleasedApplicationDto.MilestoneSummaryDto> milestoneDtos = new ArrayList<>();

            for (Milestone m : sortedMilestones) {
                boolean isReleased = "RELEASED".equalsIgnoreCase(m.getCompletionStatus()) || "COMPLETED".equalsIgnoreCase(m.getCompletionStatus());
                if (isReleased) {
                    releasedTerms++;
                } else {
                    pendingTerms++;
                    if (nextPendingMilestone == null) {
                        nextPendingMilestone = m;
                    }
                }
                milestoneDtos.add(new PartiallyReleasedApplicationDto.MilestoneSummaryDto(
                        m.getId(),
                        m.getStageNumber(),
                        m.getMilestoneName(),
                        m.getMilestoneAmount(),
                        m.getCompletionStatus(),
                        m.getDueDate()
                ));
            }

            BigDecimal sanctionedAmount = d.getTotalSanctionedAmount() != null ? d.getTotalSanctionedAmount() : BigDecimal.ZERO;
            BigDecimal disbursedAmount = d.getDisbursedAmount() != null ? d.getDisbursedAmount() : BigDecimal.ZERO;
            BigDecimal remainingAmount = d.getRemainingAmount();

            if (releasedTerms > 0 && pendingTerms > 0 && remainingAmount.compareTo(BigDecimal.ZERO) > 0) {
                Application app = d.getApplication();
                PartiallyReleasedApplicationDto dto = new PartiallyReleasedApplicationDto();
                dto.setApplicationId(app.getId());
                dto.setApplicationNumber(app.getApplicationNumber());

                if (app.getBeneficiary() != null && app.getBeneficiary().getUser() != null) {
                    dto.setBeneficiaryName(app.getBeneficiary().getUser().getFullName());
                } else {
                    dto.setBeneficiaryName("N/A");
                }

                if (app.getBeneficiary() != null) {
                    String idType = app.getBeneficiary().getIdentityType() != null ? app.getBeneficiary().getIdentityType() : "ID";
                    String idNum = app.getBeneficiary().getIdentityNumber() != null ? app.getBeneficiary().getIdentityNumber() : "";
                    dto.setBeneficiaryIdentity(idType + ": " + idNum);
                } else {
                    dto.setBeneficiaryIdentity("N/A");
                }

                dto.setSchemeName(app.getScheme() != null ? app.getScheme().getName() : "N/A");
                dto.setSanctionedAmount(sanctionedAmount);
                dto.setReleasedAmount(disbursedAmount);
                dto.setRemainingAmount(remainingAmount);
                dto.setTotalTerms(totalTerms);
                dto.setReleasedTerms(releasedTerms);
                dto.setPendingTerms(pendingTerms);
                dto.setMilestones(milestoneDtos);

                if (nextPendingMilestone != null) {
                    dto.setNextMilestoneId(nextPendingMilestone.getId());
                    dto.setNextStageNumber(nextPendingMilestone.getStageNumber());
                    dto.setNextMilestoneName(nextPendingMilestone.getMilestoneName());
                    dto.setNextReleaseAmount(nextPendingMilestone.getMilestoneAmount());
                    dto.setNextDueDate(nextPendingMilestone.getDueDate());
                }

                result.add(dto);
            }
        }

        return result;
    }

    @Transactional(readOnly = true)
    public List<Milestone> getVerifiedTermsPendingRelease() {
        List<Milestone> verifiedMilestones = milestoneRepository.findByComplianceVerificationStatus("VERIFIED");
        List<Milestone> result = new ArrayList<>();

        for (Milestone m : verifiedMilestones) {
            if ("RELEASED".equalsIgnoreCase(m.getCompletionStatus()) || "COMPLETED".equalsIgnoreCase(m.getCompletionStatus())) {
                continue;
            }

            Disbursement d = m.getDisbursement();
            if (d == null || d.getApplication() == null) continue;

            Application app = d.getApplication();
            if ("REJECTED".equalsIgnoreCase(app.getStatus()) || "FINAL_REJECTED".equalsIgnoreCase(app.getStatus()) || "COMPLETED".equalsIgnoreCase(app.getCurrentStage())) {
                continue;
            }

            BigDecimal remaining = d.getRemainingAmount();
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) continue;

            List<Milestone> allMilestones = milestoneRepository.findByDisbursementOrderByIdAsc(d);
            allMilestones.sort(Comparator.comparing(ms -> ms.getStageNumber() != null ? ms.getStageNumber() : ms.getId().intValue()));

            int idx = -1;
            for (int i = 0; i < allMilestones.size(); i++) {
                if (allMilestones.get(i).getId().equals(m.getId())) {
                    idx = i;
                    break;
                }
            }

            if (idx == 0) {
                result.add(m);
            } else if (idx > 0) {
                Milestone prev = allMilestones.get(idx - 1);
                if ("RELEASED".equalsIgnoreCase(prev.getCompletionStatus()) || "COMPLETED".equalsIgnoreCase(prev.getCompletionStatus())) {
                    result.add(m);
                }
            }
        }

        log.info("[FINANCE QUERY] number of verified Term records returned: {}", result.size());
        return result;
    }

    @Transactional(readOnly = true)
    public List<Milestone> getPendingTermDocumentVerificationForFieldOfficer(String state, String district) {
        List<String> pendingStatuses = List.of("SUBMITTED", "UNDER_REVIEW", "REUPLOAD_REQUIRED");
        List<Milestone> candidates = new ArrayList<>(milestoneRepository.findByComplianceVerificationStatusIn(pendingStatuses));

        List<Milestone> allMilestones = milestoneRepository.findAll();
        for (Milestone m : allMilestones) {
            if ("RELEASED".equalsIgnoreCase(m.getCompletionStatus()) || "COMPLETED".equalsIgnoreCase(m.getCompletionStatus())) {
                continue;
            }
            if ("VERIFIED".equalsIgnoreCase(m.getComplianceVerificationStatus()) || "FIELD_VERIFIED".equalsIgnoreCase(m.getComplianceVerificationStatus())) {
                continue;
            }
            boolean existsInCandidates = candidates.stream().anyMatch(c -> c.getId().equals(m.getId()));
            if (!existsInCandidates) {
                if (m.getSubmittedDocument() != null) {
                    candidates.add(m);
                } else if (documentRepository != null) {
                    List<Document> docs = documentRepository.findByMilestoneIdOrderByIdDesc(m.getId());
                    if (docs != null && !docs.isEmpty()) {
                        Document activeDoc = docs.stream()
                                .filter(d -> "ACTIVE".equalsIgnoreCase(d.getStatus()) || "SUBMITTED".equalsIgnoreCase(d.getStatus()) || "PENDING".equalsIgnoreCase(d.getStatus()))
                                .findFirst().orElse(null);
                        if (activeDoc != null) {
                            m.setSubmittedDocument(activeDoc);
                            candidates.add(m);
                        }
                    }
                }
            }
        }

        List<Milestone> result = new ArrayList<>();

        for (Milestone m : candidates) {
            if (m.getSubmittedDocument() == null && documentRepository != null) {
                List<Document> docs = documentRepository.findByMilestoneIdOrderByIdDesc(m.getId());
                if (docs != null && !docs.isEmpty()) {
                    Document activeDoc = docs.stream()
                            .filter(d -> "ACTIVE".equalsIgnoreCase(d.getStatus()) || "SUBMITTED".equalsIgnoreCase(d.getStatus()) || "PENDING".equalsIgnoreCase(d.getStatus()))
                            .findFirst().orElse(null);
                    if (activeDoc != null) {
                        m.setSubmittedDocument(activeDoc);
                    }
                }
            }

            if (m.getSubmittedDocument() == null) continue;

            Disbursement d = m.getDisbursement();
            if (d == null || d.getApplication() == null || d.getApplication().getBeneficiary() == null) continue;

            Application app = d.getApplication();
            if ("REJECTED".equalsIgnoreCase(app.getStatus()) || "FINAL_REJECTED".equalsIgnoreCase(app.getStatus()) || "COMPLETED".equalsIgnoreCase(app.getCurrentStage())) {
                continue;
            }

            if ("RELEASED".equalsIgnoreCase(m.getCompletionStatus()) || "COMPLETED".equalsIgnoreCase(m.getCompletionStatus())) {
                continue;
            }

            Beneficiary b = app.getBeneficiary();

            if (state != null && !state.isBlank() && !"ALL".equalsIgnoreCase(state.trim()) && b.getState() != null && !state.trim().equalsIgnoreCase(b.getState().trim())) {
                continue;
            }

            if (district != null && !district.isBlank() && !"ALL".equalsIgnoreCase(district.trim())
                    && !district.toLowerCase().contains("headquarters")
                    && !district.toLowerCase().contains("administration")
                    && !district.toLowerCase().contains("collectorate")) {
                if (b.getDistrict() != null && !b.getDistrict().isBlank()) {
                    if (!district.trim().equalsIgnoreCase(b.getDistrict().trim())) {
                        continue;
                    }
                }
            }

            result.add(m);
        }

        log.info("[FIELD OFFICER QUERY] number of pending Term records returned: {}", result.size());
        return result;
    }

    @Transactional(readOnly = true)
    public List<Milestone> getTermComplianceReviewsForDistrictOfficer(String state, String district) {
        List<String> reviewStatuses = List.of("FIELD_VERIFIED", "PENDING_DISTRICT_APPROVAL");
        List<Milestone> candidates = milestoneRepository.findByComplianceVerificationStatusIn(reviewStatuses);
        List<Milestone> result = new ArrayList<>();

        for (Milestone m : candidates) {
            if (m.getSubmittedDocument() == null && documentRepository != null) {
                List<Document> docs = documentRepository.findByMilestoneIdOrderByIdDesc(m.getId());
                if (docs != null && !docs.isEmpty()) {
                    Document activeDoc = docs.stream()
                            .filter(d -> "ACTIVE".equalsIgnoreCase(d.getStatus()) || "SUBMITTED".equalsIgnoreCase(d.getStatus()) || "PENDING".equalsIgnoreCase(d.getStatus()) || "VERIFIED".equalsIgnoreCase(d.getVerificationStatus()))
                            .findFirst().orElse(null);
                    if (activeDoc != null) {
                        m.setSubmittedDocument(activeDoc);
                    }
                }
            }

            if (m.getSubmittedDocument() == null) continue;

            Disbursement d = m.getDisbursement();
            if (d == null || d.getApplication() == null || d.getApplication().getBeneficiary() == null) continue;

            Application app = d.getApplication();
            if ("REJECTED".equalsIgnoreCase(app.getStatus()) || "FINAL_REJECTED".equalsIgnoreCase(app.getStatus()) || "COMPLETED".equalsIgnoreCase(app.getCurrentStage())) {
                continue;
            }

            if ("RELEASED".equalsIgnoreCase(m.getCompletionStatus()) || "COMPLETED".equalsIgnoreCase(m.getCompletionStatus())) {
                continue;
            }

            Beneficiary b = app.getBeneficiary();

            if (state != null && !state.isBlank() && !"ALL".equalsIgnoreCase(state.trim()) && b.getState() != null && !state.trim().equalsIgnoreCase(b.getState().trim())) {
                continue;
            }

            if (district != null && !district.isBlank() && !"ALL".equalsIgnoreCase(district.trim())
                    && !district.toLowerCase().contains("headquarters")
                    && !district.toLowerCase().contains("administration")
                    && !district.toLowerCase().contains("collectorate")) {
                if (b.getDistrict() != null && !b.getDistrict().isBlank()) {
                    if (!district.trim().equalsIgnoreCase(b.getDistrict().trim())) {
                        continue;
                    }
                }
            }

            result.add(m);
        }

        log.info("[DISTRICT OFFICER QUERY] number of pending Term records returned: {}", result.size());
        return result;
    }

    @Transactional(readOnly = true)
    public List<Milestone> getActionRequiredTermMilestonesForBeneficiary(Long beneficiaryId) {
        List<Application> apps = applicationRepository.findByBeneficiaryId(beneficiaryId);
        List<Milestone> result = new ArrayList<>();

        for (Application app : apps) {
            if ("REJECTED".equalsIgnoreCase(app.getStatus()) || "FINAL_REJECTED".equalsIgnoreCase(app.getStatus()) || "COMPLETED".equalsIgnoreCase(app.getStatus())) {
                continue;
            }

            Disbursement d = disbursementRepository.findByApplication(app).orElse(null);
            if (d == null) continue;

            List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(d);
            milestones.sort(Comparator.comparing(m -> m.getStageNumber() != null ? m.getStageNumber() : m.getId().intValue()));

            for (int i = 0; i < milestones.size(); i++) {
                Milestone m = milestones.get(i);
                boolean isReleased = "RELEASED".equalsIgnoreCase(m.getCompletionStatus()) || "COMPLETED".equalsIgnoreCase(m.getCompletionStatus());

                if (isReleased) continue;

                boolean prevReleased = (i == 0) || ("RELEASED".equalsIgnoreCase(milestones.get(i - 1).getCompletionStatus()) || "COMPLETED".equalsIgnoreCase(milestones.get(i - 1).getCompletionStatus()));

                if (prevReleased) {
                    if (Boolean.TRUE.equals(m.getRequiredFlag())) {
                        result.add(m);
                    }
                    break;
                }
            }
        }

        return result;
    }
}
