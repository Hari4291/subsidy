package com.example.digital_subsidy.dto;

public class EligibilityCriteriaDto {

    private Long id;
    private Long schemeId;
    private String criterionName;
    private String criterionType; // NUMERIC, CATEGORICAL, BOOLEAN, TEXT
    private String operator; // EQUALS, LTE, GTE, IN, CONTAINS, IS_TRUE
    private String targetValue;
    private Integer weightPoints;
    private String description;
    private String questionText;
    private String answerType; // TEXT, NUMBER, DECIMAL, YES_NO, DROPDOWN, DATE
    private String options;
    private Integer displayOrder = 1;
    private Boolean required = Boolean.TRUE;
    private Boolean active = Boolean.TRUE;

    public EligibilityCriteriaDto() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSchemeId() {
        return schemeId;
    }

    public void setSchemeId(Long schemeId) {
        this.schemeId = schemeId;
    }

    public String getCriterionName() {
        return criterionName;
    }

    public void setCriterionName(String criterionName) {
        this.criterionName = criterionName;
    }

    public String getCriterionType() {
        return criterionType;
    }

    public void setCriterionType(String criterionType) {
        this.criterionType = criterionType;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getTargetValue() {
        return targetValue;
    }

    public void setTargetValue(String targetValue) {
        this.targetValue = targetValue;
    }

    public Integer getWeightPoints() {
        return weightPoints != null ? weightPoints : 0;
    }

    public void setWeightPoints(Integer weightPoints) {
        this.weightPoints = weightPoints;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public String getAnswerType() {
        return answerType;
    }

    public void setAnswerType(String answerType) {
        this.answerType = answerType;
    }

    public String getOptions() {
        return options;
    }

    public void setOptions(String options) {
        this.options = options;
    }

    public Integer getDisplayOrder() {
        return displayOrder != null ? displayOrder : 1;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Boolean getRequired() {
        return required != null ? required : true;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public Boolean getActive() {
        return active != null ? active : true;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
