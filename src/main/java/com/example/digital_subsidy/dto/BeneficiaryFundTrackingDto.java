package com.example.digital_subsidy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class BeneficiaryFundTrackingDto {

    private Long applicationId;
    private String applicationNumber;
    private Long beneficiaryId;
    private String beneficiaryName;
    private String beneficiaryIdentity;
    private Long schemeId;
    private String schemeName;

    private BigDecimal totalSanctionedAmount = BigDecimal.ZERO;
    private BigDecimal totalReleasedAmount = BigDecimal.ZERO;
    private BigDecimal remainingAmount = BigDecimal.ZERO;

    private int totalTerms;
    private int completedTerms;
    private int remainingTerms;

    private String currentTermName;
    private Integer currentStageNumber;
    private String currentTermStatus;
    private LocalDate nextDueDate;
    private String overallStatus;

    private List<TermDetailDto> termDetails = new ArrayList<>();

    public BeneficiaryFundTrackingDto() {}

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

    public Long getBeneficiaryId() {
        return beneficiaryId;
    }

    public void setBeneficiaryId(Long beneficiaryId) {
        this.beneficiaryId = beneficiaryId;
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

    public BigDecimal getTotalSanctionedAmount() {
        return totalSanctionedAmount;
    }

    public void setTotalSanctionedAmount(BigDecimal totalSanctionedAmount) {
        this.totalSanctionedAmount = totalSanctionedAmount != null ? totalSanctionedAmount : BigDecimal.ZERO;
    }

    public BigDecimal getTotalReleasedAmount() {
        return totalReleasedAmount;
    }

    public void setTotalReleasedAmount(BigDecimal totalReleasedAmount) {
        this.totalReleasedAmount = totalReleasedAmount != null ? totalReleasedAmount : BigDecimal.ZERO;
    }

    public BigDecimal getRemainingAmount() {
        return remainingAmount;
    }

    public void setRemainingAmount(BigDecimal remainingAmount) {
        this.remainingAmount = remainingAmount != null ? remainingAmount : BigDecimal.ZERO;
    }

    public int getTotalTerms() {
        return totalTerms;
    }

    public void setTotalTerms(int totalTerms) {
        this.totalTerms = totalTerms;
    }

    public int getCompletedTerms() {
        return completedTerms;
    }

    public void setCompletedTerms(int completedTerms) {
        this.completedTerms = completedTerms;
    }

    public int getRemainingTerms() {
        return remainingTerms;
    }

    public void setRemainingTerms(int remainingTerms) {
        this.remainingTerms = remainingTerms;
    }

    public String getCurrentTermName() {
        return currentTermName;
    }

    public void setCurrentTermName(String currentTermName) {
        this.currentTermName = currentTermName;
    }

    public String getCurrentTerm() {
        if (currentTermName != null && !currentTermName.trim().isEmpty()) {
            return currentTermName;
        }
        if (currentStageNumber != null) {
            return "Term " + currentStageNumber;
        }
        return "N/A";
    }

    public void setCurrentTerm(String currentTerm) {
        this.currentTermName = currentTerm;
    }

    public BigDecimal getSanctionedAmount() {
        return totalSanctionedAmount;
    }

    public void setSanctionedAmount(BigDecimal sanctionedAmount) {
        setTotalSanctionedAmount(sanctionedAmount);
    }

    public BigDecimal getReleasedAmount() {
        return totalReleasedAmount;
    }

    public void setReleasedAmount(BigDecimal releasedAmount) {
        setTotalReleasedAmount(releasedAmount);
    }

    public Integer getCurrentStageNumber() {
        return currentStageNumber;
    }

    public void setCurrentStageNumber(Integer currentStageNumber) {
        this.currentStageNumber = currentStageNumber;
    }

    public String getCurrentTermStatus() {
        return currentTermStatus;
    }

    public void setCurrentTermStatus(String currentTermStatus) {
        this.currentTermStatus = currentTermStatus;
    }

    public LocalDate getNextDueDate() {
        return nextDueDate;
    }

    public void setNextDueDate(LocalDate nextDueDate) {
        this.nextDueDate = nextDueDate;
    }

    public LocalDate getNextTermDueDate() {
        return nextDueDate;
    }

    public void setNextTermDueDate(LocalDate nextTermDueDate) {
        this.nextDueDate = nextTermDueDate;
    }

    public String getOverallStatus() {
        return overallStatus;
    }

    public void setOverallStatus(String overallStatus) {
        this.overallStatus = overallStatus;
    }

    public List<TermDetailDto> getTermDetails() {
        return termDetails;
    }

    public void setTermDetails(List<TermDetailDto> termDetails) {
        this.termDetails = termDetails != null ? termDetails : new ArrayList<>();
    }
}
