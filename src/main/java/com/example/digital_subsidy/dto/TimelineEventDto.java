package com.example.digital_subsidy.dto;

import java.time.LocalDateTime;

public class TimelineEventDto {

    private String stageCode;       // SUBMITTED, ELIGIBILITY_CHECK, FIELD_VERIFICATION, DISTRICT_REVIEW, FINANCE_APPROVAL, APPROVED, DISBURSEMENT, COMPLIANCE, COMPLETED
    private String stageTitle;      // Human-readable title
    private String status;          // Status string
    private LocalDateTime eventDate;
    private String performedByRole;
    private String performedByEmail;
    private String remarks;
    private String state;           // COMPLETED, CURRENT, PENDING, RE_VERIFICATION_REQUIRED, FINAL_REJECTED
    private String iconClass;
    private String badgeClass;
    private String actionUrl;
    private Object extraDetails;    // E.g., restriction cooldown info, sanction adjustment info, milestone details

    public TimelineEventDto() {}

    public TimelineEventDto(String stageCode, String stageTitle, String status, LocalDateTime eventDate, String performedByRole, String performedByEmail, String remarks, String state, String iconClass, String badgeClass) {
        this.stageCode = stageCode;
        this.stageTitle = stageTitle;
        this.status = status;
        this.eventDate = eventDate;
        this.performedByRole = performedByRole;
        this.performedByEmail = performedByEmail;
        this.remarks = remarks;
        this.state = state;
        this.iconClass = iconClass;
        this.badgeClass = badgeClass;
    }

    public String getStageCode() {
        return stageCode;
    }

    public void setStageCode(String stageCode) {
        this.stageCode = stageCode;
    }

    public String getStageTitle() {
        return stageTitle;
    }

    public void setStageTitle(String stageTitle) {
        this.stageTitle = stageTitle;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDateTime eventDate) {
        this.eventDate = eventDate;
    }

    public String getPerformedByRole() {
        return performedByRole;
    }

    public void setPerformedByRole(String performedByRole) {
        this.performedByRole = performedByRole;
    }

    public String getPerformedByEmail() {
        return performedByEmail;
    }

    public void setPerformedByEmail(String performedByEmail) {
        this.performedByEmail = performedByEmail;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getIconClass() {
        return iconClass;
    }

    public void setIconClass(String iconClass) {
        this.iconClass = iconClass;
    }

    public String getBadgeClass() {
        return badgeClass;
    }

    public void setBadgeClass(String badgeClass) {
        this.badgeClass = badgeClass;
    }

    public String getActionUrl() {
        return actionUrl;
    }

    public void setActionUrl(String actionUrl) {
        this.actionUrl = actionUrl;
    }

    public Object getExtraDetails() {
        return extraDetails;
    }

    public void setExtraDetails(Object extraDetails) {
        this.extraDetails = extraDetails;
    }
}
