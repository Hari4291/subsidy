package com.example.digital_subsidy.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "grievances")
public class Grievance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "grievance_number", nullable = false, unique = true, length = 50)
    private String grievanceNumber;

    @ManyToOne(optional = false)
    @JoinColumn(name = "beneficiary_id", nullable = false)
    private Beneficiary beneficiary;

    @ManyToOne
    @JoinColumn(name = "application_id")
    private Application application;

    @Column(nullable = false, length = 150)
    private String subject = "Grievance Complaint";

    @Column(name = "category", nullable = false, length = 100)
    private String category; // APPLICATION_DELAY, DOCUMENT_VERIFICATION, ELIGIBILITY_SCORE, REJECTION_COMPLAINT, DISBURSEMENT_ISSUE, FIELD_OFFICER_BEHAVIOUR, DISTRICT_OFFICER_REVIEW, OTHER

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(name = "supporting_document_path", length = 255)
    private String supportingDocumentPath;

    @Column(nullable = false, length = 30)
    private String status = "SUBMITTED"; // SUBMITTED, UNDER_REVIEW, CLARIFICATION_REQUIRED, RESOLVED, REJECTED

    @Column(nullable = false, length = 20)
    private String priority = "STANDARD"; // HIGH, MEDIUM, STANDARD

    @ManyToOne
    @JoinColumn(name = "assigned_officer_id")
    private Officer assignedOfficer;

    @Column(name = "resolution_remarks", columnDefinition = "TEXT")
    private String resolutionRemarks;

    @Column(name = "beneficiary_clarification", columnDefinition = "TEXT")
    private String beneficiaryClarification;

    @Column(name = "resolved_by", length = 150)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Grievance() {}

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getSupportingDocumentPath() {
        return supportingDocumentPath;
    }

    public void setSupportingDocumentPath(String supportingDocumentPath) {
        this.supportingDocumentPath = supportingDocumentPath;
    }

    public String getPriority() {
        return priority != null ? priority : "STANDARD";
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getBeneficiaryClarification() {
        return beneficiaryClarification;
    }

    public void setBeneficiaryClarification(String beneficiaryClarification) {
        this.beneficiaryClarification = beneficiaryClarification;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGrievanceNumber() {
        return grievanceNumber;
    }

    public void setGrievanceNumber(String grievanceNumber) {
        this.grievanceNumber = grievanceNumber;
    }

    public Beneficiary getBeneficiary() {
        return beneficiary;
    }

    public void setBeneficiary(Beneficiary beneficiary) {
        this.beneficiary = beneficiary;
    }

    public Application getApplication() {
        return application;
    }

    public void setApplication(Application application) {
        this.application = application;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Officer getAssignedOfficer() {
        return assignedOfficer;
    }

    public void setAssignedOfficer(Officer assignedOfficer) {
        this.assignedOfficer = assignedOfficer;
    }

    public String getResolutionRemarks() {
        return resolutionRemarks;
    }

    public void setResolutionRemarks(String resolutionRemarks) {
        this.resolutionRemarks = resolutionRemarks;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
