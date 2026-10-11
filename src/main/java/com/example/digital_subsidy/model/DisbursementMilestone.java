package com.example.digital_subsidy.model;

import jakarta.persistence.*;

@Entity
@Table(name = "scheme_disbursement_milestones")
public class DisbursementMilestone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "scheme_id", nullable = false)
    private Scheme scheme;

    @Column(name = "milestone_name", nullable = false, length = 150)
    private String milestoneName;

    @Column(nullable = false)
    private Integer percentage; // e.g., 30, 40, 30

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 1;

    @Column(name = "is_active", nullable = false)
    private Boolean active = Boolean.TRUE;

    @Column(length = 255)
    private String description;

    @Column(name = "required_document_type", length = 100)
    private String requiredDocumentType;

    public DisbursementMilestone() {}

    public DisbursementMilestone(Scheme scheme, String milestoneName, Integer percentage, Integer displayOrder, Boolean active, String description) {
        this.scheme = scheme;
        this.milestoneName = milestoneName;
        this.percentage = percentage;
        this.displayOrder = displayOrder;
        this.active = active;
        this.description = description;
    }

    public DisbursementMilestone(Scheme scheme, String milestoneName, Integer percentage, Integer displayOrder, Boolean active, String description, String requiredDocumentType) {
        this.scheme = scheme;
        this.milestoneName = milestoneName;
        this.percentage = percentage;
        this.displayOrder = displayOrder;
        this.active = active;
        this.description = description;
        this.requiredDocumentType = requiredDocumentType;
    }

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

    public String getMilestoneName() {
        return milestoneName;
    }

    public void setMilestoneName(String milestoneName) {
        this.milestoneName = milestoneName;
    }

    public Integer getPercentage() {
        return percentage;
    }

    public void setPercentage(Integer percentage) {
        this.percentage = percentage;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public boolean isActive() {
        return active != null ? active : true;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRequiredDocumentType() {
        return requiredDocumentType;
    }

    public void setRequiredDocumentType(String requiredDocumentType) {
        this.requiredDocumentType = requiredDocumentType;
    }
}
