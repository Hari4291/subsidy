package com.example.digital_subsidy.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "milestones")
public class Milestone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "disbursement_id", nullable = false)
    private Disbursement disbursement;

    @Column(name = "stage_number")
    private Integer stageNumber = 1;

    @Column(name = "milestone_name", nullable = false, length = 100)
    private String milestoneName; // e.g. "Milestone 1 - Initial Grant (30%)"

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "compliance_condition", length = 255)
    private String complianceCondition;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(nullable = false)
    private Integer percentage;

    @Column(name = "milestone_amount", nullable = false)
    private BigDecimal milestoneAmount;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "completion_status", nullable = false, length = 30)
    private String completionStatus = "PENDING"; // PENDING, READY_FOR_RELEASE, RELEASED, ON_HOLD, COMPLETED, CANCELLED

    @Column(name = "compliance_status", nullable = false, length = 30)
    private String complianceStatus = "COMPLIANT"; // COMPLIANT, OVERDUE, NON_COMPLIANT

    @Column(name = "released_amount")
    private BigDecimal releasedAmount = BigDecimal.ZERO;

    @Column(name = "release_date")
    private LocalDateTime releaseDate;

    // ── Phase 2 Compliance Tracking Fields ──
    @Column(name = "compliance_verification_status", nullable = false, length = 40)
    private String complianceVerificationStatus = "PENDING"; // PENDING, DUE, SUBMITTED, UNDER_REVIEW, VERIFIED, REJECTED, REUPLOAD_REQUIRED, OVERDUE, NON_COMPLIANT

    @Column(name = "required_document_type", length = 100)
    private String requiredDocumentType = "UTILIZATION_CERTIFICATE";

    @Column(name = "required_flag", nullable = false)
    private Boolean requiredFlag = Boolean.TRUE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document submittedDocument;

    @Column(name = "submitted_date")
    private LocalDateTime submittedDate;

    @Column(name = "verified_date")
    private LocalDateTime verifiedDate;

    @Column(name = "verified_by", length = 100)
    private String verifiedBy;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "beneficiary_remarks", columnDefinition = "TEXT")
    private String beneficiaryRemarks;

    @Column(name = "officer_remarks", columnDefinition = "TEXT")
    private String officerRemarks;

    @Column(name = "reminder_status", length = 50)
    private String reminderStatus = "NOT_SENT";

    @Column(name = "last_reminder_date")
    private LocalDateTime lastReminderDate;

    @Column(name = "next_reminder_date")
    private LocalDate nextReminderDate;

    @Column(name = "overdue_flag", nullable = false)
    private Boolean overdueFlag = Boolean.FALSE;

    public Milestone() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getStageNumber() {
        return stageNumber;
    }

    public void setStageNumber(Integer stageNumber) {
        this.stageNumber = stageNumber;
    }

    public Disbursement getDisbursement() {
        return disbursement;
    }

    public void setDisbursement(Disbursement disbursement) {
        this.disbursement = disbursement;
    }

    public String getMilestoneName() {
        return milestoneName;
    }

    public void setMilestoneName(String milestoneName) {
        this.milestoneName = milestoneName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getComplianceCondition() {
        return complianceCondition;
    }

    public void setComplianceCondition(String complianceCondition) {
        this.complianceCondition = complianceCondition;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public Integer getPercentage() {
        return percentage;
    }

    public void setPercentage(Integer percentage) {
        this.percentage = percentage;
    }

    public BigDecimal getMilestoneAmount() {
        return milestoneAmount;
    }

    public void setMilestoneAmount(BigDecimal milestoneAmount) {
        this.milestoneAmount = milestoneAmount;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getCompletionStatus() {
        return completionStatus;
    }

    public void setCompletionStatus(String completionStatus) {
        this.completionStatus = completionStatus;
    }

    public String getComplianceStatus() {
        return complianceStatus;
    }

    public void setComplianceStatus(String complianceStatus) {
        this.complianceStatus = complianceStatus;
    }

    public BigDecimal getReleasedAmount() {
        return releasedAmount;
    }

    public void setReleasedAmount(BigDecimal releasedAmount) {
        this.releasedAmount = releasedAmount;
    }

    public LocalDateTime getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(LocalDateTime releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getComplianceVerificationStatus() {
        return complianceVerificationStatus != null ? complianceVerificationStatus : "PENDING";
    }

    public void setComplianceVerificationStatus(String complianceVerificationStatus) {
        this.complianceVerificationStatus = complianceVerificationStatus;
    }

    public String getRequiredDocumentType() {
        return requiredDocumentType;
    }

    public void setRequiredDocumentType(String requiredDocumentType) {
        this.requiredDocumentType = requiredDocumentType;
    }

    public Boolean getRequiredFlag() {
        return requiredFlag != null ? requiredFlag : true;
    }

    public void setRequiredFlag(Boolean requiredFlag) {
        this.requiredFlag = requiredFlag;
    }

    public Document getSubmittedDocument() {
        return submittedDocument;
    }

    public void setSubmittedDocument(Document submittedDocument) {
        this.submittedDocument = submittedDocument;
    }

    public LocalDateTime getSubmittedDate() {
        return submittedDate;
    }

    public void setSubmittedDate(LocalDateTime submittedDate) {
        this.submittedDate = submittedDate;
    }

    public LocalDateTime getVerifiedDate() {
        return verifiedDate;
    }

    public void setVerifiedDate(LocalDateTime verifiedDate) {
        this.verifiedDate = verifiedDate;
    }

    public String getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(String verifiedBy) {
        this.verifiedBy = verifiedBy;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getBeneficiaryRemarks() {
        return beneficiaryRemarks;
    }

    public void setBeneficiaryRemarks(String beneficiaryRemarks) {
        this.beneficiaryRemarks = beneficiaryRemarks;
    }

    public String getOfficerRemarks() {
        return officerRemarks;
    }

    public void setOfficerRemarks(String officerRemarks) {
        this.officerRemarks = officerRemarks;
    }

    public String getReminderStatus() {
        return reminderStatus != null ? reminderStatus : "NOT_SENT";
    }

    public void setReminderStatus(String reminderStatus) {
        this.reminderStatus = reminderStatus;
    }

    public LocalDateTime getLastReminderDate() {
        return lastReminderDate;
    }

    public void setLastReminderDate(LocalDateTime lastReminderDate) {
        this.lastReminderDate = lastReminderDate;
    }

    public LocalDate getNextReminderDate() {
        return nextReminderDate;
    }

    public void setNextReminderDate(LocalDate nextReminderDate) {
        this.nextReminderDate = nextReminderDate;
    }

    public Boolean getOverdueFlag() {
        return overdueFlag != null ? overdueFlag : false;
    }

    public void setOverdueFlag(Boolean overdueFlag) {
        this.overdueFlag = overdueFlag;
    }
}
