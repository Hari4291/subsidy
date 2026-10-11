package com.example.digital_subsidy.dto;

import java.math.BigDecimal;

public class FundTrackingSummaryDto {

    private BigDecimal totalSanctioned = BigDecimal.ZERO;
    private BigDecimal totalReleased = BigDecimal.ZERO;
    private BigDecimal totalRemaining = BigDecimal.ZERO;
    private BigDecimal pendingReleaseAmount = BigDecimal.ZERO;
    private BigDecimal releasedPercentage = BigDecimal.ZERO;
    private BigDecimal remainingPercentage = BigDecimal.ZERO;

    private long totalBeneficiaries = 0;
    private long termsCompleted = 0;
    private long termsRemaining = 0;
    private long activeApplications = 0;

    public FundTrackingSummaryDto() {}

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

    public BigDecimal getTotalRemaining() {
        return totalRemaining;
    }

    public void setTotalRemaining(BigDecimal totalRemaining) {
        this.totalRemaining = totalRemaining != null ? totalRemaining : BigDecimal.ZERO;
    }

    public BigDecimal getPendingReleaseAmount() {
        return pendingReleaseAmount;
    }

    public void setPendingReleaseAmount(BigDecimal pendingReleaseAmount) {
        this.pendingReleaseAmount = pendingReleaseAmount != null ? pendingReleaseAmount : BigDecimal.ZERO;
    }

    public BigDecimal getPendingRelease() {
        return pendingReleaseAmount;
    }

    public void setPendingRelease(BigDecimal pendingRelease) {
        this.pendingReleaseAmount = pendingRelease != null ? pendingRelease : BigDecimal.ZERO;
    }

    public BigDecimal getReleasedPercentage() {
        return releasedPercentage;
    }

    public void setReleasedPercentage(BigDecimal releasedPercentage) {
        this.releasedPercentage = releasedPercentage != null ? releasedPercentage : BigDecimal.ZERO;
    }

    public BigDecimal getRemainingPercentage() {
        return remainingPercentage;
    }

    public void setRemainingPercentage(BigDecimal remainingPercentage) {
        this.remainingPercentage = remainingPercentage != null ? remainingPercentage : BigDecimal.ZERO;
    }

    public long getTotalBeneficiaries() {
        return totalBeneficiaries;
    }

    public void setTotalBeneficiaries(long totalBeneficiaries) {
        this.totalBeneficiaries = totalBeneficiaries;
    }

    public long getTermsCompleted() {
        return termsCompleted;
    }

    public void setTermsCompleted(long termsCompleted) {
        this.termsCompleted = termsCompleted;
    }

    public long getTermsRemaining() {
        return termsRemaining;
    }

    public void setTermsRemaining(long termsRemaining) {
        this.termsRemaining = termsRemaining;
    }

    public long getActiveApplications() {
        return activeApplications;
    }

    public void setActiveApplications(long activeApplications) {
        this.activeApplications = activeApplications;
    }
}
