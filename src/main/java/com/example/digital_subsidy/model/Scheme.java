package com.example.digital_subsidy.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "schemes")
public class Scheme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "min_amount", nullable = false)
    private BigDecimal minAmount;

    @Column(name = "max_amount", nullable = false)
    private BigDecimal maxAmount;

    @Column(name = "total_budget", nullable = false)
    private BigDecimal totalBudget;

    @Column(name = "remaining_budget", nullable = false)
    private BigDecimal remainingBudget;

    @Column(name = "target_categories", length = 255)
    private String targetCategories; // Comma separated e.g. "General,OBC,SC,ST"

    @Column(name = "eligible_states", length = 255)
    private String eligibleStates; // Comma separated or "ALL"

    @Column(length = 100)
    private String category = "General"; // Agriculture, Solar, MSME, Energy, Technology, etc.

    @Column(name = "min_qualifying_score", nullable = false)
    private Integer minQualifyingScore = 60; // Minimum score % required for eligibility

    @Column(name = "rejection_cooldown_days", nullable = false)
    private Integer rejectionCooldownDays = 30; // Reapplication restriction cooldown period in days

    @Column(name = "single_release_threshold", nullable = false)
    private BigDecimal singleReleaseThreshold = com.example.digital_subsidy.service.DisbursementService.DISBURSEMENT_SINGLE_RELEASE_THRESHOLD;

    @Column(name = "priority_queue_enabled", nullable = false)
    private Boolean priorityQueueEnabled = true;

    @Column(name = "eligibility_weight", nullable = false)
    private Integer eligibilityWeight = 60;

    @Column(name = "waiting_weight", nullable = false)
    private Integer waitingWeight = 25;

    @Column(name = "vulnerability_weight", nullable = false)
    private Integer vulnerabilityWeight = 15;

    @Column(name = "high_priority_threshold", nullable = false)
    private Integer highPriorityThreshold = 75;

    @Column(name = "medium_priority_threshold", nullable = false)
    private Integer mediumPriorityThreshold = 50;

    @Column(name = "max_aging_days", nullable = false)
    private Integer maxAgingDays = 30;

    @Column(name = "escalation_waiting_days", nullable = false)
    private Integer escalationWaitingDays = 7;

    @Column(name = "start_date")
    private java.time.LocalDate startDate;

    @Column(name = "end_date")
    private java.time.LocalDate endDate;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE"; // ACTIVE, INACTIVE, DRAFT

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "scheme", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EligibilityCriteria> criteriaList = new ArrayList<>();

    @OneToMany(mappedBy = "scheme", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RequiredDocument> requiredDocuments = new ArrayList<>();

    @OneToMany(mappedBy = "scheme", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DisbursementMilestone> milestones = new ArrayList<>();

    public Scheme() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getMinAmount() {
        return minAmount;
    }

    public void setMinAmount(BigDecimal minAmount) {
        this.minAmount = minAmount;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public void setMaxAmount(BigDecimal maxAmount) {
        this.maxAmount = maxAmount;
    }

    public BigDecimal getTotalBudget() {
        return totalBudget;
    }

    public void setTotalBudget(BigDecimal totalBudget) {
        this.totalBudget = totalBudget;
    }

    public BigDecimal getRemainingBudget() {
        return remainingBudget;
    }

    public void setRemainingBudget(BigDecimal remainingBudget) {
        this.remainingBudget = remainingBudget;
    }

    public String getTargetCategories() {
        return targetCategories;
    }

    public void setTargetCategories(String targetCategories) {
        this.targetCategories = targetCategories;
    }

    public String getEligibleStates() {
        return eligibleStates;
    }

    public void setEligibleStates(String eligibleStates) {
        this.eligibleStates = eligibleStates;
    }

    public String getCategory() {
        return category != null ? category : "General";
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getMinQualifyingScore() {
        return minQualifyingScore != null ? minQualifyingScore : 60;
    }

    public void setMinQualifyingScore(Integer minQualifyingScore) {
        this.minQualifyingScore = minQualifyingScore;
    }

    public Integer getRejectionCooldownDays() {
        return rejectionCooldownDays != null ? rejectionCooldownDays : 30;
    }

    public void setRejectionCooldownDays(Integer rejectionCooldownDays) {
        this.rejectionCooldownDays = rejectionCooldownDays;
    }

    public BigDecimal getSingleReleaseThreshold() {
        return singleReleaseThreshold != null ? singleReleaseThreshold : com.example.digital_subsidy.service.DisbursementService.DISBURSEMENT_SINGLE_RELEASE_THRESHOLD;
    }

    public void setSingleReleaseThreshold(BigDecimal singleReleaseThreshold) {
        this.singleReleaseThreshold = singleReleaseThreshold;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<EligibilityCriteria> getCriteriaList() {
        return criteriaList;
    }

    public void setCriteriaList(List<EligibilityCriteria> criteriaList) {
        this.criteriaList = criteriaList;
    }

    public List<RequiredDocument> getRequiredDocuments() {
        return requiredDocuments;
    }

    public void setRequiredDocuments(List<RequiredDocument> requiredDocuments) {
        this.requiredDocuments = requiredDocuments;
    }

    public java.time.LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(java.time.LocalDate startDate) {
        this.startDate = startDate;
    }

    public java.time.LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(java.time.LocalDate endDate) {
        this.endDate = endDate;
    }

    public List<DisbursementMilestone> getMilestones() {
        return milestones;
    }

    public void setMilestones(List<DisbursementMilestone> milestones) {
        this.milestones = milestones;
    }

    public Boolean getPriorityQueueEnabled() {
        return priorityQueueEnabled != null ? priorityQueueEnabled : true;
    }

    public void setPriorityQueueEnabled(Boolean priorityQueueEnabled) {
        this.priorityQueueEnabled = priorityQueueEnabled;
    }

    public Integer getEligibilityWeight() {
        return eligibilityWeight != null ? eligibilityWeight : 60;
    }

    public void setEligibilityWeight(Integer eligibilityWeight) {
        this.eligibilityWeight = eligibilityWeight;
    }

    public Integer getWaitingWeight() {
        return waitingWeight != null ? waitingWeight : 25;
    }

    public void setWaitingWeight(Integer waitingWeight) {
        this.waitingWeight = waitingWeight;
    }

    public Integer getVulnerabilityWeight() {
        return vulnerabilityWeight != null ? vulnerabilityWeight : 15;
    }

    public void setVulnerabilityWeight(Integer vulnerabilityWeight) {
        this.vulnerabilityWeight = vulnerabilityWeight;
    }

    public Integer getHighPriorityThreshold() {
        return highPriorityThreshold != null ? highPriorityThreshold : 75;
    }

    public void setHighPriorityThreshold(Integer highPriorityThreshold) {
        this.highPriorityThreshold = highPriorityThreshold;
    }

    public Integer getMediumPriorityThreshold() {
        return mediumPriorityThreshold != null ? mediumPriorityThreshold : 50;
    }

    public void setMediumPriorityThreshold(Integer mediumPriorityThreshold) {
        this.mediumPriorityThreshold = mediumPriorityThreshold;
    }

    public Integer getMaxAgingDays() {
        return maxAgingDays != null ? maxAgingDays : 30;
    }

    public void setMaxAgingDays(Integer maxAgingDays) {
        this.maxAgingDays = maxAgingDays;
    }

    public Integer getEscalationWaitingDays() {
        return escalationWaitingDays != null ? escalationWaitingDays : 7;
    }

    public void setEscalationWaitingDays(Integer escalationWaitingDays) {
        this.escalationWaitingDays = escalationWaitingDays;
    }

    public boolean isValidPriorityWeights() {
        int ew = getEligibilityWeight();
        int ww = getWaitingWeight();
        int vw = getVulnerabilityWeight();
        return ew >= 0 && ww >= 0 && vw >= 0 && (ew + ww + vw == 100);
    }
}
