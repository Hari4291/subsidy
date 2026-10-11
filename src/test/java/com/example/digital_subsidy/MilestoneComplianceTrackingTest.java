package com.example.digital_subsidy;

import com.example.digital_subsidy.dto.ComplianceSubmissionDto;
import com.example.digital_subsidy.dto.ComplianceVerificationDto;
import com.example.digital_subsidy.dto.StageReleaseDto;
import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.*;
import com.example.digital_subsidy.service.DisbursementService;
import com.example.digital_subsidy.service.DocumentService;
import com.example.digital_subsidy.service.ReminderSchedulerService;
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
public class MilestoneComplianceTrackingTest {

    @Autowired
    private DisbursementService disbursementService;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private ReminderSchedulerService reminderSchedulerService;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private BeneficiaryRepository beneficiaryRepository;

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private User beneficiaryUser;
    private User otherUser;
    private User officerUser;
    private Beneficiary beneficiary;
    private Beneficiary otherBeneficiary;
    private Scheme testScheme;

    @BeforeEach
    void setUp() {
        long ts = System.currentTimeMillis();

        beneficiaryUser = new User();
        beneficiaryUser.setEmail("ben_comp_" + ts + "@gov.in");
        beneficiaryUser.setPassword("Password@123");
        beneficiaryUser.setFullName("Compliance Beneficiary");
        beneficiaryUser.setMobileNumber("911" + (ts % 10000000));
        beneficiaryUser = userRepository.save(beneficiaryUser);

        beneficiary = new Beneficiary();
        beneficiary.setUser(beneficiaryUser);
        beneficiary.setIdentityType("AADHAAR");
        beneficiary.setIdentityNumber("998877" + (ts % 1000000));
        beneficiary.setAddress("100 Compliance Street");
        beneficiary.setAge(30);
        beneficiary.setGender("MALE");
        beneficiary.setPincode("500001");
        beneficiary.setCategory("GENERAL");
        beneficiary.setState("TELANGANA");
        beneficiary.setDistrict("HYDERABAD");
        beneficiary = beneficiaryRepository.save(beneficiary);

        otherUser = new User();
        otherUser.setEmail("other_user_" + ts + "@gov.in");
        otherUser.setPassword("Password@123");
        otherUser.setFullName("Other User");
        otherUser.setMobileNumber("922" + (ts % 10000000));
        otherUser = userRepository.save(otherUser);

        otherBeneficiary = new Beneficiary();
        otherBeneficiary.setUser(otherUser);
        otherBeneficiary.setIdentityType("AADHAAR");
        otherBeneficiary.setIdentityNumber("112233" + (ts % 1000000));
        otherBeneficiary.setAddress("200 Other Street");
        otherBeneficiary.setAge(28);
        otherBeneficiary.setGender("FEMALE");
        otherBeneficiary.setPincode("500002");
        otherBeneficiary.setCategory("OBC");
        otherBeneficiary.setState("TELANGANA");
        otherBeneficiary.setDistrict("HYDERABAD");
        otherBeneficiary = beneficiaryRepository.save(otherBeneficiary);

        officerUser = new User();
        officerUser.setEmail("officer_comp_" + ts + "@gov.in");
        officerUser.setPassword("Password@123");
        officerUser.setFullName("Verification Officer");
        officerUser.setMobileNumber("933" + (ts % 10000000));
        officerUser = userRepository.save(officerUser);

        testScheme = new Scheme();
        testScheme.setName("Compliance Scheme " + ts);
        testScheme.setCategory("GENERAL");
        testScheme.setTotalBudget(new BigDecimal("1000000.00"));
        testScheme.setRemainingBudget(new BigDecimal("1000000.00"));
        testScheme.setMinAmount(new BigDecimal("1000.00"));
        testScheme.setMaxAmount(new BigDecimal("200000.00"));
        testScheme.setStatus("ACTIVE");
        testScheme = schemeRepository.save(testScheme);
    }

    private Application createApprovedApp(BigDecimal sanctionedAmt) {
        Application app = new Application();
        app.setApplicationNumber("APP-COMP-" + System.currentTimeMillis());
        app.setBeneficiary(beneficiary);
        app.setScheme(testScheme);
        app.setRequestedAmount(sanctionedAmt);
        app.setSanctionedAmount(sanctionedAmt);
        app.setStatus("APPROVED");
        app.setCurrentStage("DISBURSEMENT");
        return applicationRepository.save(app);
    }

