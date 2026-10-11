package com.example.digital_subsidy.dto;

import java.math.BigDecimal;

public class FundUtilizationSummaryDto {

    private BigDecimal totalAllocatedAmount = BigDecimal.ZERO;
    private BigDecimal totalSanctionedAmount = BigDecimal.ZERO;
    private BigDecimal totalReleasedAmount = BigDecimal.ZERO;
    private BigDecimal totalUtilizedAmount = BigDecimal.ZERO;
    private BigDecimal totalRemainingAllocation = BigDecimal.ZERO;
    private BigDecimal unutilizedReleasedAmount = BigDecimal.ZERO;
    private double utilizationPercentage = 0.0;

    public FundUtilizationSummaryDto() {}

    public FundUtilizationSummaryDto(BigDecimal totalAllocatedAmount, BigDecimal totalSanctionedAmount,
                                   BigDecimal totalReleasedAmount, BigDecimal totalUtilizedAmount,
                                   BigDecimal totalRemainingAllocation, BigDecimal unutilizedReleasedAmount,
                                   double utilizationPercentage) {
        this.totalAllocatedAmount = totalAllocatedAmount;
        this.totalSanctionedAmount = totalSanctionedAmount;
        this.totalReleasedAmount = totalReleasedAmount;
        this.totalUtilizedAmount = totalUtilizedAmount;
        this.totalRemainingAllocation = totalRemainingAllocation;
        this.unutilizedReleasedAmount = unutilizedReleasedAmount;
        this.utilizationPercentage = utilizationPercentage;
    }

    public FundUtilizationSummaryDto(BigDecimal totalAllocatedAmount, BigDecimal totalSanctionedAmount,
                                   BigDecimal totalReleasedAmount, BigDecimal totalUtilizedAmount,
                                   BigDecimal totalRemainingAllocation, BigDecimal unutilizedReleasedAmount,
                                   int utilizationPercentage) {
        this(totalAllocatedAmount, totalSanctionedAmount, totalReleasedAmount, totalUtilizedAmount,
             totalRemainingAllocation, unutilizedReleasedAmount, (double) utilizationPercentage);
    }

    public BigDecimal getTotalAllocatedAmount() {
        return totalAllocatedAmount != null ? totalAllocatedAmount : BigDecimal.ZERO;
    }

    public void setTotalAllocatedAmount(BigDecimal totalAllocatedAmount) {
        this.totalAllocatedAmount = totalAllocatedAmount;
    }

    public BigDecimal getTotalSanctionedAmount() {
        return totalSanctionedAmount != null ? totalSanctionedAmount : BigDecimal.ZERO;
    }

    public void setTotalSanctionedAmount(BigDecimal totalSanctionedAmount) {
        this.totalSanctionedAmount = totalSanctionedAmount;
    }

    public BigDecimal getTotalReleasedAmount() {
        return totalReleasedAmount != null ? totalReleasedAmount : BigDecimal.ZERO;
    }

    public void setTotalReleasedAmount(BigDecimal totalReleasedAmount) {
        this.totalReleasedAmount = totalReleasedAmount;
    }

    public BigDecimal getTotalUtilizedAmount() {
        return totalUtilizedAmount != null ? totalUtilizedAmount : BigDecimal.ZERO;
    }

    public void setTotalUtilizedAmount(BigDecimal totalUtilizedAmount) {
        this.totalUtilizedAmount = totalUtilizedAmount;
    }

    public BigDecimal getTotalRemainingAllocation() {
        return totalRemainingAllocation != null ? totalRemainingAllocation : BigDecimal.ZERO;
    }

    public void setTotalRemainingAllocation(BigDecimal totalRemainingAllocation) {
        this.totalRemainingAllocation = totalRemainingAllocation;
    }

    public BigDecimal getUnutilizedReleasedAmount() {
        return unutilizedReleasedAmount != null ? unutilizedReleasedAmount : BigDecimal.ZERO;
    }

    public void setUnutilizedReleasedAmount(BigDecimal unutilizedReleasedAmount) {
        this.unutilizedReleasedAmount = unutilizedReleasedAmount;
    }

    public double getUtilizationPercentage() {
        return utilizationPercentage;
    }

    public void setUtilizationPercentage(double utilizationPercentage) {
        this.utilizationPercentage = utilizationPercentage;
    }

    public void setUtilizationPercentage(int utilizationPercentage) {
        this.utilizationPercentage = (double) utilizationPercentage;
    }

    public String getFormattedUtilizationPercentage() {
        if (utilizationPercentage == 0.0) return "0%";
        if (utilizationPercentage >= 100.0) return String.format(java.util.Locale.US, "%.0f%%", utilizationPercentage);
        if (utilizationPercentage < 1.0) {
            String formatted = String.format(java.util.Locale.US, "%.3f", utilizationPercentage);
            formatted = formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
            return formatted + "%";
        }
        String formatted = String.format(java.util.Locale.US, "%.2f", utilizationPercentage);
        formatted = formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
        return formatted + "%";
    }
}
