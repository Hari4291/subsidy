package com.example.digital_subsidy.model;

import jakarta.persistence.*;

@Entity
@Table(name = "eligibility_criteria")
public class EligibilityCriteria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "scheme_id", nullable = false)
    private Scheme scheme;

    @Column(name = "criterion_name", nullable = false, length = 100)
    private String criterionName; // e.g. "MIN_AGE", "MAX_INCOME", "CATEGORY_CHECK", "STATE_CHECK", "DOC_IDENTITY"

    @Column(name = "criterion_type", nullable = false, length = 50)
    private String criterionType; // NUMERIC, CATEGORICAL, BOOLEAN

    @Column(nullable = false, length = 20)
    private String operator; // GTE, LTE, EQUALS, IN, CONTAINS, IS_TRUE

    @Column(name = "target_value", nullable = false, length = 255)
    private String targetValue;

    @Column(name = "weight_points", nullable = false)
    private Integer weightPoints; // Weight towards score e.g. 20, 30

    @Column(length = 255)
    private String description;

    @Column(name = "question_text", length = 255)
    private String questionText;

    @Column(name = "answer_type", length = 50)
    private String answerType; // BOOLEAN, NUMERIC, CATEGORICAL, TEXT

    @Column(name = "options", length = 500)
    private String options; // Comma separated options e.g. "Residential,Commercial,Other"

    @Column(name = "display_order")
    private Integer displayOrder = 1;

    @Column(name = "is_required")
    private Boolean required = Boolean.TRUE;

    @Column(name = "is_active")
    private Boolean active = Boolean.TRUE;

    public EligibilityCriteria() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Scheme getScheme() {
        return scheme;
    }

    public void setScheme(Scheme scheme) {
        this.scheme = scheme;
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
        return weightPoints;
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
        return questionText != null ? questionText : description;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public String getAnswerType() {
        return answerType != null ? answerType : criterionType;
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

    public boolean isRequired() {
        return required != null ? required : true;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public boolean isActive() {
        return active != null ? active : true;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
