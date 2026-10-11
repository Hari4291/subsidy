package com.example.digital_subsidy.dto;

import java.math.BigDecimal;

public class SchemeFundUtilizationDto {

    private Long schemeId;
    private String schemeName;
    private String category;
    private BigDecimal allocatedAmount = BigDecimal.ZERO;
    private BigDecimal sanctionedAmount = BigDecimal.ZERO;
    private BigDecimal releasedAmount = BigDecimal.ZERO;
    private BigDecimal utilizedAmount = BigDecimal.ZERO;
    private BigDecimal remainingAmount = BigDecimal.ZERO;
    private long applicationCount = 0;
    private double utilizationPercentage = 0.0;
    private boolean lowBudgetWarning = false;

    public SchemeFundUtilizationDto() {}

    public Long getSchemeId() {
        return schemeId;
    }

    public void setSchemeId(Long schemeId) {
        this.schemeId = schemeId;
    }

    public String getSchemeName() {
        return schemeName;
    }

    public void setSchemeName(String schemeName) {
        this.schemeName = schemeName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getAllocatedAmount() {
        return allocatedAmount != null ? allocatedAmount : BigDecimal.ZERO;
    }

    public void setAllocatedAmount(BigDecimal allocatedAmount) {
        this.allocatedAmount = allocatedAmount;
    }

    public BigDecimal getSanctionedAmount() {
        return sanctionedAmount != null ? sanctionedAmount : BigDecimal.ZERO;
    }

    public void setSanctionedAmount(BigDecimal sanctionedAmount) {
        this.sanctionedAmount = sanctionedAmount;
    }

    public BigDecimal getReleasedAmount() {
        return releasedAmount != null ? releasedAmount : BigDecimal.ZERO;
    }

    public void setReleasedAmount(BigDecimal releasedAmount) {
        this.releasedAmount = releasedAmount;
    }

    public BigDecimal getUtilizedAmount() {
        return utilizedAmount != null ? utilizedAmount : BigDecimal.ZERO;
    }

    public void setUtilizedAmount(BigDecimal utilizedAmount) {
        this.utilizedAmount = utilizedAmount;
    }

    public BigDecimal getRemainingAmount() {
        return remainingAmount != null ? remainingAmount : BigDecimal.ZERO;
    }

    public void setRemainingAmount(BigDecimal remainingAmount) {
        this.remainingAmount = remainingAmount;
    }

    public long getApplicationCount() {
        return applicationCount;
    }

    public void setApplicationCount(long applicationCount) {
        this.applicationCount = applicationCount;
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

    public boolean isLowBudgetWarning() {
        return lowBudgetWarning;
    }

    public void setLowBudgetWarning(boolean lowBudgetWarning) {
        this.lowBudgetWarning = lowBudgetWarning;
    }
}
