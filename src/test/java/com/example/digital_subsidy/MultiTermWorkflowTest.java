package com.example.digital_subsidy;

import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.*;
import com.example.digital_subsidy.service.DisbursementService;
import com.example.digital_subsidy.service.DocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class MultiTermWorkflowTest {

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private BeneficiaryRepository beneficiaryRepository;

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private DisbursementRepository disbursementRepository;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DisbursementService disbursementService;

    @Autowired
    private DocumentService documentService;

    private Beneficiary beneficiary;
    private Scheme scheme;

    @BeforeEach
    void setUp() {
        scheme = schemeRepository.findAll().stream()
                .filter(s -> s.getName().contains("Solar") || s.getName().contains("Rooftop"))
                .findFirst()
                .orElseGet(() -> {
                    Scheme s = new Scheme();
                    s.setName("National Rooftop Solar Subsidy Scheme");
                    s.setDescription("Solar Subsidy Test");
                    s.setMaxAmount(new BigDecimal("100000"));
                    s.setMinAmount(new BigDecimal("10000"));
                    s.setRemainingBudget(new BigDecimal("1000000"));
                    return schemeRepository.save(s);
                });

        beneficiary = beneficiaryRepository.findAll().stream().findFirst().orElse(null);
    }

    @Test
    void testAutomaticTermRule_UnderOrEqual40k_CreatesSingleTerm() {
        Application app = new Application();
        app.setApplicationNumber("APP-TEST-40K");
        app.setBeneficiary(beneficiary);
        app.setScheme(scheme);
        app.setRequestedAmount(new BigDecimal("40000"));
        app.setSanctionedAmount(new BigDecimal("40000"));
        app.setStatus("APPROVED");
        app.setCurrentStage("DISBURSEMENT");
        app = applicationRepository.save(app);

        Disbursement d = disbursementService.initializeDisbursement(app, null);

        assertNotNull(d);
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(d);
        assertEquals(1, milestones.size());
        assertEquals(0, new BigDecimal("40000.00").compareTo(milestones.get(0).getMilestoneAmount()));
    }

    @Test
    void testAutomaticTermRule_Above40k_CreatesThreeTerms() {
        Application app = new Application();
        app.setApplicationNumber("APP-TEST-60K");
        app.setBeneficiary(beneficiary);
        app.setScheme(scheme);
        app.setRequestedAmount(new BigDecimal("60000"));
        app.setSanctionedAmount(new BigDecimal("60000"));
        app.setStatus("APPROVED");
        app.setCurrentStage("DISBURSEMENT");
        app = applicationRepository.save(app);

        Disbursement d = disbursementService.initializeDisbursement(app, null);

        assertNotNull(d);
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(d);
        assertEquals(3, milestones.size());
        assertEquals(0, new BigDecimal("18000.00").compareTo(milestones.get(0).getMilestoneAmount()));
        assertEquals(0, new BigDecimal("24000.00").compareTo(milestones.get(1).getMilestoneAmount()));
        assertEquals(0, new BigDecimal("18000.00").compareTo(milestones.get(2).getMilestoneAmount()));
    }

    @Test
    void testCompleteMultiTermWorkflow_60k() throws Exception {
        // Step 1: Create application & disbursement plan (60k -> 3 terms)
        Application app = new Application();
        app.setApplicationNumber("APP-TEST-MULTI");
        app.setBeneficiary(beneficiary);
        app.setScheme(scheme);
        app.setRequestedAmount(new BigDecimal("60000"));
        app.setSanctionedAmount(new BigDecimal("60000"));
        app.setStatus("APPROVED");
        app.setCurrentStage("DISBURSEMENT");
        app = applicationRepository.save(app);

        Disbursement d = disbursementService.initializeDisbursement(app, null);
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(d);
        assertEquals(3, milestones.size());

        Milestone t1 = milestones.get(0);
        Milestone t2 = milestones.get(1);
        Milestone t3 = milestones.get(2);

        // Term 1 compliance is verified during initial District Officer sanction & Finance approval
        t1.setComplianceVerificationStatus("VERIFIED");
        milestoneRepository.save(t1);

        // Step 2: Release Term 1
        Milestone rel1 = disbursementService.releaseMilestone(t1.getId());
        assertNotNull(rel1);
        assertEquals(0, new BigDecimal("18000.00").compareTo(rel1.getMilestoneAmount()));

        // Check Term 2 is now activated with document requirement
        t2 = milestoneRepository.findById(t2.getId()).orElseThrow();
        assertEquals("INSTALLATION_PROGRESS_REPORT", t2.getRequiredDocumentType());
        assertNotNull(t2.getDueDate());

        // Step 3: Beneficiary uploads Term 2 Document
        MockMultipartFile validDoc = new MockMultipartFile(
                "file", "progress_report.pdf", "application/pdf", "Dummy PDF content for Term 2".getBytes()
        );

        Document uploadedDoc = documentService.saveComplianceDocument(
                beneficiary, app, t2, "INSTALLATION_PROGRESS_REPORT", validDoc
        );

        assertNotNull(uploadedDoc);

        t2 = milestoneRepository.findById(t2.getId()).orElseThrow();
        t2.setSubmittedDocument(uploadedDoc);
        t2.setComplianceVerificationStatus("SUBMITTED");
        t2.setSubmittedDate(LocalDateTime.now());
        milestoneRepository.save(t2);

        // Document scoping test: query only documents for app & milestone t2
        List<Document> scopedDocs = documentRepository.findByApplicationIdAndMilestoneId(app.getId(), t2.getId());
        assertEquals(1, scopedDocs.size());
        assertEquals("progress_report.pdf", scopedDocs.get(0).getFileName());

        // Step 4: Field Officer verifies Term 2 document
        documentService.updateDocumentVerification(uploadedDoc.getId(), "VERIFIED", "Progress report verified by FO", "fo@gov.in");
        t2.setComplianceVerificationStatus("VERIFIED");
        t2.setVerifiedBy("fo@gov.in");
        t2.setVerifiedDate(LocalDateTime.now());
        milestoneRepository.save(t2);

        // Check if verified term appears in Finance pending queue
        final Long targetT2Id = t2.getId();
        List<Milestone> verifiedTermsPendingRelease = disbursementService.getVerifiedTermsPendingRelease();
        assertTrue(verifiedTermsPendingRelease.stream().anyMatch(m -> m.getId().equals(targetT2Id)));

        // Step 5: Finance releases Term 2
        Milestone rel2 = disbursementService.releaseMilestone(t2.getId());
        assertNotNull(rel2);
        assertEquals(0, new BigDecimal("24000.00").compareTo(rel2.getMilestoneAmount()));

        // Term 3 is now activated
        t3 = milestoneRepository.findById(t3.getId()).orElseThrow();
        assertEquals("COMMISSIONING_CERTIFICATE", t3.getRequiredDocumentType());

        // Step 6: Beneficiary uploads Term 3 document
        MockMultipartFile doc3 = new MockMultipartFile(
                "file", "term3_cert.pdf", "application/pdf", "Term 3 PDF content".getBytes()
        );
        Document uploadedDoc3 = documentService.saveComplianceDocument(
                beneficiary, app, t3, "COMMISSIONING_CERTIFICATE", doc3
        );
        documentService.updateDocumentVerification(uploadedDoc3.getId(), "VERIFIED", "Term 3 cert verified", "fo@gov.in");
        t3.setSubmittedDocument(uploadedDoc3);
        t3.setComplianceVerificationStatus("VERIFIED");
        milestoneRepository.save(t3);

        // Step 7: Final term release by Finance
        Milestone rel3 = disbursementService.releaseMilestone(t3.getId());
        assertNotNull(rel3);
        assertEquals(0, new BigDecimal("18000.00").compareTo(rel3.getMilestoneAmount()));

        // Verify final completion state
        Disbursement finalD = disbursementRepository.findById(d.getId()).orElseThrow();
        assertEquals("COMPLETED", finalD.getStatus());
        assertEquals(0, new BigDecimal("60000.00").compareTo(finalD.getDisbursedAmount()));
        assertEquals(0, new BigDecimal("0.00").compareTo(finalD.getRemainingAmount()));

        Application finalApp = applicationRepository.findById(app.getId()).orElseThrow();
        assertEquals("COMPLETED", finalApp.getCurrentStage());
    }

    @Test
    void testValidation_InvalidFileFormat_Rejected() {
        MockMultipartFile exeFile = new MockMultipartFile(
                "file", "virus.exe", "application/x-msdownload", "bad content".getBytes()
        );

        String validationError = documentService.validateDocument(exeFile, "INCOME_CERTIFICATE");
        assertNotNull(validationError);
        assertTrue(validationError.contains("Unsupported file format"));
    }

    @Test
    void testValidation_FileSizeOver5MB_Rejected() {
        byte[] bigData = new byte[6 * 1024 * 1024]; // 6 MB
        MockMultipartFile bigFile = new MockMultipartFile(
                "file", "large.pdf", "application/pdf", bigData
        );

        String validationError = documentService.validateDocument(bigFile, "INCOME_CERTIFICATE");
        assertNotNull(validationError);
        assertTrue(validationError.contains("File size must not exceed 5 MB"));
    }
}
