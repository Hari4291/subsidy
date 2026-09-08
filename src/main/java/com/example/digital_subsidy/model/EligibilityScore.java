package com.example.digital_subsidy.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "eligibility_scores")
public class EligibilityScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = true)
    @JoinColumn(name = "application_id", nullable = true)
    private Application application;

    @Column(name = "total_score", nullable = false)
    private Integer totalScore;

    @Column(name = "is_eligible", nullable = false)
    private boolean eligible;

    @Column(name = "passed_criteria_json", columnDefinition = "TEXT")
    private String passedCriteriaJson;

    @Column(name = "evaluated_at", nullable = false)
    private LocalDateTime evaluatedAt = LocalDateTime.now();

    public EligibilityScore() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Application getApplication() {
        return application;
    }

    public void setApplication(Application application) {
        this.application = application;
    }

    public Integer getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(Integer totalScore) {
        this.totalScore = totalScore;
    }

    public boolean isEligible() {
        return eligible;
    }

    public void setEligible(boolean eligible) {
        this.eligible = eligible;
    }

    public String getPassedCriteriaJson() {
        return passedCriteriaJson;
    }

    public void setPassedCriteriaJson(String passedCriteriaJson) {
        this.passedCriteriaJson = passedCriteriaJson;
    }

    public LocalDateTime getEvaluatedAt() {
        return evaluatedAt;
    }

    public void setEvaluatedAt(LocalDateTime evaluatedAt) {
        this.evaluatedAt = evaluatedAt;
    }
}
