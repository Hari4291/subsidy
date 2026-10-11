package com.example.digital_subsidy.dto;

import java.math.BigDecimal;

public class DisbursementReportDto {

    private Long applicationId;
    private String applicationNumber;
    private String beneficiaryName;
    private String beneficiaryIdentity;
    private String schemeName;
    private BigDecimal sanctionedAmount = BigDecimal.ZERO;
    private BigDecimal releasedAmount = BigDecimal.ZERO;
    private BigDecimal remainingAmount = BigDecimal.ZERO;
    private int totalTerms = 0;
    private int releasedTerms = 0;
    private int pendingTerms = 0;
    private String nextPendingTerm;
    private String disbursementStatus; // PLANNED, PARTIALLY_RELEASED, COMPLETED, ON_HOLD

    public DisbursementReportDto() {}

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getApplicationNumber() {
        return applicationNumber;
    }

    public void setApplicationNumber(String applicationNumber) {
        this.applicationNumber = applicationNumber;
    }

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public void setBeneficiaryName(String beneficiaryName) {
        this.beneficiaryName = beneficiaryName;
    }

    public String getBeneficiaryIdentity() {
        return beneficiaryIdentity;
    }

    public void setBeneficiaryIdentity(String beneficiaryIdentity) {
        this.beneficiaryIdentity = beneficiaryIdentity;
    }

    public String getSchemeName() {
        return schemeName;
    }

    public void setSchemeName(String schemeName) {
        this.schemeName = schemeName;
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

    public BigDecimal getRemainingAmount() {
        return remainingAmount != null ? remainingAmount : BigDecimal.ZERO;
    }

    public void setRemainingAmount(BigDecimal remainingAmount) {
        this.remainingAmount = remainingAmount;
    }

    public int getTotalTerms() {
        return totalTerms;
    }

    public void setTotalTerms(int totalTerms) {
        this.totalTerms = totalTerms;
    }

    public int getReleasedTerms() {
        return releasedTerms;
    }

    public void setReleasedTerms(int releasedTerms) {
        this.releasedTerms = releasedTerms;
    }

    public int getPendingTerms() {
        return pendingTerms;
    }

    public void setPendingTerms(int pendingTerms) {
        this.pendingTerms = pendingTerms;
    }

    public String getNextPendingTerm() {
        return nextPendingTerm;
    }

    public void setNextPendingTerm(String nextPendingTerm) {
        this.nextPendingTerm = nextPendingTerm;
    }

    public String getDisbursementStatus() {
        return disbursementStatus;
    }

    public void setDisbursementStatus(String disbursementStatus) {
        this.disbursementStatus = disbursementStatus;
    }
}
