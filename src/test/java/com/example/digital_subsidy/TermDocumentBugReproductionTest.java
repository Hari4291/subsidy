package com.example.digital_subsidy;

import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.*;
import com.example.digital_subsidy.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class TermDocumentBugReproductionTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private BeneficiaryRepository beneficiaryRepository;

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

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

    @Autowired
    private ReminderSchedulerService reminderSchedulerService;

    private User beneficiaryUser;
    private Beneficiary beneficiary;
    private Application application;
    private Disbursement disbursement;
    private Milestone milestone;

    @BeforeEach
    void setUp() {
        Role roleBen = roleRepository.findByName("ROLE_BENEFICIARY")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_BENEFICIARY")));

        String email = "repro_ben@test.gov.in";
        beneficiaryUser = userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User();
            u.setEmail(email);
            u.setFullName("Repro Beneficiary");
            u.setPassword("password");
            u.setMobileNumber("9876543219");
            u.getRoles().add(roleBen);
            return userRepository.save(u);
        });

        beneficiary = beneficiaryRepository.findByUserEmail(email).orElseGet(() -> {
            Beneficiary b = new Beneficiary();
            b.setUser(beneficiaryUser);
            b.setState("Andhra Pradesh");
            b.setDistrict("Kakinada");
            b.setAddress("101 Test Road");
            b.setPincode("533001");
            b.setCategory("OBC");
            b.setGender("Male");
            b.setAge(29);
            b.setIdentityType("Aadhaar");
            b.setIdentityNumber("123412341234");
            return beneficiaryRepository.save(b);
        });

        Scheme scheme = schemeRepository.findAll().stream().findFirst().orElseGet(() -> {
            Scheme s = new Scheme();
            s.setName("Single-Phase Test Grant");
            s.setCategory("General");
            s.setMinAmount(new BigDecimal("1000"));
            s.setMaxAmount(new BigDecimal("100000"));
            s.setTotalBudget(new BigDecimal("50000"));
            s.setRemainingBudget(new BigDecimal("50000"));
            s.setStatus("ACTIVE");
            return schemeRepository.save(s);
        });

        application = new Application();
        application.setApplicationNumber("APP-5067");
        application.setBeneficiary(beneficiary);
        application.setScheme(scheme);
        application.setRequestedAmount(new BigDecimal("35000"));
        application.setSanctionedAmount(new BigDecimal("35000"));
        application.setStatus("DISBURSED");
        application.setCurrentStage("DISBURSEMENT");
        application = applicationRepository.save(application);

        disbursement = new Disbursement();
        disbursement.setApplication(application);
        disbursement.setTotalSanctionedAmount(new BigDecimal("35000"));
        disbursement.setDisbursedAmount(BigDecimal.ZERO);
        disbursement.setStatus("PLANNED");
        disbursement = disbursementRepository.save(disbursement);

        milestone = new Milestone();
        milestone.setDisbursement(disbursement);
        milestone.setStageNumber(1);
        milestone.setMilestoneName("Single-Phase Grant Release (100%)");
        milestone.setPercentage(100);
        milestone.setMilestoneAmount(new BigDecimal("35000"));
        milestone.setCompletionStatus("PENDING");
        milestone.setComplianceVerificationStatus("PENDING");
        milestone.setRequiredDocumentType("UTILIZATION_CERTIFICATE");
        milestone.setRequiredFlag(true);
        milestone.setDueDate(LocalDate.now().plusDays(30));
        milestone = milestoneRepository.save(milestone);
    }

    @Test
    @DisplayName("1. Valid JPG upload with uppercase extension .JPG")
    void testValidJpgUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "DSC08876.JPG", "image/jpeg", "content".getBytes());
        Milestone res = disbursementService.submitComplianceProof(milestone.getId(), application.getId(), file, "Remarks", beneficiaryUser.getEmail());
        assertNotNull(res);
        assertEquals("SUBMITTED", res.getComplianceVerificationStatus());
        assertEquals("DSC08876.JPG", res.getSubmittedDocument().getFileName());
    }

    @Test
    @DisplayName("2. Valid JPEG upload")
    void testValidJpegUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "proof.jpeg", "image/jpeg", "content".getBytes());
        Milestone res = disbursementService.submitComplianceProof(milestone.getId(), application.getId(), file, "Remarks", beneficiaryUser.getEmail());
        assertNotNull(res);
        assertEquals("SUBMITTED", res.getComplianceVerificationStatus());
    }

    @Test
    @DisplayName("3. Valid PNG upload")
    void testValidPngUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "receipt.png", "image/png", "content".getBytes());
        Milestone res = disbursementService.submitComplianceProof(milestone.getId(), application.getId(), file, "Remarks", beneficiaryUser.getEmail());
        assertNotNull(res);
        assertEquals("SUBMITTED", res.getComplianceVerificationStatus());
    }

    @Test
    @DisplayName("4. Valid PDF upload")
    void testValidPdfUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", "%PDF-1.4 content".getBytes());
        Milestone res = disbursementService.submitComplianceProof(milestone.getId(), application.getId(), file, "Remarks", beneficiaryUser.getEmail());
        assertNotNull(res);
        assertEquals("SUBMITTED", res.getComplianceVerificationStatus());
    }

    @Test
    @DisplayName("5. File >5MB rejected")
    void testFileExceedsSizeLimit() {
        byte[] largeBytes = new byte[6 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "large.pdf", "application/pdf", largeBytes);
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
                disbursementService.submitComplianceProof(milestone.getId(), application.getId(), file, "Remarks", beneficiaryUser.getEmail())
        );
        assertTrue(ex.getMessage().contains("5 MB"));
    }

    @Test
    @DisplayName("6. Empty file rejected")
    void testEmptyFileRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
                disbursementService.submitComplianceProof(milestone.getId(), application.getId(), file, "Remarks", beneficiaryUser.getEmail())
        );
        assertTrue(ex.getMessage().contains("empty"));
    }

    @Test
    @DisplayName("7. Invalid extension rejected")
    void testInvalidExtensionRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "script.sh", "text/plain", "echo test".getBytes());
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
                disbursementService.submitComplianceProof(milestone.getId(), application.getId(), file, "Remarks", beneficiaryUser.getEmail())
        );
        assertTrue(ex.getMessage().contains("Unsupported file format"));
    }

    @Test
    @DisplayName("10. Mismatched application/milestone relationship rejected")
    void testMismatchedApplicationRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "DSC08876.JPG", "image/jpeg", "content".getBytes());
        Exception ex = assertThrows(IllegalArgumentException.class, () ->
                disbursementService.submitComplianceProof(milestone.getId(), 99999L, file, "Remarks", beneficiaryUser.getEmail())
        );
        assertTrue(ex.getMessage().contains("does not belong to Application ID"));
    }

    @Test
    @DisplayName("11. Beneficiary ownership violation rejected (IDOR)")
    void testIdorViolationRejected() {
        String otherEmail = "other_ben@test.gov.in";
        User otherUser = new User();
        otherUser.setEmail(otherEmail);
        otherUser.setFullName("Other Beneficiary");
        otherUser.setPassword("password");
        otherUser.setMobileNumber("9111111111");
        userRepository.save(otherUser);

        Beneficiary otherBen = new Beneficiary();
        otherBen.setUser(otherUser);
        otherBen.setAge(30);
        otherBen.setCategory("OBC");
        otherBen.setGender("Male");
        otherBen.setState("Andhra Pradesh");
        otherBen.setDistrict("Kakinada");
        otherBen.setAddress("456 Other St");
        otherBen.setPincode("533001");
        otherBen.setIdentityType("Aadhaar");
        otherBen.setIdentityNumber("999911112222");
        beneficiaryRepository.save(otherBen);

        MockMultipartFile file = new MockMultipartFile("file", "DSC08876.JPG", "image/jpeg", "content".getBytes());
        assertThrows(AccessDeniedException.class, () ->
                disbursementService.submitComplianceProof(milestone.getId(), application.getId(), file, "Remarks", otherEmail)
        );
    }

    @Test
    @DisplayName("14-16. Reupload archives old active document and activates new document")
    void testReuploadArchivesOldDocument() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile("file", "doc1.jpg", "image/jpeg", "content1".getBytes());
        disbursementService.submitComplianceProof(milestone.getId(), application.getId(), file1, "First upload", beneficiaryUser.getEmail());

        List<Document> docs1 = documentRepository.findByMilestoneIdOrderByIdDesc(milestone.getId());
        assertEquals(1, docs1.size());
        assertEquals("ACTIVE", docs1.get(0).getStatus());

        MockMultipartFile file2 = new MockMultipartFile("file", "doc2.jpg", "image/jpeg", "content2".getBytes());
        disbursementService.submitComplianceProof(milestone.getId(), application.getId(), file2, "Re-upload", beneficiaryUser.getEmail());

        List<Document> docs2 = documentRepository.findByMilestoneIdOrderByIdDesc(milestone.getId());
        assertEquals(2, docs2.size());
        assertEquals("ACTIVE", docs2.get(0).getStatus());
        assertEquals("ARCHIVED", docs2.get(1).getStatus());
    }

    @Test
    @DisplayName("20. Reminder scheduler skips reminder after submission")
    void testReminderStopsAfterSubmission() throws Exception {
        milestone.setDueDate(LocalDate.now());
        milestoneRepository.save(milestone);

        MockMultipartFile file = new MockMultipartFile("file", "DSC08876.JPG", "image/jpeg", "content".getBytes());
        disbursementService.submitComplianceProof(milestone.getId(), application.getId(), file, "Submitted", beneficiaryUser.getEmail());

        int count = reminderSchedulerService.processComplianceReminders();
        assertEquals(0, count, "No upload reminder should be sent for a SUBMITTED milestone");
    }
}
