package com.example.digital_subsidy.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "disbursement_releases")
public class DisbursementRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "disbursement_id", nullable = false)
    private Disbursement disbursement;

    @ManyToOne(optional = false)
    @JoinColumn(name = "milestone_id", nullable = false)
    private Milestone milestone;

    @ManyToOne(optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(name = "released_amount", nullable = false)
    private BigDecimal releasedAmount;

    @Column(name = "release_date", nullable = false)
    private LocalDateTime releaseDate = LocalDateTime.now();

    @Column(name = "released_by", nullable = false, length = 100)
    private String releasedBy;

    @Column(name = "transaction_reference", length = 100)
    private String transactionReference;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod; // e.g. DBT_BANK_TRANSFER, NEFT, RTGS, TREASURY_PAYMENT

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(nullable = false, length = 30)
    private String status = "SUCCESS"; // SUCCESS, FAILED, REVERSED

    public DisbursementRelease() {}

    public DisbursementRelease(Disbursement disbursement, Milestone milestone, Application application,
                               BigDecimal releasedAmount, String releasedBy, String transactionReference,
                               String paymentMethod, String remarks) {
        this.disbursement = disbursement;
        this.milestone = milestone;
        this.application = application;
        this.releasedAmount = releasedAmount;
        this.releasedBy = releasedBy;
        this.transactionReference = transactionReference;
        this.paymentMethod = paymentMethod;
        this.remarks = remarks;
        this.releaseDate = LocalDateTime.now();
        this.status = "SUCCESS";
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Application getApplication() {
        return application;
    }

    public void setApplication(Application application) {
        this.application = application;
    }

    public BigDecimal getReleasedAmount() {
        return releasedAmount;
    }

    public void setReleasedAmount(BigDecimal releasedAmount) {
        this.releasedAmount = releasedAmount;
    }

    public LocalDateTime getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(LocalDateTime releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getReleasedBy() {
        return releasedBy;
    }

    public void setReleasedBy(String releasedBy) {
        this.releasedBy = releasedBy;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(String transactionReference) {
        this.transactionReference = transactionReference;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
