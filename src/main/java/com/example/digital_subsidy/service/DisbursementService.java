package com.example.digital_subsidy.service;

import com.example.digital_subsidy.exception.ResourceNotFoundException;
import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.DisbursementRepository;
import com.example.digital_subsidy.repository.MilestoneRepository;
import com.example.digital_subsidy.repository.SchemeRepository;
import com.example.digital_subsidy.util.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DisbursementService {

    private final DisbursementRepository disbursementRepository;
    private final MilestoneRepository milestoneRepository;
    private final com.example.digital_subsidy.repository.DisbursementMilestoneRepository schemeMilestoneRepository;
    private final SchemeRepository schemeRepository;
    private final AuditLogger auditLogger;

    public DisbursementService(DisbursementRepository disbursementRepository,
                               MilestoneRepository milestoneRepository,
                               com.example.digital_subsidy.repository.DisbursementMilestoneRepository schemeMilestoneRepository,
                               SchemeRepository schemeRepository,
                               AuditLogger auditLogger) {
        this.disbursementRepository = disbursementRepository;
        this.milestoneRepository = milestoneRepository;
        this.schemeMilestoneRepository = schemeMilestoneRepository;
        this.schemeRepository = schemeRepository;
        this.auditLogger = auditLogger;
    }

    @Transactional
    public Disbursement initializeDisbursement(Application application, User approver) {
        Disbursement disbursement = new Disbursement();
        disbursement.setApplication(application);
        disbursement.setTotalSanctionedAmount(application.getRequestedAmount());
        disbursement.setDisbursedAmount(BigDecimal.ZERO);
        disbursement.setStatus("IN_PROGRESS");
        disbursement.setApprovedBy(approver);

        Disbursement savedDisbursement = disbursementRepository.save(disbursement);

        // Generate Staged Milestones based on Scheme Configuration
        List<com.example.digital_subsidy.model.DisbursementMilestone> schemeMilestones = 
                schemeMilestoneRepository.findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(application.getScheme().getId());

        BigDecimal total = application.getRequestedAmount();

        if (schemeMilestones != null && !schemeMilestones.isEmpty()) {
            BigDecimal allocated = BigDecimal.ZERO;
            for (int i = 0; i < schemeMilestones.size(); i++) {
                com.example.digital_subsidy.model.DisbursementMilestone sm = schemeMilestones.get(i);
                BigDecimal amt;
                if (i == schemeMilestones.size() - 1) {
                    amt = total.subtract(allocated);
                } else {
                    amt = total.multiply(new BigDecimal(sm.getPercentage())).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                    allocated = allocated.add(amt);
                }
                String name = sm.getMilestoneName() + " (" + sm.getPercentage() + "%)";
                String desc = sm.getDescription() != null ? sm.getDescription() : "Tranche " + (i + 1) + " for " + sm.getMilestoneName();
                createMilestone(savedDisbursement, name, desc, sm.getPercentage(), amt, LocalDate.now().plusDays((i + 1) * 30L));
            }
        } else {
            // Fallback default 30% - 40% - 30%
            BigDecimal m1Amt = total.multiply(new BigDecimal("0.30")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal m2Amt = total.multiply(new BigDecimal("0.40")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal m3Amt = total.subtract(m1Amt).subtract(m2Amt);

            createMilestone(savedDisbursement, "Milestone 1 - Initial Sanction (30%)", "First tranche upon sanction approval", 30, m1Amt, LocalDate.now().plusDays(15));
            createMilestone(savedDisbursement, "Milestone 2 - Mid-Term Compliance (40%)", "Second tranche upon 50% fund utilization proof", 40, m2Amt, LocalDate.now().plusDays(45));
            createMilestone(savedDisbursement, "Milestone 3 - Final Completion (30%)", "Final tranche upon project completion certificate", 30, m3Amt, LocalDate.now().plusDays(90));
        }

        auditLogger.log("DISBURSEMENT_SCHEDULED", "Disbursement", savedDisbursement.getId(), 
                "Scheduled 3 milestone disbursements for Application: " + application.getApplicationNumber());

        return savedDisbursement;
    }

    private void createMilestone(Disbursement disbursement, String name, String desc, int pct, BigDecimal amount, LocalDate dueDate) {
        Milestone milestone = new Milestone();
        milestone.setDisbursement(disbursement);
        milestone.setMilestoneName(name);
        milestone.setDescription(desc);
        milestone.setPercentage(pct);
        milestone.setMilestoneAmount(amount);
        milestone.setDueDate(dueDate);
        milestone.setCompletionStatus("PENDING");
        milestone.setComplianceStatus("COMPLIANT");
        milestoneRepository.save(milestone);
    }

    @Transactional
    public Milestone releaseMilestone(Long milestoneId) {
        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found with ID: " + milestoneId));

        if ("COMPLETED".equalsIgnoreCase(milestone.getCompletionStatus())) {
            throw new IllegalStateException("Milestone fund has already been released");
        }

        milestone.setCompletionStatus("COMPLETED");
        milestone.setReleasedAmount(milestone.getMilestoneAmount());
        milestone.setReleaseDate(LocalDateTime.now());
        Milestone savedMilestone = milestoneRepository.save(milestone);

        // Update total disbursed amount in Disbursement
        Disbursement disbursement = milestone.getDisbursement();
        BigDecimal newTotalDisbursed = disbursement.getDisbursedAmount().add(milestone.getMilestoneAmount());
        disbursement.setDisbursedAmount(newTotalDisbursed);

        // Check if all milestones completed
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        boolean allDone = milestones.stream().allMatch(m -> "COMPLETED".equalsIgnoreCase(m.getCompletionStatus()));
        if (allDone) {
            disbursement.setStatus("COMPLETED");
            disbursement.getApplication().setCurrentStage("COMPLETED");
        }
        disbursementRepository.save(disbursement);

        // Update Scheme Remaining Budget
        Scheme scheme = disbursement.getApplication().getScheme();
        BigDecimal currentRemaining = scheme.getRemainingBudget() != null ? scheme.getRemainingBudget() : scheme.getTotalBudget();
        scheme.setRemainingBudget(currentRemaining.subtract(milestone.getMilestoneAmount()));
        schemeRepository.save(scheme);

        auditLogger.log("MILESTONE_RELEASED", "Milestone", milestone.getId(), 
                "Released ₹" + milestone.getMilestoneAmount() + " for " + milestone.getMilestoneName());

        return savedMilestone;
    }

    @Transactional(readOnly = true)
    public Disbursement getDisbursementByApplicationId(Long applicationId) {
        return disbursementRepository.findByApplicationIdWithMilestones(applicationId)
                .orElse(null);
    }

    public BigDecimal getTotalDisbursedAmount() {
        BigDecimal sum = disbursementRepository.sumTotalDisbursedAmount();
        return sum != null ? sum : BigDecimal.ZERO;
    }
}