    @Test
    @DisplayName("Test 1: Compliance Milestone Creation and Attributes")
    void testComplianceMilestoneCreation() {
        Application app = createApprovedApp(new BigDecimal("40000.00"));
        Disbursement d = disbursementService.initializeDisbursement(app, beneficiaryUser);

        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(d);
        assertEquals(1, milestones.size());
        Milestone m = milestones.get(0);
        assertTrue(m.getRequiredFlag());
        assertEquals("PENDING", m.getComplianceVerificationStatus());
        assertNotNull(m.getDueDate());
    }

    @Test
    @DisplayName("Test 2 & 3 & 8: Valid Beneficiary Proof Upload transitions to SUBMITTED state")
    void testBeneficiaryProofUploadValid() throws Exception {
        Application app = createApprovedApp(new BigDecimal("40000.00"));
        Disbursement d = disbursementService.initializeDisbursement(app, beneficiaryUser);
        Milestone m = milestoneRepository.findByDisbursementOrderByIdAsc(d).get(0);

        MockMultipartFile file = new MockMultipartFile("file", "proof.pdf", "application/pdf", "Valid proof content".getBytes());
        ComplianceSubmissionDto dto = new ComplianceSubmissionDto();
        dto.setMilestoneId(m.getId());
        dto.setBeneficiaryRemarks("Uploaded invoice proof");

        Milestone updated = disbursementService.submitComplianceProof(dto, file, beneficiaryUser.getEmail());
        assertEquals("SUBMITTED", updated.getComplianceVerificationStatus());
        assertNotNull(updated.getSubmittedDocument());
        assertNotNull(updated.getSubmittedDate());
        assertEquals("Uploaded invoice proof", updated.getBeneficiaryRemarks());
    }

