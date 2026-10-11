package com.example.digital_subsidy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class TermDetailDto {

    private Long milestoneId;
    private Integer stageNumber;
    private String milestoneName;
    private BigDecimal milestoneAmount = BigDecimal.ZERO;
    private BigDecimal releasedAmount = BigDecimal.ZERO;
    private BigDecimal remainingAmount = BigDecimal.ZERO;
    private String completionStatus;
    private String complianceVerificationStatus;
    private String requiredDocumentType;
    private LocalDate dueDate;
    private LocalDateTime releaseDate;

    public TermDetailDto() {}

    public Long getMilestoneId() {
        return milestoneId;
    }

    public void setMilestoneId(Long milestoneId) {
        this.milestoneId = milestoneId;
    }

    public Integer getStageNumber() {
        return stageNumber;
    }

    public void setStageNumber(Integer stageNumber) {
        this.stageNumber = stageNumber;
    }

    public String getMilestoneName() {
        return milestoneName;
    }

    public void setMilestoneName(String milestoneName) {
        this.milestoneName = milestoneName;
    }

    public BigDecimal getMilestoneAmount() {
        return milestoneAmount;
    }

    public void setMilestoneAmount(BigDecimal milestoneAmount) {
        this.milestoneAmount = milestoneAmount != null ? milestoneAmount : BigDecimal.ZERO;
    }

    public BigDecimal getReleasedAmount() {
        return releasedAmount;
    }

    public void setReleasedAmount(BigDecimal releasedAmount) {
        this.releasedAmount = releasedAmount != null ? releasedAmount : BigDecimal.ZERO;
    }

    public BigDecimal getRemainingAmount() {
        return remainingAmount;
    }

    public void setRemainingAmount(BigDecimal remainingAmount) {
        this.remainingAmount = remainingAmount != null ? remainingAmount : BigDecimal.ZERO;
    }

    public String getCompletionStatus() {
        return completionStatus;
    }

    public void setCompletionStatus(String completionStatus) {
        this.completionStatus = completionStatus;
    }

    public String getComplianceVerificationStatus() {
        return complianceVerificationStatus;
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

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public LocalDateTime getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(LocalDateTime releaseDate) {
        this.releaseDate = releaseDate;
    }
}
