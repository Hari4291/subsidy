package com.example.digital_subsidy.dto;

public class ComplianceVerificationDto {

    private Long milestoneId;
    private String action; // VERIFY, REJECT, REUPLOAD_REQUIRED
    private String remarks; // Mandatory for rejection

    public ComplianceVerificationDto() {}

    public ComplianceVerificationDto(Long milestoneId, String action, String remarks) {
        this.milestoneId = milestoneId;
        this.action = action;
        this.remarks = remarks;
    }

    public Long getMilestoneId() {
        return milestoneId;
    }

    public void setMilestoneId(Long milestoneId) {
        this.milestoneId = milestoneId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public String getOfficerRemarks() {
        return remarks;
    }

    public void setOfficerRemarks(String officerRemarks) {
        this.remarks = officerRemarks;
    }
}
