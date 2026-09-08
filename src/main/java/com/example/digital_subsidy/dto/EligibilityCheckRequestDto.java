package com.example.digital_subsidy.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class EligibilityCheckRequestDto {

    @NotNull(message = "Scheme selection is required")
    private Long schemeId;

    private BigDecimal annualIncome;
    private String educationLevel;
    private String businessType;
    private boolean identityVerified;
    private boolean requiredDocsUploaded;

    private java.util.Map<Long, String> criterionAnswers = new java.util.HashMap<>();
    private java.util.Map<String, String> answers = new java.util.HashMap<>();

    public java.util.Map<Long, String> getCriterionAnswers() {
        return criterionAnswers;
    }

    public void setCriterionAnswers(java.util.Map<Long, String> criterionAnswers) {
        this.criterionAnswers = criterionAnswers;
    }

    public java.util.Map<String, String> getAnswers() {
        return answers;
    }

    public void setAnswers(java.util.Map<String, String> answers) {
        this.answers = answers;
    }

    public EligibilityCheckRequestDto() {}

    public Long getSchemeId() {
        return schemeId;
    }

    public void setSchemeId(Long schemeId) {
        this.schemeId = schemeId;
    }

    public BigDecimal getAnnualIncome() {
        return annualIncome;
    }

    public void setAnnualIncome(BigDecimal annualIncome) {
        this.annualIncome = annualIncome;
    }

    public String getEducationLevel() {
        return educationLevel;
    }

    public void setEducationLevel(String educationLevel) {
        this.educationLevel = educationLevel;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public boolean isIdentityVerified() {
        return identityVerified;
    }

    public boolean isRequiredDocsUploaded() {
        return requiredDocsUploaded;
    }

    public void setRequiredDocsUploaded(boolean requiredDocsUploaded) {
        this.requiredDocsUploaded = requiredDocsUploaded;
    }

    private boolean docAadhaarUploaded = true;
    private boolean docBankPassbookUploaded = true;

    public boolean isDocAadhaarUploaded() {
        return docAadhaarUploaded;
    }

    public void setDocAadhaarUploaded(boolean docAadhaarUploaded) {
        this.docAadhaarUploaded = docAadhaarUploaded;
    }

    public boolean isDocBankPassbookUploaded() {
        return docBankPassbookUploaded;
    }

    public void setDocBankPassbookUploaded(boolean docBankPassbookUploaded) {
        this.docBankPassbookUploaded = docBankPassbookUploaded;
    }

    private String farmerStatus = "ACTIVE_FARMER";
    private BigDecimal landHoldingAcres = new BigDecimal("1.5");
    private boolean rooftopOwnership = true;
    private boolean docLandRecordUploaded = true;
    private boolean docElectricityBillUploaded = true;
    private boolean docBusinessRegUploaded = true;

    public String getFarmerStatus() {
        return farmerStatus;
    }

    public void setFarmerStatus(String farmerStatus) {
        this.farmerStatus = farmerStatus;
    }

    public BigDecimal getLandHoldingAcres() {
        return landHoldingAcres;
    }

    public void setLandHoldingAcres(BigDecimal landHoldingAcres) {
        this.landHoldingAcres = landHoldingAcres;
    }

    public boolean isRooftopOwnership() {
        return rooftopOwnership;
    }

    public void setRooftopOwnership(boolean rooftopOwnership) {
        this.rooftopOwnership = rooftopOwnership;
    }

    public boolean isDocLandRecordUploaded() {
        return docLandRecordUploaded;
    }

    public void setDocLandRecordUploaded(boolean docLandRecordUploaded) {
        this.docLandRecordUploaded = docLandRecordUploaded;
    }

    public boolean isDocElectricityBillUploaded() {
        return docElectricityBillUploaded;
    }

    public void setDocElectricityBillUploaded(boolean docElectricityBillUploaded) {
        this.docElectricityBillUploaded = docElectricityBillUploaded;
    }

    public boolean isDocBusinessRegUploaded() {
        return docBusinessRegUploaded;
    }

    public void setDocBusinessRegUploaded(boolean docBusinessRegUploaded) {
        this.docBusinessRegUploaded = docBusinessRegUploaded;
    }
}
