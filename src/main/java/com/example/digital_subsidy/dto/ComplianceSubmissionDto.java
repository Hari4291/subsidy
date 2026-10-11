package com.example.digital_subsidy.dto;

import org.springframework.web.multipart.MultipartFile;

public class ComplianceSubmissionDto {

    private Long milestoneId;
    private MultipartFile file;
    private String beneficiaryRemarks;

    public ComplianceSubmissionDto() {}

    public ComplianceSubmissionDto(Long milestoneId, MultipartFile file, String beneficiaryRemarks) {
        this.milestoneId = milestoneId;
        this.file = file;
        this.beneficiaryRemarks = beneficiaryRemarks;
    }

    public Long getMilestoneId() {
        return milestoneId;
    }

    public void setMilestoneId(Long milestoneId) {
        this.milestoneId = milestoneId;
    }

    public MultipartFile getFile() {
        return file;
    }

    public void setFile(MultipartFile file) {
        this.file = file;
    }

    public String getBeneficiaryRemarks() {
        return beneficiaryRemarks;
    }

    public void setBeneficiaryRemarks(String beneficiaryRemarks) {
        this.beneficiaryRemarks = beneficiaryRemarks;
    }
}
