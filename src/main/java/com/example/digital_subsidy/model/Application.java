package com.example.digital_subsidy.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "applications", indexes = {
    @Index(name = "idx_app_stage_priority", columnList = "current_stage, priority_level, priority_score"),
    @Index(name = "idx_app_status_priority", columnList = "status, priority_score")
})
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_number", nullable = false, unique = true, length = 50)
    private String applicationNumber;

    @ManyToOne(optional = false)
    @JoinColumn(name = "beneficiary_id", nullable = false)
    private Beneficiary beneficiary;

    @ManyToOne(optional = false)
    @JoinColumn(name = "scheme_id", nullable = false)
    private Scheme scheme;

    @Column(name = "requested_amount", nullable = false)
    private BigDecimal requestedAmount;

    @Column(name = "eligibility_score")
    private Integer eligibilityScore;

    @Column(name = "current_stage", nullable = false, length = 50)
    private String currentStage = "SUBMITTED";

    @Column(nullable = false, length = 50)
    private String status = "PENDING"; // PENDING, APPROVED, REJECTED, RE_VERIFICATION_REQUIRED

    @ManyToOne
    @JoinColumn(name = "assigned_officer_id")
    private Officer assignedOfficer;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "sanctioned_amount")
    private BigDecimal sanctionedAmount;

    @Column(name = "amount_adjustment_reason", columnDefinition = "TEXT")
    private String amountAdjustmentReason;

    @Column(name = "sanctioned_by", length = 100)
    private String sanctionedBy;

    @Column(name = "sanctioned_date")
    private LocalDateTime sanctionedDate;

    @Column(name = "finance_approval_status", length = 50)
    private String financeApprovalStatus;

    @Column(name = "finance_approved_by", length = 100)
    private String financeApprovedBy;

    @Column(name = "finance_approved_date")
    private LocalDateTime financeApprovedDate;

    @Column(name = "priority_score", precision = 5, scale = 1)
    private BigDecimal priorityScore = BigDecimal.ZERO;

    @Column(name = "priority_level", length = 20)
    private String priorityLevel = "STANDARD"; // HIGH, MEDIUM, STANDARD

    @Column(name = "priority_calculated_at")
    private LocalDateTime priorityCalculatedAt;

    @Column(name = "priority_breakdown_json", columnDefinition = "TEXT")
    private String priorityBreakdownJson;

    @Column(name = "is_priority_overridden", nullable = false)
    private Boolean isPriorityOverridden = false;

    @Column(name = "priority_override_reason", columnDefinition = "TEXT")
    private String priorityOverrideReason;

    @Column(name = "priority_overridden_by", length = 100)
    private String priorityOverriddenBy;

    @Column(name = "priority_overridden_at")
    private LocalDateTime priorityOverriddenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Officer getAssignedOfficer() {
        return assignedOfficer;
    }

    public void setAssignedOfficer(Officer assignedOfficer) {
        this.assignedOfficer = assignedOfficer;
    }

    public Application() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getApplicationNumber() {
        return applicationNumber;
    }

    public void setApplicationNumber(String applicationNumber) {
        this.applicationNumber = applicationNumber;
    }

    public Beneficiary getBeneficiary() {
        return beneficiary;
    }

    public void setBeneficiary(Beneficiary beneficiary) {
        this.beneficiary = beneficiary;
    }

    public Scheme getScheme() {
        return scheme;
    }

    public void setScheme(Scheme scheme) {
        this.scheme = scheme;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public void setRequestedAmount(BigDecimal requestedAmount) {
        this.requestedAmount = requestedAmount;
    }

    public BigDecimal getSanctionedAmount() {
        return sanctionedAmount;
    }

    public void setSanctionedAmount(BigDecimal sanctionedAmount) {
        this.sanctionedAmount = sanctionedAmount;
    }

    public String getAmountAdjustmentReason() {
        return amountAdjustmentReason;
    }

    public void setAmountAdjustmentReason(String amountAdjustmentReason) {
        this.amountAdjustmentReason = amountAdjustmentReason;
    }

    public String getSanctionedBy() {
        return sanctionedBy;
    }

    public void setSanctionedBy(String sanctionedBy) {
        this.sanctionedBy = sanctionedBy;
    }

    public LocalDateTime getSanctionedDate() {
        return sanctionedDate;
    }

    public void setSanctionedDate(LocalDateTime sanctionedDate) {
        this.sanctionedDate = sanctionedDate;
    }

    public String getFinanceApprovalStatus() {
        return financeApprovalStatus;
    }

    public void setFinanceApprovalStatus(String financeApprovalStatus) {
        this.financeApprovalStatus = financeApprovalStatus;
    }

    public String getFinanceApprovedBy() {
        return financeApprovedBy;
    }

    public void setFinanceApprovedBy(String financeApprovedBy) {
        this.financeApprovedBy = financeApprovedBy;
    }

    public LocalDateTime getFinanceApprovedDate() {
        return financeApprovedDate;
    }

    public void setFinanceApprovedDate(LocalDateTime financeApprovedDate) {
        this.financeApprovedDate = financeApprovedDate;
    }

    public Integer getEligibilityScore() {
        return eligibilityScore;
    }

    public void setEligibilityScore(Integer eligibilityScore) {
        this.eligibilityScore = eligibilityScore;
    }

    public String getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(String currentStage) {
        this.currentStage = currentStage;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public BigDecimal getPriorityScore() {
        return priorityScore != null ? priorityScore : BigDecimal.ZERO;
    }

    public void setPriorityScore(BigDecimal priorityScore) {
        this.priorityScore = priorityScore;
    }

    public String getPriorityLevel() {
        return priorityLevel != null ? priorityLevel : "STANDARD";
    }

    public void setPriorityLevel(String priorityLevel) {
        this.priorityLevel = priorityLevel;
    }

    public LocalDateTime getPriorityCalculatedAt() {
        return priorityCalculatedAt;
    }

    public void setPriorityCalculatedAt(LocalDateTime priorityCalculatedAt) {
        this.priorityCalculatedAt = priorityCalculatedAt;
    }

    public String getPriorityBreakdownJson() {
        return priorityBreakdownJson;
    }

    public void setPriorityBreakdownJson(String priorityBreakdownJson) {
        this.priorityBreakdownJson = priorityBreakdownJson;
    }

    public Boolean getIsPriorityOverridden() {
        return isPriorityOverridden != null ? isPriorityOverridden : false;
    }

    public void setIsPriorityOverridden(Boolean priorityOverridden) {
        isPriorityOverridden = priorityOverridden;
    }

    public String getPriorityOverrideReason() {
        return priorityOverrideReason;
    }

    public void setPriorityOverrideReason(String priorityOverrideReason) {
        this.priorityOverrideReason = priorityOverrideReason;
    }

    public String getPriorityOverriddenBy() {
        return priorityOverriddenBy;
    }

    public void setPriorityOverriddenBy(String priorityOverriddenBy) {
        this.priorityOverriddenBy = priorityOverriddenBy;
    }

    public LocalDateTime getPriorityOverriddenAt() {
        return priorityOverriddenAt;
    }

    public void setPriorityOverriddenAt(LocalDateTime priorityOverriddenAt) {
        this.priorityOverriddenAt = priorityOverriddenAt;
    }
}
