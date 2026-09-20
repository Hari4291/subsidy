package com.example.digital_subsidy.service;

import com.example.digital_subsidy.exception.ResourceNotFoundException;
import com.example.digital_subsidy.model.Application;
import com.example.digital_subsidy.model.Beneficiary;
import com.example.digital_subsidy.model.Grievance;
import com.example.digital_subsidy.model.Officer;
import com.example.digital_subsidy.repository.ApplicationRepository;
import com.example.digital_subsidy.repository.GrievanceRepository;
import com.example.digital_subsidy.repository.OfficerRepository;
import com.example.digital_subsidy.util.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GrievanceService {

    private final GrievanceRepository grievanceRepository;
    private final ApplicationRepository applicationRepository;
    private final OfficerRepository officerRepository;
    private final AuditLogger auditLogger;

    public GrievanceService(GrievanceRepository grievanceRepository,
                            ApplicationRepository applicationRepository,
                            OfficerRepository officerRepository,
                            AuditLogger auditLogger) {
        this.grievanceRepository = grievanceRepository;
        this.applicationRepository = applicationRepository;
        this.officerRepository = officerRepository;
        this.auditLogger = auditLogger;
    }

    @Transactional
    public Grievance submitGrievance(Beneficiary beneficiary, Long applicationId, String category, String subject, String description, String supportingDocumentPath) {
        Grievance g = new Grievance();
        g.setGrievanceNumber("GRV-" + (100000 + (long)(Math.random() * 900000)));
        g.setBeneficiary(beneficiary);

        if (applicationId != null) {
            Application app = applicationRepository.findById(applicationId).orElse(null);
            if (app != null) {
                // Security Check: Verify application belongs to beneficiary
                if (!app.getBeneficiary().getId().equals(beneficiary.getId())) {
                    throw new org.springframework.security.access.AccessDeniedException("Unauthorized: Application does not belong to this beneficiary.");
                }
                g.setApplication(app);
            }
        }

        g.setCategory(category != null && !category.isBlank() ? category : "OTHER");
        g.setSubject(subject != null && !subject.isBlank() ? subject : "Grievance Complaint");
        g.setDescription(description);
        g.setSupportingDocumentPath(supportingDocumentPath);
        g.setStatus("SUBMITTED");
        g.setPriority("APPLICATION_DELAY".equalsIgnoreCase(category) || "DISBURSEMENT_ISSUE".equalsIgnoreCase(category) ? "HIGH" : "STANDARD");
        g.setCreatedAt(LocalDateTime.now());
        g.setUpdatedAt(LocalDateTime.now());

        Grievance saved = grievanceRepository.save(g);
        auditLogger.log("GRIEVANCE_SUBMITTED", "Grievance", saved.getId(),
                "Beneficiary " + beneficiary.getUser().getEmail() + " submitted grievance: " + saved.getGrievanceNumber() + " (" + saved.getCategory() + ")");
        return saved;
    }

    @Transactional
    public Grievance submitGrievance(Beneficiary beneficiary, Long applicationId, String category, String description) {
        String defaultSubject = category != null ? category.replace("_", " ") : "Grievance Complaint";
        return submitGrievance(beneficiary, applicationId, category, defaultSubject, description, null);
    }

    public List<Grievance> getGrievancesForBeneficiary(Long beneficiaryId) {
        return grievanceRepository.findByBeneficiaryIdOrderByIdDesc(beneficiaryId);
    }

    public List<Grievance> getGrievancesForDistrict(String district, String state, String status, String category, String priority) {
        List<Grievance> list;
        if (district != null && !district.isBlank()) {
            list = grievanceRepository.findByDistrictIgnoreCaseAndTrim(district.trim());
            if (list.isEmpty() && state != null && !state.isBlank()) {
                list = grievanceRepository.findByStateIgnoreCaseAndTrim(state.trim());
            }
            if (list.isEmpty()) {
                list = grievanceRepository.findAll();
            }
        } else if (state != null && !state.isBlank()) {
            list = grievanceRepository.findByStateIgnoreCaseAndTrim(state.trim());
            if (list.isEmpty()) {
                list = grievanceRepository.findAll();
            }
        } else {
            list = grievanceRepository.findAll();
        }

        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            final String targetStatus = status.trim().toUpperCase();
            list = list.stream().filter(g -> {
                String s = g.getStatus() != null ? g.getStatus().trim().toUpperCase() : "";
                if ("SUBMITTED".equals(targetStatus) || "NEW".equals(targetStatus) || "OPEN".equals(targetStatus)) {
                    return "SUBMITTED".equals(s) || "OPEN".equals(s) || "NEW".equals(s);
                } else if ("UNDER_REVIEW".equals(targetStatus) || "IN_PROGRESS".equals(targetStatus)) {
                    return "UNDER_REVIEW".equals(s) || "IN_PROGRESS".equals(s);
                } else if ("CLARIFICATION_REQUIRED".equals(targetStatus) || "NEEDS_CLARIFICATION".equals(targetStatus) || "ACTION_REQUIRED".equals(targetStatus)) {
                    return "CLARIFICATION_REQUIRED".equals(s) || "NEEDS_CLARIFICATION".equals(s) || "ACTION_REQUIRED".equals(s);
                } else {
                    return targetStatus.equals(s);
                }
            }).collect(Collectors.toList());
        }

        if (category != null && !category.isBlank() && !"ALL".equalsIgnoreCase(category)) {
            final String targetCategory = category.trim().toUpperCase();
            list = list.stream().filter(g -> g.getCategory() != null && targetCategory.equalsIgnoreCase(g.getCategory().trim())).collect(Collectors.toList());
        }

        if (priority != null && !priority.isBlank() && !"ALL".equalsIgnoreCase(priority)) {
            final String targetPriority = priority.trim().toUpperCase();
            list = list.stream().filter(g -> g.getPriority() != null && targetPriority.equalsIgnoreCase(g.getPriority().trim())).collect(Collectors.toList());
        }

        return list;
    }

    public List<Grievance> getGrievancesForDistrict(String state, String status) {
        return getGrievancesForDistrict(null, state, status, null, null);
    }

    public List<Grievance> getGrievancesAssignedToOfficer(Long officerId) {
        return grievanceRepository.findByAssignedOfficerIdOrderByIdDesc(officerId);
    }

    public Grievance getGrievanceById(Long id) {
        return grievanceRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Grievance not found with ID: " + id));
    }

    @Transactional
    public Grievance updateGrievanceStatus(Long id, String status, String remarks, Long assignedOfficerId, String updatedBy) {
        Grievance g = getGrievanceById(id);
        String oldStatus = g.getStatus();
        g.setStatus(status);

        if (remarks != null && !remarks.isBlank()) {
            g.setResolutionRemarks(remarks);
        }

        if (assignedOfficerId != null) {
            Officer officer = officerRepository.findById(assignedOfficerId).orElse(null);
            g.setAssignedOfficer(officer);
        }

        if ("RESOLVED".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status) || "CLOSED".equalsIgnoreCase(status)) {
            g.setResolvedBy(updatedBy);
            g.setResolvedAt(LocalDateTime.now());
        }

        g.setUpdatedAt(LocalDateTime.now());
        Grievance saved = grievanceRepository.save(g);

        auditLogger.log("GRIEVANCE_STATUS_UPDATED", "Grievance", g.getId(),
                "Grievance " + g.getGrievanceNumber() + " status changed from " + oldStatus + " to " + status + " by " + updatedBy + ". Remarks: " + remarks);
        return saved;
    }

    @Transactional
    public Grievance submitClarificationResponse(Long id, String responseText, Beneficiary beneficiary) {
        Grievance g = getGrievanceById(id);
        if (!g.getBeneficiary().getId().equals(beneficiary.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Unauthorized: Cannot update another beneficiary's grievance.");
        }

        g.setBeneficiaryClarification(responseText);
        g.setStatus("UNDER_REVIEW");
        g.setUpdatedAt(LocalDateTime.now());

        Grievance saved = grievanceRepository.save(g);
        auditLogger.log("GRIEVANCE_CLARIFICATION_SUBMITTED", "Grievance", g.getId(),
                "Beneficiary " + beneficiary.getUser().getEmail() + " submitted clarification response for grievance: " + g.getGrievanceNumber());
        return saved;
    }

    @Transactional
    public Grievance reopenGrievance(Long id, String reason, Beneficiary beneficiary) {
        Grievance g = getGrievanceById(id);
        if (!g.getBeneficiary().getId().equals(beneficiary.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Unauthorized: Cannot reopen another beneficiary's grievance.");
        }
        if (!"RESOLVED".equalsIgnoreCase(g.getStatus()) && !"CLOSED".equalsIgnoreCase(g.getStatus())) {
            throw new IllegalStateException("Only RESOLVED or CLOSED grievances can be reopened.");
        }
        g.setStatus("REOPENED");
        g.setResolutionRemarks("Reopened by Beneficiary. Reason: " + reason);
        g.setUpdatedAt(LocalDateTime.now());

        Grievance saved = grievanceRepository.save(g);
        auditLogger.log("GRIEVANCE_REOPENED", "Grievance", g.getId(),
                "Beneficiary " + beneficiary.getUser().getEmail() + " reopened grievance: " + g.getGrievanceNumber() + ". Reason: " + reason);
        return saved;
    }

    public Map<String, Long> getGrievanceMetrics(String district, String state) {
        List<Grievance> list = getGrievancesForDistrict(district, state, "ALL", "ALL", "ALL");

        Map<String, Long> m = new HashMap<>();
        m.put("total", (long) list.size());
        m.put("newCount", list.stream().filter(g -> {
            String s = g.getStatus() != null ? g.getStatus().trim().toUpperCase() : "";
            return "SUBMITTED".equals(s) || "OPEN".equals(s) || "NEW".equals(s);
        }).count());
        m.put("underReviewCount", list.stream().filter(g -> {
            String s = g.getStatus() != null ? g.getStatus().trim().toUpperCase() : "";
            return "UNDER_REVIEW".equals(s) || "IN_PROGRESS".equals(s);
        }).count());
        m.put("clarificationCount", list.stream().filter(g -> {
            String s = g.getStatus() != null ? g.getStatus().trim().toUpperCase() : "";
            return "CLARIFICATION_REQUIRED".equals(s) || "NEEDS_CLARIFICATION".equals(s) || "ACTION_REQUIRED".equals(s);
        }).count());
        m.put("resolvedCount", list.stream().filter(g -> {
            String s = g.getStatus() != null ? g.getStatus().trim().toUpperCase() : "";
            return "RESOLVED".equals(s);
        }).count());
        m.put("rejectedCount", list.stream().filter(g -> {
            String s = g.getStatus() != null ? g.getStatus().trim().toUpperCase() : "";
            return "REJECTED".equals(s);
        }).count());

        return m;
    }
}
