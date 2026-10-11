package com.example.digital_subsidy.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fund_utilizations")
public class FundUtilization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "application_id")
    private Application application;

    @ManyToOne
    @JoinColumn(name = "disbursement_id")
    private Disbursement disbursement;

    @ManyToOne
    @JoinColumn(name = "milestone_id")
    private Milestone milestone;

    @ManyToOne
    @JoinColumn(name = "release_id")
    private DisbursementRelease release;

    @ManyToOne(optional = false)
    @JoinColumn(name = "scheme_id", nullable = false)
    private Scheme scheme;

    @Column(length = 100)
    private String state;

    @Column(length = 100)
    private String district;

    @Column(name = "sanctioned_amount")
    private BigDecimal sanctionedAmount = BigDecimal.ZERO;

    @Column(name = "released_amount")
    private BigDecimal releasedAmount = BigDecimal.ZERO;

    @Column(name = "utilized_amount")
    private BigDecimal utilizedAmount = BigDecimal.ZERO;

    @Column(name = "utilization_status", length = 30, nullable = false)
    private String utilizationStatus = "PENDING"; // PENDING, PARTIALLY_UTILIZED, FULLY_UTILIZED, UNDER_REVIEW, REJECTED

    @Column(name = "utilization_date")
    private LocalDateTime utilizationDate;

    @Column(name = "submitted_by", length = 100)
    private String submittedBy;

    @Column(name = "verified_by", length = 100)
    private String verifiedBy;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public FundUtilization() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Application getApplication() {
        return application;
    }

    public void setApplication(Application application) {
        this.application = application;
    }

    public Disbursement getDisbursement() {
        return disbursement;
    }

    public void setDisbursement(Disbursement disbursement) {
        this.disbursement = disbursement;
    }

    public Milestone getMilestone() {
        return milestone;
    }

    public void setMilestone(Milestone milestone) {
        this.milestone = milestone;
    }

    public DisbursementRelease getRelease() {
        return release;
    }

    public void setRelease(DisbursementRelease release) {
        this.release = release;
    }

    public Scheme getScheme() {
        return scheme;
    }

    public void setScheme(Scheme scheme) {
        this.scheme = scheme;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
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

    public String getUtilizationStatus() {
        return utilizationStatus;
    }

    public void setUtilizationStatus(String utilizationStatus) {
        this.utilizationStatus = utilizationStatus;
    }

    public LocalDateTime getUtilizationDate() {
        return utilizationDate;
    }

    public void setUtilizationDate(LocalDateTime utilizationDate) {
        this.utilizationDate = utilizationDate;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(String submittedBy) {
        this.submittedBy = submittedBy;
    }

    public String getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(String verifiedBy) {
        this.verifiedBy = verifiedBy;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
