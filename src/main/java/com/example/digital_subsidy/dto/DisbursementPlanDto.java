package com.example.digital_subsidy.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class DisbursementPlanDto {

    private Long applicationId;
    private BigDecimal sanctionedAmount;
    private String calculationType = "PERCENTAGE"; // PERCENTAGE or FIXED_AMOUNT
    private List<StageConfigDto> stages = new ArrayList<>();

    public DisbursementPlanDto() {}

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public BigDecimal getSanctionedAmount() {
        return sanctionedAmount;
    }

    public void setSanctionedAmount(BigDecimal sanctionedAmount) {
        this.sanctionedAmount = sanctionedAmount;
    }

    public String getCalculationType() {
        return calculationType;
    }

    public void setCalculationType(String calculationType) {
        this.calculationType = calculationType;
    }

    public List<StageConfigDto> getStages() {
        return stages;
    }

    public void setStages(List<StageConfigDto> stages) {
        this.stages = stages;
    }
}
