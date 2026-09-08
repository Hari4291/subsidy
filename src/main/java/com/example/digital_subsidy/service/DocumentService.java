package com.example.digital_subsidy.service;

import com.example.digital_subsidy.model.Application;
import com.example.digital_subsidy.model.Beneficiary;
import com.example.digital_subsidy.model.Document;
import com.example.digital_subsidy.repository.DocumentRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final String uploadDir = "uploads/documents/";

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public String validateDocument(MultipartFile file, String expectedDocType) {
        // 1. Empty / Null Check
        if (file == null || file.isEmpty() || file.getSize() == 0) {
            if ("AADHAAR".equalsIgnoreCase(expectedDocType)) {
                return "Aadhaar Card is required.";
            } else if ("BANK_PASSBOOK".equalsIgnoreCase(expectedDocType)) {
                return "Bank Passbook is required.";
            } else {
                return "Uploaded file is empty.";
            }
        }

        // 2. File Size Check (Max 5 MB)
        long maxSizeInBytes = 5 * 1024 * 1024;
        if (file.getSize() > maxSizeInBytes) {
            return "File size must not exceed 5 MB.";
        }

        // 3. Supported File Format / Extension & MIME Check
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }

        List<String> allowedExtensions = List.of(".pdf", ".jpg", ".jpeg", ".png");
        if (!allowedExtensions.contains(extension)) {
            return "Unsupported file format. Please upload PDF, JPG, JPEG, or PNG.";
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            String lowerContentType = contentType.toLowerCase();
            if (!lowerContentType.contains("pdf") &&
                !lowerContentType.contains("jpeg") &&
                !lowerContentType.contains("jpg") &&
                !lowerContentType.contains("png")) {
                return "Unsupported file format. Please upload PDF, JPG, JPEG, or PNG.";
            }
        }

        return null; // Valid file format, size, and presence
    }

    @Transactional
    public Document saveDocument(Beneficiary beneficiary, Application application, String documentType, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        String originalFilename = file.getOriginalFilename();
        String fileExtension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String storedFileName = UUID.randomUUID().toString() + "_" + documentType + fileExtension;
        Path targetPath = Paths.get(uploadDir + storedFileName);
        Files.copy(file.getInputStream(), targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        Document document = new Document();
        document.setBeneficiary(beneficiary);
        document.setApplication(application);
        document.setDocumentType(documentType);
        document.setFileName(originalFilename != null ? originalFilename : storedFileName);
        document.setFilePath(targetPath.toString());
        document.setContentType(file.getContentType());
        document.setFileSize(file.getSize());
        document.setUploadedDate(LocalDateTime.now());
        document.setVerificationStatus("PENDING");
        document.setStatus("ACTIVE");

        return documentRepository.save(document);
    }

    @Transactional
    public Document replaceDocument(Beneficiary beneficiary, Application application, String documentType, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        // Mark existing active document of same type for beneficiary/application as ARCHIVED/INACTIVE
        List<Document> existing;
        if (application != null) {
            existing = documentRepository.findByApplicationId(application.getId());
        } else {
            existing = documentRepository.findByBeneficiaryIdAndDocumentType(beneficiary.getId(), documentType);
        }

        for (Document oldDoc : existing) {
            if (documentType.equalsIgnoreCase(oldDoc.getDocumentType()) && "ACTIVE".equals(oldDoc.getStatus())) {
                oldDoc.setStatus("ARCHIVED");
                documentRepository.save(oldDoc);
            }
        }

        return saveDocument(beneficiary, application, documentType, file);
    }

    public Document getDocumentById(Long id) {
        return documentRepository.findById(id).orElse(null);
    }

    @Transactional
    public Document updateDocumentVerification(Long documentId, String status, String remarks, String verifiedBy) {
        Document doc = getDocumentById(documentId);
        if (doc != null) {
            doc.setVerificationStatus(status);
            doc.setVerificationRemarks(remarks);
            doc.setVerifiedBy(verifiedBy);
            doc.setVerifiedAt(LocalDateTime.now());
            return documentRepository.save(doc);
        }
        return null;
    }

    public List<Document> getDocumentsByBeneficiary(Long beneficiaryId) {
        return documentRepository.findByBeneficiaryId(beneficiaryId);
    }

    public List<Document> getDocumentsByApplication(Long applicationId) {
        return documentRepository.findByApplicationId(applicationId);
    }
}
