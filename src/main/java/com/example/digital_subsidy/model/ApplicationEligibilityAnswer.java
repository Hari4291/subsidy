package com.example.digital_subsidy.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Stores a snapshot of each beneficiary's eligibility answer at the time of application submission.
 * Decoupled from the live EligibilityCriteria so Admin changes do NOT alter historical answers.
 */
@Entity
@Table(name = "application_eligibility_answers")
public class ApplicationEligibilityAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    /** FK to the criterion row — nullable in case the criterion is later deleted */
    @Column(name = "criterion_id")
    private Long criterionId;

    /** Snapshot of question text at submission time — never changes even if Admin edits the question */
    @Column(name = "question_text_snapshot", nullable = false, length = 500)
    private String questionTextSnapshot;

    /** The answer submitted by the beneficiary */
    @Column(name = "submitted_answer", length = 1000)
    private String submittedAnswer;

    @Column(name = "scheme_id")
    private Long schemeId;

    /** Evaluation result snapshot e.g. "PASS" or "FAIL" */
    @Column(name = "evaluation_result", length = 20)
    private String evaluationResult;

    /** Score points contributed by this criterion answer */
    @Column(name = "score_contribution")
    private Integer scoreContribution;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public ApplicationEligibilityAnswer() {}

    // ---- Getters & Setters ----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Application getApplication() { return application; }
    public void setApplication(Application application) { this.application = application; }

    public Long getSchemeId() { return schemeId; }
    public void setSchemeId(Long schemeId) { this.schemeId = schemeId; }

    public Long getCriterionId() { return criterionId; }
    public void setCriterionId(Long criterionId) { this.criterionId = criterionId; }

    public String getQuestionTextSnapshot() { return questionTextSnapshot; }
    public void setQuestionTextSnapshot(String questionTextSnapshot) { this.questionTextSnapshot = questionTextSnapshot; }

    public String getSubmittedAnswer() { return submittedAnswer; }
    public void setSubmittedAnswer(String submittedAnswer) { this.submittedAnswer = submittedAnswer; }

    public String getEvaluationResult() { return evaluationResult; }
    public void setEvaluationResult(String evaluationResult) { this.evaluationResult = evaluationResult; }

    public Integer getScoreContribution() { return scoreContribution; }
    public void setScoreContribution(Integer scoreContribution) { this.scoreContribution = scoreContribution; }

    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
