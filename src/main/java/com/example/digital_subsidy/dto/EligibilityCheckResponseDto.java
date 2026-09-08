package com.example.digital_subsidy.dto;

import java.util.ArrayList;
import java.util.List;

public class EligibilityCheckResponseDto {

    private Long schemeId;
    private String schemeName;
    private int totalScore;
    private boolean eligible;
    private String statusMessage;
    private List<CriterionResultDto> criterionResults = new ArrayList<>();

    public EligibilityCheckResponseDto() {}

    public Long getSchemeId() {
        return schemeId;
    }

    public void setSchemeId(Long schemeId) {
        this.schemeId = schemeId;
    }

    public String getSchemeName() {
        return schemeName;
    }

    public void setSchemeName(String schemeName) {
        this.schemeName = schemeName;
    }

    public int getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(int totalScore) {
        this.totalScore = totalScore;
    }

    public boolean isEligible() {
        return eligible;
    }

    public void setEligible(boolean eligible) {
        this.eligible = eligible;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public List<CriterionResultDto> getCriterionResults() {
        return criterionResults;
    }

    public void setCriterionResults(List<CriterionResultDto> criterionResults) {
        this.criterionResults = criterionResults;
    }

    public static class CriterionResultDto {
        private String criterionName;
        private String description;
        private boolean passed;
        private int weightPoints;
        private int earnedPoints;

        public CriterionResultDto() {}

        public CriterionResultDto(String criterionName, String description, boolean passed, int weightPoints, int earnedPoints) {
            this.criterionName = criterionName;
            this.description = description;
            this.passed = passed;
            this.weightPoints = weightPoints;
            this.earnedPoints = earnedPoints;
        }

        public String getCriterionName() {
            return criterionName;
        }

        public void setCriterionName(String criterionName) {
            this.criterionName = criterionName;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public boolean isPassed() {
            return passed;
        }

        public void setPassed(boolean passed) {
            this.passed = passed;
        }

        public int getWeightPoints() {
            return weightPoints;
        }

        public void setWeightPoints(int weightPoints) {
            this.weightPoints = weightPoints;
        }

        public int getEarnedPoints() {
            return earnedPoints;
        }

        public void setEarnedPoints(int earnedPoints) {
            this.earnedPoints = earnedPoints;
        }
    }
}
