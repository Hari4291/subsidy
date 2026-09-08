package com.example.digital_subsidy.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class ApplicationSubmissionDto {

    @NotNull(message = "Scheme selection is required")
    private Long schemeId;

    @NotNull(message = "Requested amount is required")
    @DecimalMin(value = "100.00", message = "Requested amount must be at least ₹100")
    private BigDecimal requestedAmount;

    private BigDecimal annualIncome;
    private String remarks;

    /**
     * Map from criterionId → submitted answer string.
     * Populated during eligibility check and carried through to application submission.
     * Stored in application_eligibility_answers for Field Officer visibility.
     */
    private Map<Long, String> criterionAnswers = new HashMap<>();

    /**
     * Map from criterionId → question text at time of submission (historical snapshot).
     * Protects against Admin editing questions after submission.
     */
    private Map<Long, String> criterionQuestionSnapshots = new HashMap<>();

    /**
     * Map from criterionId → display order at time of submission.
     */
    private Map<Long, Integer> criterionDisplayOrders = new HashMap<>();

    public ApplicationSubmissionDto() {}

    public Long getSchemeId() { return schemeId; }
    public void setSchemeId(Long schemeId) { this.schemeId = schemeId; }

    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public void setRequestedAmount(BigDecimal requestedAmount) { this.requestedAmount = requestedAmount; }

    public BigDecimal getAnnualIncome() { return annualIncome; }
    public void setAnnualIncome(BigDecimal annualIncome) { this.annualIncome = annualIncome; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public Map<Long, String> getCriterionAnswers() { return criterionAnswers; }
    public void setCriterionAnswers(Map<Long, String> criterionAnswers) {
        this.criterionAnswers = criterionAnswers != null ? criterionAnswers : new HashMap<>();
    }

    public Map<Long, String> getCriterionQuestionSnapshots() { return criterionQuestionSnapshots; }
    public void setCriterionQuestionSnapshots(Map<Long, String> criterionQuestionSnapshots) {
        this.criterionQuestionSnapshots = criterionQuestionSnapshots != null ? criterionQuestionSnapshots : new HashMap<>();
    }

    public Map<Long, Integer> getCriterionDisplayOrders() { return criterionDisplayOrders; }
    public void setCriterionDisplayOrders(Map<Long, Integer> criterionDisplayOrders) {
        this.criterionDisplayOrders = criterionDisplayOrders != null ? criterionDisplayOrders : new HashMap<>();
    }
}