    @Test
    @DisplayName("Test 4: Empty File Upload Rejected")
    void testEmptyFileRejected() {
        Application app = createApprovedApp(new BigDecimal("40000.00"));
        Disbursement d = disbursementService.initializeDisbursement(app, beneficiaryUser);
        Milestone m = milestoneRepository.findByDisbursementOrderByIdAsc(d).get(0);

        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        ComplianceSubmissionDto dto = new ComplianceSubmissionDto();
        dto.setMilestoneId(m.getId());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            disbursementService.submitComplianceProof(dto, emptyFile, beneficiaryUser.getEmail());
        });
        assertTrue(ex.getMessage().contains("empty"));
    }

    @Test
    @DisplayName("Test 5: Unsupported File Extension Rejected")
    void testUnsupportedExtensionRejected() {
        Application app = createApprovedApp(new BigDecimal("40000.00"));
        Disbursement d = disbursementService.initializeDisbursement(app, beneficiaryUser);
        Milestone m = milestoneRepository.findByDisbursementOrderByIdAsc(d).get(0);

        MockMultipartFile exeFile = new MockMultipartFile("file", "script.exe", "application/x-msdownload", "exe bytes".getBytes());
        ComplianceSubmissionDto dto = new ComplianceSubmissionDto();
        dto.setMilestoneId(m.getId());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            disbursementService.submitComplianceProof(dto, exeFile, beneficiaryUser.getEmail());
        });
        assertTrue(ex.getMessage().contains("Unsupported file format"));
    }

    @Test
    @DisplayName("Test 6: File Exceeding 5 MB Limit Rejected")
    void testFileExceeding5MBRejected() {
        Application app = createApprovedApp(new BigDecimal("40000.00"));
        Disbursement d = disbursementService.initializeDisbursement(app, beneficiaryUser);
        Milestone m = milestoneRepository.findByDisbursementOrderByIdAsc(d).get(0);

        byte[] largeBytes = new byte[5 * 1024 * 1024 + 10];
        MockMultipartFile largeFile = new MockMultipartFile("file", "large.pdf", "application/pdf", largeBytes);
        ComplianceSubmissionDto dto = new ComplianceSubmissionDto();
        dto.setMilestoneId(m.getId());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            disbursementService.submitComplianceProof(dto, largeFile, beneficiaryUser.getEmail());
        });
        assertTrue(ex.getMessage().contains("5 MB"));
    }

    @Test
    @DisplayName("Test 7 & 26: IDOR Protection — Unauthorized Beneficiary Cannot Access or Submit Proof")
    void testIDORProtectionOnSubmission() {
        Application app = createApprovedApp(new BigDecimal("40000.00"));
        Disbursement d = disbursementService.initializeDisbursement(app, beneficiaryUser);
        Milestone m = milestoneRepository.findByDisbursementOrderByIdAsc(d).get(0);

        MockMultipartFile file = new MockMultipartFile("file", "proof.pdf", "application/pdf", "Valid proof content".getBytes());
        ComplianceSubmissionDto dto = new ComplianceSubmissionDto();
        dto.setMilestoneId(m.getId());

        assertThrows(AccessDeniedException.class, () -> {
            disbursementService.submitComplianceProof(dto, file, otherUser.getEmail());
        });
    }

    @Test
    @DisplayName("Test 9: Authorized Officer Verification Workflow")
    void testOfficerVerificationSuccess() throws Exception {
        Application app = createApprovedApp(new BigDecimal("40000.00"));
        Disbursement d = disbursementService.initializeDisbursement(app, beneficiaryUser);
        Milestone m = milestoneRepository.findByDisbursementOrderByIdAsc(d).get(0);

        MockMultipartFile file = new MockMultipartFile("file", "proof.pdf", "application/pdf", "Proof content".getBytes());
        ComplianceSubmissionDto subDto = new ComplianceSubmissionDto();
        subDto.setMilestoneId(m.getId());
        disbursementService.submitComplianceProof(subDto, file, beneficiaryUser.getEmail());

        ComplianceVerificationDto verDto = new ComplianceVerificationDto();
        verDto.setMilestoneId(m.getId());
        verDto.setAction("VERIFY");
        verDto.setOfficerRemarks("Verified and approved");

        Milestone verified = disbursementService.verifyCompliance(verDto, officerUser.getEmail());
        assertEquals("VERIFIED", verified.getComplianceVerificationStatus());
        assertNotNull(verified.getVerifiedDate());
        assertEquals(officerUser.getEmail(), verified.getVerifiedBy());
    }

    @Test
    @DisplayName("Test 10: Officer Rejection Requires Mandatory Remarks")
    void testOfficerRejectionRequiresRemarks() throws Exception {
        Application app = createApprovedApp(new BigDecimal("40000.00"));
        Disbursement d = disbursementService.initializeDisbursement(app, beneficiaryUser);
        Milestone m = milestoneRepository.findByDisbursementOrderByIdAsc(d).get(0);

        MockMultipartFile file = new MockMultipartFile("file", "proof.pdf", "application/pdf", "Proof content".getBytes());
        ComplianceSubmissionDto subDto = new ComplianceSubmissionDto();
        subDto.setMilestoneId(m.getId());
        disbursementService.submitComplianceProof(subDto, file, beneficiaryUser.getEmail());

        ComplianceVerificationDto verDtoNoRemarks = new ComplianceVerificationDto();
        verDtoNoRemarks.setMilestoneId(m.getId());
        verDtoNoRemarks.setAction("REJECT");
        verDtoNoRemarks.setOfficerRemarks("   "); // blank

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            disbursementService.verifyCompliance(verDtoNoRemarks, officerUser.getEmail());
        });
        assertTrue(ex.getMessage().toLowerCase().contains("mandatory"));
    }

    @Test
    @DisplayName("Test 11 & 12: Re-upload after Rejection Preserves Previous History")
    void testReuploadPreservesHistory() throws Exception {
        Application app = createApprovedApp(new BigDecimal("40000.00"));
        Disbursement d = disbursementService.initializeDisbursement(app, beneficiaryUser);
        Milestone m = milestoneRepository.findByDisbursementOrderByIdAsc(d).get(0);

        // 1st Upload
        MockMultipartFile file1 = new MockMultipartFile("file", "invoice_v1.pdf", "application/pdf", "Version 1 bytes".getBytes());
        ComplianceSubmissionDto subDto1 = new ComplianceSubmissionDto();
        subDto1.setMilestoneId(m.getId());
        disbursementService.submitComplianceProof(subDto1, file1, beneficiaryUser.getEmail());

        // Officer rejects
        ComplianceVerificationDto rejectDto = new ComplianceVerificationDto();
        rejectDto.setMilestoneId(m.getId());
        rejectDto.setAction("REJECT");
        rejectDto.setOfficerRemarks("Invoice total mismatch");
        disbursementService.verifyCompliance(rejectDto, officerUser.getEmail());

        Milestone rejectedM = milestoneRepository.findById(m.getId()).orElseThrow();
        assertEquals("REUPLOAD_REQUIRED", rejectedM.getComplianceVerificationStatus());
        assertEquals("Invoice total mismatch", rejectedM.getRejectionReason());

        // 2nd Upload (Re-upload)
        MockMultipartFile file2 = new MockMultipartFile("file", "invoice_v2.pdf", "application/pdf", "Version 2 bytes".getBytes());
        ComplianceSubmissionDto subDto2 = new ComplianceSubmissionDto();
        subDto2.setMilestoneId(m.getId());
        subDto2.setBeneficiaryRemarks("Corrected invoice attached");
        disbursementService.submitComplianceProof(subDto2, file2, beneficiaryUser.getEmail());

        Milestone reuploadedM = milestoneRepository.findById(m.getId()).orElseThrow();
        assertEquals("SUBMITTED", reuploadedM.getComplianceVerificationStatus());

        // Verify Document History in Repository
        List<Document> docHistory = documentRepository.findByMilestoneIdOrderByIdDesc(m.getId());
        assertEquals(2, docHistory.size());
        assertEquals("ARCHIVED", docHistory.get(1).getStatus()); // older doc archived
        assertEquals("ACTIVE", docHistory.get(0).getStatus()); // newer doc active
    }

    @Test
    @DisplayName("Test 14 & 15: Missing Compliance Blocks Release & Verified Compliance Allows Release")
    void testReleaseBlockingByCompliance() throws Exception {
        Application app = createApprovedApp(new BigDecimal("40000.00"));
        Disbursement d = disbursementService.initializeDisbursement(app, beneficiaryUser);
        Milestone m = milestoneRepository.findByDisbursementOrderByIdAsc(d).get(0);

        StageReleaseDto releaseDto = new StageReleaseDto();
        releaseDto.setMilestoneId(m.getId());

        // 1. Attempt release when compliance is PENDING -> BLOCKED
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            disbursementService.releaseMilestone(releaseDto, officerUser.getEmail());
        });
        assertTrue(ex.getMessage().contains("Release blocked"));

        // 2. Upload proof & verify compliance
        MockMultipartFile file = new MockMultipartFile("file", "proof.pdf", "application/pdf", "Valid proof content".getBytes());
        ComplianceSubmissionDto subDto = new ComplianceSubmissionDto();
        subDto.setMilestoneId(m.getId());
        disbursementService.submitComplianceProof(subDto, file, beneficiaryUser.getEmail());

        ComplianceVerificationDto verDto = new ComplianceVerificationDto();
        verDto.setMilestoneId(m.getId());
        verDto.setAction("VERIFY");
        verDto.setOfficerRemarks("Approved");
        disbursementService.verifyCompliance(verDto, officerUser.getEmail());

        // 3. Attempt release when compliance is VERIFIED -> SUCCESS
        Milestone released = disbursementService.releaseMilestone(releaseDto, officerUser.getEmail());
        assertEquals("COMPLETED", released.getCompletionStatus());
    }

    @Test
    @DisplayName("Test 16 & 17: Upcoming Reminder Generation and Idempotence")
    void testReminderGenerationAndIdempotence() {
        Application app = createApprovedApp(new BigDecimal("40000.00"));
        Disbursement d = disbursementService.initializeDisbursement(app, beneficiaryUser);
        Milestone m = milestoneRepository.findByDisbursementOrderByIdAsc(d).get(0);

        // Set due date to 3 days in future
        m.setDueDate(LocalDate.now().plusDays(3));
        m.setComplianceVerificationStatus("PENDING");
        milestoneRepository.save(m);

        // Process reminders 1st time
        reminderSchedulerService.processComplianceReminders();

        Milestone updatedM = milestoneRepository.findById(m.getId()).orElseThrow();
        assertEquals("3_DAYS_SENT", updatedM.getReminderStatus());
        assertNotNull(updatedM.getLastReminderDate());

        long count1 = notificationRepository.count();

        // Process reminders 2nd time -> Idempotent (no duplicate reminders/notifications)
        reminderSchedulerService.processComplianceReminders();
        long count2 = notificationRepository.count();

        assertEquals(count1, count2, "Idempotent reminder processing MUST NOT create duplicate notifications");
    }

    @Test
    @DisplayName("Test 18, 19, 13: Overdue Detection and Non-Compliance Flagging")
    void testOverdueDetectionAndNonComplianceFlag() {
        Application app = createApprovedApp(new BigDecimal("40000.00"));
        Disbursement d = disbursementService.initializeDisbursement(app, beneficiaryUser);
        Milestone m = milestoneRepository.findByDisbursementOrderByIdAsc(d).get(0);

        // Set due date to yesterday
        m.setDueDate(LocalDate.now().minusDays(1));
        m.setComplianceVerificationStatus("PENDING");
        milestoneRepository.save(m);

        // Process overdue job
        reminderSchedulerService.processOverdueCompliance();

        Milestone overdueM = milestoneRepository.findById(m.getId()).orElseThrow();
        assertEquals("OVERDUE", overdueM.getComplianceVerificationStatus());
        assertTrue(overdueM.getOverdueFlag());

        // Verified milestone should NOT be marked overdue
        overdueM.setComplianceVerificationStatus("VERIFIED");
        milestoneRepository.save(overdueM);

        reminderSchedulerService.processOverdueCompliance();
        Milestone verifiedM = milestoneRepository.findById(m.getId()).orElseThrow();
        assertEquals("VERIFIED", verifiedM.getComplianceVerificationStatus());
    }

    @Test
    @DisplayName("Test 20 & 21: Single-Phase and Three-Phase Independent Compliance Enforcement")
    void testSingleAndThreePhaseComplianceEnforcement() throws Exception {
        // Single Phase
        Application app1 = createApprovedApp(new BigDecimal("40000.00"));
        Disbursement d1 = disbursementService.initializeDisbursement(app1, beneficiaryUser);
        List<Milestone> m1List = milestoneRepository.findByDisbursementOrderByIdAsc(d1);
        assertEquals(1, m1List.size());
        assertEquals("PENDING", m1List.get(0).getComplianceVerificationStatus());

        // Three Phase
        Application app3 = createApprovedApp(new BigDecimal("60000.00"));
        Disbursement d3 = disbursementService.initializeDisbursement(app3, beneficiaryUser);
        List<Milestone> m3List = milestoneRepository.findByDisbursementOrderByIdAsc(d3);
        assertEquals(3, m3List.size());

        // Verify Phase 1 compliance, Phase 2 remains PENDING
        Milestone p1 = m3List.get(0);
        MockMultipartFile file = new MockMultipartFile("file", "proof.pdf", "application/pdf", "Valid proof content".getBytes());
        ComplianceSubmissionDto subDto = new ComplianceSubmissionDto();
        subDto.setMilestoneId(p1.getId());
        disbursementService.submitComplianceProof(subDto, file, beneficiaryUser.getEmail());

        ComplianceVerificationDto verDto = new ComplianceVerificationDto();
        verDto.setMilestoneId(p1.getId());
        verDto.setAction("VERIFY");
        verDto.setOfficerRemarks("Approved P1");
        disbursementService.verifyCompliance(verDto, officerUser.getEmail());

        // Release Phase 1 -> ALLOWED
        StageReleaseDto relDto1 = new StageReleaseDto();
        relDto1.setMilestoneId(p1.getId());
        disbursementService.releaseMilestone(relDto1, officerUser.getEmail());

        // Release Phase 2 -> BLOCKED because Phase 2 compliance is still PENDING
        StageReleaseDto relDto2 = new StageReleaseDto();
        relDto2.setMilestoneId(m3List.get(1).getId());
        assertThrows(IllegalStateException.class, () -> {
            disbursementService.releaseMilestone(relDto2, officerUser.getEmail());
        });
    }

    @Test
    @DisplayName("Test 27: Audit Log Creation for Compliance Events")
    void testAuditLogEntriesCreatedForCompliance() throws Exception {
        Application app = createApprovedApp(new BigDecimal("40000.00"));
        Disbursement d = disbursementService.initializeDisbursement(app, beneficiaryUser);
        Milestone m = milestoneRepository.findByDisbursementOrderByIdAsc(d).get(0);

        MockMultipartFile file = new MockMultipartFile("file", "proof.pdf", "application/pdf", "Proof content".getBytes());
        ComplianceSubmissionDto subDto = new ComplianceSubmissionDto();
        subDto.setMilestoneId(m.getId());
        disbursementService.submitComplianceProof(subDto, file, beneficiaryUser.getEmail());

        List<AuditLog> logs = auditLogRepository.findAll();
        boolean foundUploadLog = logs.stream().anyMatch(l -> "COMPLIANCE_PROOF_UPLOADED".equals(l.getAction()));
        assertTrue(foundUploadLog, "Audit log for COMPLIANCE_PROOF_UPLOADED must be generated");
    }
}
