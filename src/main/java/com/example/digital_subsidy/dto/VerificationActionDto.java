package com.example.digital_subsidy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;

/**
 * DTO for Field Officer / District Officer verification decisions.
 *
 * Document verification uses generic maps keyed by document ID so the form
 * is fully dynamic — no hardcoded Aadhaar / Passbook fields.
 */
public class VerificationActionDto {

    @NotNull(message = "Application ID is required")
    private Long applicationId;

    @NotBlank(message = "Action decision is required")
    private String action; // APPROVE, REJECT, REQUEST_RE_VERIFICATION

    private String remarks;

    /**
     * Map from document.id → verification status decision
     * Values: VERIFIED | INVALID | RE_VERIFICATION_REQUIRED
     */
    private Map<Long, String> documentVerificationStatuses = new HashMap<>();

    /**
     * Map from document.id → officer remarks for that specific document
     */
    private Map<Long, String> documentVerificationRemarks = new HashMap<>();

    public VerificationActionDto() {}

    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public Map<Long, String> getDocumentVerificationStatuses() { return documentVerificationStatuses; }
    public void setDocumentVerificationStatuses(Map<Long, String> documentVerificationStatuses) {
        this.documentVerificationStatuses = documentVerificationStatuses != null ? documentVerificationStatuses : new HashMap<>();
    }

    public Map<Long, String> getDocumentVerificationRemarks() { return documentVerificationRemarks; }
    public void setDocumentVerificationRemarks(Map<Long, String> documentVerificationRemarks) {
        this.documentVerificationRemarks = documentVerificationRemarks != null ? documentVerificationRemarks : new HashMap<>();
    }
}
