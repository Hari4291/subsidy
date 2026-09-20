package com.example.digital_subsidy.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class PriorityBreakdownDto {

    private Integer eligibilityScore = 0;
    private Integer eligibilityWeight = 60;
    private BigDecimal eligibilityContribution = BigDecimal.ZERO;

    private long waitingDays = 0;
    private BigDecimal waitingFactor = BigDecimal.ZERO;
    private Integer waitingWeight = 25;
    private BigDecimal waitingContribution = BigDecimal.ZERO;

    private BigDecimal vulnerabilityFactor = BigDecimal.ZERO;
    private Integer vulnerabilityWeight = 15;
    private BigDecimal vulnerabilityContribution = BigDecimal.ZERO;
    private List<String> vulnerabilityDetails = new ArrayList<>();

    private BigDecimal finalPriorityScore = BigDecimal.ZERO;
    private String priorityLevel = "STANDARD";

    private boolean isOverridden = false;
    private String overrideReason;
    private String overriddenBy;

    public PriorityBreakdownDto() {}

    public Integer getEligibilityScore() {
        return eligibilityScore;
    }

    public void setEligibilityScore(Integer eligibilityScore) {
        this.eligibilityScore = eligibilityScore;
    }

    public Integer getEligibilityWeight() {
        return eligibilityWeight;
    }

    public void setEligibilityWeight(Integer eligibilityWeight) {
        this.eligibilityWeight = eligibilityWeight;
    }

    public BigDecimal getEligibilityContribution() {
        return eligibilityContribution;
    }

    public void setEligibilityContribution(BigDecimal eligibilityContribution) {
        this.eligibilityContribution = eligibilityContribution;
    }

    public long getWaitingDays() {
        return waitingDays;
    }

    public void setWaitingDays(long waitingDays) {
        this.waitingDays = waitingDays;
    }

    public BigDecimal getWaitingFactor() {
        return waitingFactor;
    }

    public void setWaitingFactor(BigDecimal waitingFactor) {
        this.waitingFactor = waitingFactor;
    }

    public Integer getWaitingWeight() {
        return waitingWeight;
    }

    public void setWaitingWeight(Integer waitingWeight) {
        this.waitingWeight = waitingWeight;
    }

    public BigDecimal getWaitingContribution() {
        return waitingContribution;
    }

    public void setWaitingContribution(BigDecimal waitingContribution) {
        this.waitingContribution = waitingContribution;
    }

    public BigDecimal getVulnerabilityFactor() {
        return vulnerabilityFactor;
    }

    public void setVulnerabilityFactor(BigDecimal vulnerabilityFactor) {
        this.vulnerabilityFactor = vulnerabilityFactor;
    }

    public Integer getVulnerabilityWeight() {
        return vulnerabilityWeight;
    }

    public void setVulnerabilityWeight(Integer vulnerabilityWeight) {
        this.vulnerabilityWeight = vulnerabilityWeight;
    }

    public BigDecimal getVulnerabilityContribution() {
        return vulnerabilityContribution;
    }

    public void setVulnerabilityContribution(BigDecimal vulnerabilityContribution) {
        this.vulnerabilityContribution = vulnerabilityContribution;
    }

    public List<String> getVulnerabilityDetails() {
        return vulnerabilityDetails;
    }

    public void setVulnerabilityDetails(List<String> vulnerabilityDetails) {
        this.vulnerabilityDetails = vulnerabilityDetails != null ? vulnerabilityDetails : new ArrayList<>();
    }

    public BigDecimal getFinalPriorityScore() {
        return finalPriorityScore;
    }

    public void setFinalPriorityScore(BigDecimal finalPriorityScore) {
        this.finalPriorityScore = finalPriorityScore;
    }

    public String getPriorityLevel() {
        return priorityLevel;
    }

    public void setPriorityLevel(String priorityLevel) {
        this.priorityLevel = priorityLevel;
    }

    public boolean isOverridden() {
        return isOverridden;
    }

    public void setOverridden(boolean overridden) {
        isOverridden = overridden;
    }

    public String getOverrideReason() {
        return overrideReason;
    }

    public void setOverrideReason(String overrideReason) {
        this.overrideReason = overrideReason;
    }

    public String getOverriddenBy() {
        return overriddenBy;
    }

    public void setOverriddenBy(String overriddenBy) {
        this.overriddenBy = overriddenBy;
    }
}
