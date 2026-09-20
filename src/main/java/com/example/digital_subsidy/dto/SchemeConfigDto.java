package com.example.digital_subsidy.dto;

import java.math.BigDecimal;

public class SchemeConfigDto {

    private Long id;
    private String name;
    private String category;
    private String description;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private BigDecimal totalBudget;
    private BigDecimal remainingBudget;
    private String targetCategories;
    private String eligibleStates;
    private Integer minQualifyingScore = 60;
    private Integer rejectionCooldownDays = 30;
    
    @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
    private java.time.LocalDate startDate;

    @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
    private java.time.LocalDate endDate;

    private String status = "DRAFT"; // DRAFT, ACTIVE, INACTIVE

    public SchemeConfigDto() {}

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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
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

    public String getStatus() {
        return status != null ? status : "DRAFT";
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
