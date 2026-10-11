package com.example.digital_subsidy.dto;

import java.math.BigDecimal;

public class TermLevelSummaryDto {

    private int stageNumber;
    private String stageName;
    private long applicationCount = 0;
    private BigDecimal totalSanctioned = BigDecimal.ZERO;
    private BigDecimal totalReleased = BigDecimal.ZERO;
    private BigDecimal totalPending = BigDecimal.ZERO;
    private long completedCount = 0;

    public TermLevelSummaryDto() {}

    public TermLevelSummaryDto(int stageNumber, String stageName) {
        this.stageNumber = stageNumber;
        this.stageName = stageName;
    }

    public int getStageNumber() {
        return stageNumber;
    }

    public void setStageNumber(int stageNumber) {
        this.stageNumber = stageNumber;
    }

    public String getStageName() {
        return stageName;
    }

    public void setStageName(String stageName) {
        this.stageName = stageName;
    }

    public String getTermName() {
        if (stageName != null && !stageName.trim().isEmpty()) {
            return stageName;
        }
        return "Term " + stageNumber;
    }

    public void setTermName(String termName) {
        this.stageName = termName;
    }

    public long getApplicationCount() {
        return applicationCount;
    }

    public void setApplicationCount(long applicationCount) {
        this.applicationCount = applicationCount;
    }

    public long getTotalApplications() {
        return applicationCount;
    }

    public void setTotalApplications(long totalApplications) {
        this.applicationCount = totalApplications;
    }

    public BigDecimal getTotalSanctioned() {
        return totalSanctioned;
    }

    public void setTotalSanctioned(BigDecimal totalSanctioned) {
        this.totalSanctioned = totalSanctioned != null ? totalSanctioned : BigDecimal.ZERO;
    }

    public BigDecimal getTotalReleased() {
        return totalReleased;
    }

    public void setTotalReleased(BigDecimal totalReleased) {
        this.totalReleased = totalReleased != null ? totalReleased : BigDecimal.ZERO;
    }

    public BigDecimal getTotalPending() {
        return totalPending;
    }

    public void setTotalPending(BigDecimal totalPending) {
        this.totalPending = totalPending != null ? totalPending : BigDecimal.ZERO;
    }

    public long getCompletedCount() {
        return completedCount;
    }

    public void setCompletedCount(long completedCount) {
        this.completedCount = completedCount;
    }
}
