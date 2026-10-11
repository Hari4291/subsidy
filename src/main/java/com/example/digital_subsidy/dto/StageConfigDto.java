package com.example.digital_subsidy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class StageConfigDto {

    private Integer stageNumber;
    private String stageName;
    private String description;
    private Integer percentage;
    private BigDecimal amount;
    private LocalDate dueDate;
    private String complianceCondition;

    public StageConfigDto() {}

    public StageConfigDto(Integer stageNumber, String stageName, String description, Integer percentage, BigDecimal amount, LocalDate dueDate, String complianceCondition) {
        this.stageNumber = stageNumber;
        this.stageName = stageName;
        this.description = description;
        this.percentage = percentage;
        this.amount = amount;
        this.dueDate = dueDate;
        this.complianceCondition = complianceCondition;
    }

    public Integer getStageNumber() {
        return stageNumber;
    }

    public void setStageNumber(Integer stageNumber) {
        this.stageNumber = stageNumber;
    }

    public String getStageName() {
        return stageName;
    }

    public void setStageName(String stageName) {
        this.stageName = stageName;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getComplianceCondition() {
        return complianceCondition;
    }

    public void setComplianceCondition(String complianceCondition) {
        this.complianceCondition = complianceCondition;
    }
}
