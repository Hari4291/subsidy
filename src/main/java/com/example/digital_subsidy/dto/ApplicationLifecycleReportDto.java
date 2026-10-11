package com.example.digital_subsidy.dto;

public class ApplicationLifecycleReportDto {

    private long submittedCount = 0;
    private long eligibilityCheckCount = 0;
    private long eligibleCount = 0;
    private long fieldVerificationCount = 0;
    private long districtReviewCount = 0;
    private long financeApprovalCount = 0;
    private long approvedCount = 0;
    private long disbursementCount = 0;
    private long complianceCount = 0;
    private long completedCount = 0;
    private long rejectedCount = 0;
    private long reVerificationRequiredCount = 0;
    private long totalApplications = 0;

    public ApplicationLifecycleReportDto() {}

    public long getSubmittedCount() {
        return submittedCount;
    }

    public void setSubmittedCount(long submittedCount) {
        this.submittedCount = submittedCount;
    }

    public long getEligibilityCheckCount() {
        return eligibilityCheckCount;
    }

    public void setEligibilityCheckCount(long eligibilityCheckCount) {
        this.eligibilityCheckCount = eligibilityCheckCount;
    }

    public long getEligibleCount() {
        return eligibleCount;
    }

    public void setEligibleCount(long eligibleCount) {
        this.eligibleCount = eligibleCount;
    }

    public long getFieldVerificationCount() {
        return fieldVerificationCount;
    }

    public void setFieldVerificationCount(long fieldVerificationCount) {
        this.fieldVerificationCount = fieldVerificationCount;
    }

    public long getDistrictReviewCount() {
        return districtReviewCount;
    }

    public void setDistrictReviewCount(long districtReviewCount) {
        this.districtReviewCount = districtReviewCount;
    }

    public long getFinanceApprovalCount() {
        return financeApprovalCount;
    }

    public void setFinanceApprovalCount(long financeApprovalCount) {
        this.financeApprovalCount = financeApprovalCount;
    }

    public long getApprovedCount() {
        return approvedCount;
    }

    public void setApprovedCount(long approvedCount) {
        this.approvedCount = approvedCount;
    }

    public long getDisbursementCount() {
        return disbursementCount;
    }

    public void setDisbursementCount(long disbursementCount) {
        this.disbursementCount = disbursementCount;
    }

    public long getComplianceCount() {
        return complianceCount;
    }

    public void setComplianceCount(long complianceCount) {
        this.complianceCount = complianceCount;
    }

    public long getCompletedCount() {
        return completedCount;
    }

    public void setCompletedCount(long completedCount) {
        this.completedCount = completedCount;
    }

    public long getRejectedCount() {
        return rejectedCount;
    }

    public void setRejectedCount(long rejectedCount) {
        this.rejectedCount = rejectedCount;
    }

    public long getReVerificationRequiredCount() {
        return reVerificationRequiredCount;
    }

    public void setReVerificationRequiredCount(long reVerificationRequiredCount) {
        this.reVerificationRequiredCount = reVerificationRequiredCount;
    }

    public long getTotalApplications() {
        return totalApplications;
    }

    public void setTotalApplications(long totalApplications) {
        this.totalApplications = totalApplications;
    }
}
