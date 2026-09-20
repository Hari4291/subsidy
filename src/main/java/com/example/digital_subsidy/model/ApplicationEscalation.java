package com.example.digital_subsidy.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "application_escalations")
public class ApplicationEscalation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(name = "pending_days", nullable = false)
    private Integer pendingDays;

    @Column(name = "threshold_days", nullable = false)
    private Integer thresholdDays = 7;

    @Column(nullable = false, length = 20)
    private String severity = "ESCALATED"; // NORMAL, ESCALATED, CRITICAL

    @Column(nullable = false, length = 30)
    private String status = "OPEN"; // OPEN, RESOLVED

    @Column(name = "escalation_reason", columnDefinition = "TEXT", nullable = false)
    private String escalationReason;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "resolved_by", length = 150)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public ApplicationEscalation() {}

    public ApplicationEscalation(Application application, Integer pendingDays, Integer thresholdDays, String severity, String escalationReason) {
        this.application = application;
        this.pendingDays = pendingDays;
        this.thresholdDays = thresholdDays;
        this.severity = severity;
        this.escalationReason = escalationReason;
        this.status = "OPEN";
        this.createdAt = LocalDateTime.now();
    }

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

    public Integer getPendingDays() {
        return pendingDays;
    }

    public void setPendingDays(Integer pendingDays) {
        this.pendingDays = pendingDays;
    }

    public Integer getThresholdDays() {
        return thresholdDays;
    }

    public void setThresholdDays(Integer thresholdDays) {
        this.thresholdDays = thresholdDays;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getEscalationReason() {
        return escalationReason;
    }

    public void setEscalationReason(String escalationReason) {
        this.escalationReason = escalationReason;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    @Column(name = "escalated_by_role", length = 50)
    private String escalatedByRole = "SYSTEM";

    public String getEscalatedByRole() {
        return escalatedByRole != null ? escalatedByRole : "SYSTEM";
    }

    public void setEscalatedByRole(String escalatedByRole) {
        this.escalatedByRole = escalatedByRole;
    }

    public String getReason() {
        return escalationReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
