package com.example.digital_subsidy.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "milestones")
public class Milestone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "disbursement_id", nullable = false)
    private Disbursement disbursement;

    @Column(name = "milestone_name", nullable = false, length = 100)
    private String milestoneName; // e.g. "Milestone 1 - Initial Grant (30%)"

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Integer percentage;

    @Column(name = "milestone_amount", nullable = false)
    private BigDecimal milestoneAmount;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "completion_status", nullable = false, length = 30)
    private String completionStatus = "PENDING"; // PENDING, SUBMITTED, VERIFIED, COMPLETED

    @Column(name = "compliance_status", nullable = false, length = 30)
    private String complianceStatus = "COMPLIANT"; // COMPLIANT, OVERDUE, NON_COMPLIANT

    @Column(name = "released_amount")
    private BigDecimal releasedAmount = BigDecimal.ZERO;

    @Column(name = "release_date")
    private LocalDateTime releaseDate;

    public Milestone() {}

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

    public String getMilestoneName() {
        return milestoneName;
    }

    public void setMilestoneName(String milestoneName) {
        this.milestoneName = milestoneName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getPercentage() {
        return percentage;
    }

    public void setPercentage(Integer percentage) {
        this.percentage = percentage;
    }

    public BigDecimal getMilestoneAmount() {
        return milestoneAmount;
    }

    public void setMilestoneAmount(BigDecimal milestoneAmount) {
        this.milestoneAmount = milestoneAmount;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getCompletionStatus() {
        return completionStatus;
    }

    public void setCompletionStatus(String completionStatus) {
        this.completionStatus = completionStatus;
    }

    public String getComplianceStatus() {
        return complianceStatus;
    }

    public void setComplianceStatus(String complianceStatus) {
        this.complianceStatus = complianceStatus;
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
}
