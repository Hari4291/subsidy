package com.example.digital_subsidy.controller;

import com.example.digital_subsidy.model.Document;
import com.example.digital_subsidy.model.Officer;
import com.example.digital_subsidy.repository.OfficerRepository;
import com.example.digital_subsidy.service.DocumentService;
import com.example.digital_subsidy.util.AuditLogger;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.File;

@Controller
public class DocumentViewController {

    private final DocumentService documentService;
    private final OfficerRepository officerRepository;
    private final AuditLogger auditLogger;

    public DocumentViewController(DocumentService documentService, OfficerRepository officerRepository, AuditLogger auditLogger) {
        this.documentService = documentService;
        this.officerRepository = officerRepository;
        this.auditLogger = auditLogger;
    }

    @GetMapping({
        "/field-officer/documents/{documentId}/view",
        "/district-officer/documents/{documentId}/view",
        "/beneficiary/documents/{documentId}/view",
        "/documents/{documentId}/view"
    })
    public ResponseEntity<Resource> viewDocument(@PathVariable("documentId") Long documentId,
                                                @RequestParam(value = "applicationId", required = false) Long applicationId,
                                                Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Document doc = documentService.getDocumentById(documentId);
        if (doc == null || "ARCHIVED".equalsIgnoreCase(doc.getStatus())) {
            return ResponseEntity.notFound().build();
        }

        // Strict Application-Context IDOR Check:
        // If an explicit applicationId parameter is provided in request context, document must belong to that application.
        if (applicationId != null) {
            if (doc.getApplication() == null || !doc.getApplication().getId().equals(applicationId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        String userEmail = authentication.getName();
        boolean isBeneficiary = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_BENEFICIARY"));
        boolean isFieldOfficer = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_FIELD_OFFICER"));
        boolean isDistrictOfficer = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_DISTRICT_OFFICER"));
        boolean isFinance = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_FINANCE_APPROVER"));
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        // IDOR Authorization Check
        if (isBeneficiary) {
            String ownerEmail = doc.getBeneficiary() != null && doc.getBeneficiary().getUser() != null
                    ? doc.getBeneficiary().getUser().getEmail() : null;
            if (ownerEmail == null || !ownerEmail.equalsIgnoreCase(userEmail)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            if (doc.getApplication() != null && doc.getApplication().getBeneficiary() != null && doc.getApplication().getBeneficiary().getUser() != null) {
                String appOwnerEmail = doc.getApplication().getBeneficiary().getUser().getEmail();
                if (!appOwnerEmail.equalsIgnoreCase(userEmail)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }
            }
        } else if (isFieldOfficer || isDistrictOfficer) {
            Officer officer = officerRepository.findByUserEmail(userEmail).orElse(null);
            if (officer != null && officer.getState() != null && !officer.getState().isBlank()) {
                if (doc.getBeneficiary() == null || doc.getBeneficiary().getState() == null ||
                    !officer.getState().equalsIgnoreCase(doc.getBeneficiary().getState())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }
                if (isDistrictOfficer && officer.getDistrict() != null && !officer.getDistrict().isBlank() &&
                    doc.getBeneficiary().getDistrict() != null && !officer.getDistrict().equalsIgnoreCase(doc.getBeneficiary().getDistrict())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }
            }
        } else if (!isFinance && !isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        File file = new File(doc.getFilePath());
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;

        String contentType = doc.getContentType();
        String fileNameLower = doc.getFileName() != null ? doc.getFileName().toLowerCase() : "";

        if ((contentType != null && contentType.toLowerCase().contains("pdf")) || fileNameLower.endsWith(".pdf")) {
            mediaType = MediaType.APPLICATION_PDF;
        } else if ((contentType != null && contentType.toLowerCase().contains("png")) || fileNameLower.endsWith(".png")) {
            mediaType = MediaType.IMAGE_PNG;
        } else if ((contentType != null && (contentType.toLowerCase().contains("jpg") || contentType.toLowerCase().contains("jpeg"))) ||
                fileNameLower.endsWith(".jpg") || fileNameLower.endsWith(".jpeg")) {
            mediaType = MediaType.IMAGE_JPEG;
        }

        if (isFieldOfficer) {
            auditLogger.log("FIELD_OFFICER_VIEWED_DOCUMENT", "Document", doc.getId(),
                    "Field officer " + userEmail + " viewed document #" + doc.getId() + " (" + doc.getDocumentType() + ")");
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.getFileName() + "\"")
                .body(resource);
    }
}
