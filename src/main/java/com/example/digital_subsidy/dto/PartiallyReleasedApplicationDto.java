package com.example.digital_subsidy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PartiallyReleasedApplicationDto {
    private Long applicationId;
    private String applicationNumber;
    private String beneficiaryName;
    private String beneficiaryIdentity;
    private String schemeName;
    private BigDecimal sanctionedAmount = BigDecimal.ZERO;
    private BigDecimal releasedAmount = BigDecimal.ZERO;
    private BigDecimal remainingAmount = BigDecimal.ZERO;
    private int totalTerms;
    private int releasedTerms;
    private int pendingTerms;

    private Long nextMilestoneId;
    private Integer nextStageNumber;
    private String nextMilestoneName;
    private BigDecimal nextReleaseAmount = BigDecimal.ZERO;
    private LocalDate nextDueDate;

    private List<MilestoneSummaryDto> milestones = new ArrayList<>();

    public PartiallyReleasedApplicationDto() {}

    public static class MilestoneSummaryDto {
        private Long id;
        private Integer stageNumber;
        private String milestoneName;
        private BigDecimal milestoneAmount;
        private String completionStatus;
        private LocalDate dueDate;

        public MilestoneSummaryDto() {}

        public MilestoneSummaryDto(Long id, Integer stageNumber, String milestoneName, BigDecimal milestoneAmount, String completionStatus, LocalDate dueDate) {
            this.id = id;
            this.stageNumber = stageNumber;
            this.milestoneName = milestoneName;
            this.milestoneAmount = milestoneAmount;
            this.completionStatus = completionStatus;
            this.dueDate = dueDate;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public Integer getStageNumber() { return stageNumber; }
        public void setStageNumber(Integer stageNumber) { this.stageNumber = stageNumber; }

        public String getMilestoneName() { return milestoneName; }
        public void setMilestoneName(String milestoneName) { this.milestoneName = milestoneName; }

        public BigDecimal getMilestoneAmount() { return milestoneAmount; }
        public void setMilestoneAmount(BigDecimal milestoneAmount) { this.milestoneAmount = milestoneAmount; }

        public String getCompletionStatus() { return completionStatus; }
        public void setCompletionStatus(String completionStatus) { this.completionStatus = completionStatus; }

        public LocalDate getDueDate() { return dueDate; }
        public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    }

    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }

    public String getApplicationNumber() { return applicationNumber; }
    public void setApplicationNumber(String applicationNumber) { this.applicationNumber = applicationNumber; }

    public String getBeneficiaryName() { return beneficiaryName; }
    public void setBeneficiaryName(String beneficiaryName) { this.beneficiaryName = beneficiaryName; }

    public String getBeneficiaryIdentity() { return beneficiaryIdentity; }
    public void setBeneficiaryIdentity(String beneficiaryIdentity) { this.beneficiaryIdentity = beneficiaryIdentity; }

    public String getSchemeName() { return schemeName; }
    public void setSchemeName(String schemeName) { this.schemeName = schemeName; }

    public BigDecimal getSanctionedAmount() { return sanctionedAmount; }
    public void setSanctionedAmount(BigDecimal sanctionedAmount) { this.sanctionedAmount = sanctionedAmount; }

    public BigDecimal getReleasedAmount() { return releasedAmount; }
    public void setReleasedAmount(BigDecimal releasedAmount) { this.releasedAmount = releasedAmount; }

    public BigDecimal getRemainingAmount() { return remainingAmount; }
    public void setRemainingAmount(BigDecimal remainingAmount) { this.remainingAmount = remainingAmount; }

    public int getTotalTerms() { return totalTerms; }
    public void setTotalTerms(int totalTerms) { this.totalTerms = totalTerms; }

    public int getReleasedTerms() { return releasedTerms; }
    public void setReleasedTerms(int releasedTerms) { this.releasedTerms = releasedTerms; }

    public int getPendingTerms() { return pendingTerms; }
    public void setPendingTerms(int pendingTerms) { this.pendingTerms = pendingTerms; }

    public Long getNextMilestoneId() { return nextMilestoneId; }
    public void setNextMilestoneId(Long nextMilestoneId) { this.nextMilestoneId = nextMilestoneId; }

    public Integer getNextStageNumber() { return nextStageNumber; }
    public void setNextStageNumber(Integer nextStageNumber) { this.nextStageNumber = nextStageNumber; }

    public String getNextMilestoneName() { return nextMilestoneName; }
    public void setNextMilestoneName(String nextMilestoneName) { this.nextMilestoneName = nextMilestoneName; }

    public BigDecimal getNextReleaseAmount() { return nextReleaseAmount; }
    public void setNextReleaseAmount(BigDecimal nextReleaseAmount) { this.nextReleaseAmount = nextReleaseAmount; }

    public LocalDate getNextDueDate() { return nextDueDate; }
    public void setNextDueDate(LocalDate nextDueDate) { this.nextDueDate = nextDueDate; }

    public List<MilestoneSummaryDto> getMilestones() { return milestones; }
    public void setMilestones(List<MilestoneSummaryDto> milestones) { this.milestones = milestones; }
}
