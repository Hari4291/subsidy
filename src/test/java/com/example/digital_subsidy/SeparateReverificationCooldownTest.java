package com.example.digital_subsidy;

import com.example.digital_subsidy.dto.ApplicationSubmissionDto;
import com.example.digital_subsidy.dto.VerificationActionDto;
import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.*;
import com.example.digital_subsidy.service.ApplicationService;
import com.example.digital_subsidy.service.DocumentService;
import com.example.digital_subsidy.service.VerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
public class SeparateReverificationCooldownTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OfficerRepository officerRepository;

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private BeneficiaryRepository beneficiaryRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ApplicationRestrictionRepository restrictionRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private VerificationRepository verificationRepository;

    @Autowired
    private VerificationService verificationService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private DocumentService documentService;

    private User doUser;
    private User foUser;
    private User benUser;
    private Beneficiary testBeneficiary;
    private Scheme scheme;

    @BeforeEach
    void setUp() {
        String timestamp = String.valueOf(System.currentTimeMillis());

        // 1. Create District Officer
        doUser = new User();
        doUser.setEmail("do_sep_" + timestamp + "@gov.in");
        doUser.setFullName("District Officer Separate");
        doUser.setMobileNumber("9811" + timestamp.substring(timestamp.length() - 6));
        doUser.setPassword("password");
        doUser.setEnabled(true);
        userRepository.save(doUser);

        Officer doOfficer = new Officer(doUser, doUser.getFullName(), doUser.getEmail(), doUser.getMobileNumber(), "DISTRICT_OFFICER", "Telangana", "Hyderabad");
        officerRepository.save(doOfficer);

        // 2. Create Field Officer
        foUser = new User();
        foUser.setEmail("fo_sep_" + timestamp + "@gov.in");
        foUser.setFullName("Field Officer Separate");
        foUser.setMobileNumber("9822" + timestamp.substring(timestamp.length() - 6));
        foUser.setPassword("password");
        foUser.setEnabled(true);
        userRepository.save(foUser);

        Officer foOfficer = new Officer(foUser, foUser.getFullName(), foUser.getEmail(), foUser.getMobileNumber(), "FIELD_OFFICER", "Telangana", "Hyderabad");
        officerRepository.save(foOfficer);

        // 3. Create Beneficiary
        benUser = new User();
        benUser.setEmail("ben_sep_" + timestamp + "@gov.in");
        benUser.setFullName("Beneficiary Separate");
        benUser.setMobileNumber("9833" + timestamp.substring(timestamp.length() - 6));
        benUser.setPassword("password");
        benUser.setEnabled(true);
        userRepository.save(benUser);

        testBeneficiary = new Beneficiary();
        testBeneficiary.setUser(benUser);
        testBeneficiary.setAge(28);
        testBeneficiary.setGender("Male");
        testBeneficiary.setCategory("GENERAL");
        testBeneficiary.setState("Telangana");
        testBeneficiary.setDistrict("Hyderabad");
        testBeneficiary.setAddress("Street 5, Hyderabad");
        testBeneficiary.setPincode("500001");
        testBeneficiary.setIdentityType("Aadhaar");
        testBeneficiary.setIdentityNumber("1234-5678-9012");
        beneficiaryRepository.save(testBeneficiary);

        // 4. Create Scheme with 30 days cooldown
        scheme = new Scheme();
        scheme.setName("Separate Test Scheme " + timestamp);
        scheme.setCategory("Agriculture");
        scheme.setMinAmount(new BigDecimal("5000.00"));
        scheme.setMaxAmount(new BigDecimal("50000.00"));
        scheme.setTotalBudget(new BigDecimal("1000000.00"));
        scheme.setRemainingBudget(new BigDecimal("1000000.00"));
        scheme.setRejectionCooldownDays(30);
        scheme.setStatus("ACTIVE");
        schemeRepository.save(scheme);
    }

    private Application createAndSubmitApplication() {
        ApplicationSubmissionDto dto = new ApplicationSubmissionDto();
        dto.setSchemeId(scheme.getId());
        dto.setRequestedAmount(new BigDecimal("40000.00"));
        dto.setAnnualIncome(new BigDecimal("150000.00"));
        return applicationService.submitApplication(testBeneficiary, dto);
    }

    @Test
    @DisplayName("Test 1: District Officer -> Re-Verification Required -> NO Cooldown created & App continues")
    void test1_ReverificationNoCooldown() {
        Application app = createAndSubmitApplication();
        Long appId = app.getId();

        // DO requests re-verification
        VerificationActionDto actionDto = new VerificationActionDto();
        actionDto.setApplicationId(appId);
        actionDto.setAction("REQUEST_RE_VERIFICATION");
        actionDto.setRemarks("Please re-check land documents and bank passbook");

        verificationService.processVerification(doUser.getEmail(), "DISTRICT_OFFICER", actionDto);

        Application updatedApp = applicationRepository.findById(appId).orElseThrow();
        assertEquals("RE_VERIFICATION_REQUIRED", updatedApp.getStatus());
        assertEquals("FIELD_VERIFICATION", updatedApp.getCurrentStage());
        assertEquals("Please re-check land documents and bank passbook", updatedApp.getRejectionReason());

        // Verify NO restriction was created
        List<ApplicationRestriction> restrictions = restrictionRepository.findByApplicationId(appId);
        assertTrue(restrictions.isEmpty(), "Re-verification MUST NOT create any restriction cooldown record.");
    }

    @Test
    @DisplayName("Test 2: Re-verification -> Beneficiary correction -> FO verification -> Returns to DISTRICT_REVIEW (Same app)")
    void test2_ReverificationWorkflowLoop() throws Exception {
        Application app = createAndSubmitApplication();
        Long appId = app.getId();

        // 1. DO requests re-verification
        VerificationActionDto doAction = new VerificationActionDto();
        doAction.setApplicationId(appId);
        doAction.setAction("REQUEST_RE_VERIFICATION");
        doAction.setRemarks("Aadhaar blurry, upload clear copy");
        verificationService.processVerification(doUser.getEmail(), "DISTRICT_OFFICER", doAction);

        // 2. Beneficiary uploads replacement document
        MockMultipartFile aadhaarFile = new MockMultipartFile("docAadhaarFile", "aadhaar_clean.pdf", "application/pdf", "Clean Content".getBytes());
        applicationService.reuploadDocuments(app, aadhaarFile, null, documentService);

        Application reuploadedApp = applicationRepository.findById(appId).orElseThrow();
        assertEquals("PENDING", reuploadedApp.getStatus());
        assertEquals("FIELD_VERIFICATION", reuploadedApp.getCurrentStage());
        assertEquals(app.getApplicationNumber(), reuploadedApp.getApplicationNumber(), "Must remain SAME application #101.");

        // 3. FO re-verifies and approves
        VerificationActionDto foAction = new VerificationActionDto();
        foAction.setApplicationId(appId);
        foAction.setAction("APPROVE");
        foAction.setRemarks("Replacement Aadhaar document verified cleanly.");
        verificationService.processVerification(foUser.getEmail(), "FIELD_OFFICER", foAction);

        Application reverifiedApp = applicationRepository.findById(appId).orElseThrow();
        assertEquals("PENDING", reverifiedApp.getStatus());
        assertEquals("DISTRICT_REVIEW", reverifiedApp.getCurrentStage());
        assertEquals(app.getId(), reverifiedApp.getId(), "Must remain SAME application #101.");
    }

    @Test
    @DisplayName("Test 3: District Officer -> Final Reject -> Cooldown created")
    void test3_FinalRejectCreatesCooldown() {
        Application app = createAndSubmitApplication();
        Long appId = app.getId();

        VerificationActionDto actionDto = new VerificationActionDto();
        actionDto.setApplicationId(appId);
        actionDto.setAction("FINAL_REJECT");
        actionDto.setRemarks("Ineligible due to fraudulent documents");

        verificationService.processVerification(doUser.getEmail(), "DISTRICT_OFFICER", actionDto);

        Application rejectedApp = applicationRepository.findById(appId).orElseThrow();
        assertEquals("FINAL_REJECTED", rejectedApp.getStatus());
        assertEquals("REJECTED", rejectedApp.getCurrentStage());

        List<ApplicationRestriction> restrictions = restrictionRepository.findActiveRestrictions(testBeneficiary.getId(), scheme.getId());
        assertEquals(1, restrictions.size());
        ApplicationRestriction restriction = restrictions.get(0);
        assertEquals("ACTIVE", restriction.getStatus());
        assertEquals(30, restriction.getCooldownPeriodDays());
        assertEquals("Ineligible due to fraudulent documents", restriction.getRejectionReason());
        assertEquals(doUser.getEmail(), restriction.getRejectedBy());
        assertNotNull(restriction.getReapplicationAllowedAt());
    }

    @Test
    @DisplayName("Test 4: Final Reject + active 30-day cooldown -> New application blocked")
    void test4_ActiveCooldownBlocksNewApplication() {
        Application app = createAndSubmitApplication();
        VerificationActionDto actionDto = new VerificationActionDto();
        actionDto.setApplicationId(app.getId());
        actionDto.setAction("FINAL_REJECT");
        actionDto.setRemarks("Ineligible income");
        verificationService.processVerification(doUser.getEmail(), "DISTRICT_OFFICER", actionDto);

        // Beneficiary attempts new application while cooldown is active
        ApplicationSubmissionDto newAppDto = new ApplicationSubmissionDto();
        newAppDto.setSchemeId(scheme.getId());
        newAppDto.setRequestedAmount(new BigDecimal("30000.00"));
        newAppDto.setAnnualIncome(new BigDecimal("150000.00"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            applicationService.submitApplication(testBeneficiary, newAppDto);
        });

        assertTrue(ex.getMessage().contains("Reapplication is restricted until"), "Error message must state restriction.");
    }

    @Test
    @DisplayName("Test 5: Final Reject + cooldown expired -> New application allowed")
    void test5_ExpiredCooldownAllowsNewApplication() {
        Application app = createAndSubmitApplication();
        VerificationActionDto actionDto = new VerificationActionDto();
        actionDto.setApplicationId(app.getId());
        actionDto.setAction("FINAL_REJECT");
        actionDto.setRemarks("Temporary ineligible status");
        verificationService.processVerification(doUser.getEmail(), "DISTRICT_OFFICER", actionDto);

        // Artificially expire the restriction
        List<ApplicationRestriction> restrictions = restrictionRepository.findActiveRestrictions(testBeneficiary.getId(), scheme.getId());
        assertEquals(1, restrictions.size());
        ApplicationRestriction restriction = restrictions.get(0);
        restriction.setReapplicationAllowedAt(LocalDateTime.now().minusDays(1));
        restrictionRepository.save(restriction);

        // Now attempt new application
        ApplicationSubmissionDto newAppDto = new ApplicationSubmissionDto();
        newAppDto.setSchemeId(scheme.getId());
        newAppDto.setRequestedAmount(new BigDecimal("30000.00"));
        newAppDto.setAnnualIncome(new BigDecimal("150000.00"));

        Application newApp = applicationService.submitApplication(testBeneficiary, newAppDto);
        assertNotNull(newApp);
        assertNotNull(newApp.getId());
        assertNotEquals(app.getId(), newApp.getId(), "New application must be created after cooldown expiry.");

        // Check restriction status updated to EXPIRED
        ApplicationRestriction updatedRestriction = restrictionRepository.findById(restriction.getId()).orElseThrow();
        assertEquals("EXPIRED", updatedRestriction.getStatus());
    }

    @Test
    @DisplayName("Test 6: Re-verification application -> Beneficiary must NOT see restriction")
    void test6_ReverificationNoRestrictionMessage() {
        Application app = createAndSubmitApplication();
        VerificationActionDto actionDto = new VerificationActionDto();
        actionDto.setApplicationId(app.getId());
        actionDto.setAction("REQUEST_RE_VERIFICATION");
        actionDto.setRemarks("Update address proof");
        verificationService.processVerification(doUser.getEmail(), "DISTRICT_OFFICER", actionDto);

        Application reverifyApp = applicationRepository.findById(app.getId()).orElseThrow();
        assertEquals("RE_VERIFICATION_REQUIRED", reverifyApp.getStatus());

        List<ApplicationRestriction> activeRestrictions = restrictionRepository.findActiveRestrictions(testBeneficiary.getId(), scheme.getId());
        assertTrue(activeRestrictions.isEmpty(), "No restriction should exist for re-verification.");
    }

    @Test
    @DisplayName("Test 7: Final rejected application -> Active restriction record contains details")
    void test7_FinalRejectedRestrictionDetails() {
        Application app = createAndSubmitApplication();
        VerificationActionDto actionDto = new VerificationActionDto();
        actionDto.setApplicationId(app.getId());
        actionDto.setAction("FINAL_REJECT");
        actionDto.setRemarks("Income above maximum limit");
        verificationService.processVerification(doUser.getEmail(), "DISTRICT_OFFICER", actionDto);

        List<ApplicationRestriction> restrictions = restrictionRepository.findByApplicationId(app.getId());
        assertFalse(restrictions.isEmpty());
        ApplicationRestriction restriction = restrictions.get(0);

        assertEquals("Income above maximum limit", restriction.getRejectionReason());
        assertEquals(doUser.getEmail(), restriction.getRejectedBy());
        assertEquals("DISTRICT_OFFICER", restriction.getRejectedRole());
        assertEquals(30, restriction.getCooldownPeriodDays());
        assertNotNull(restriction.getRejectedAt());
        assertNotNull(restriction.getReapplicationAllowedAt());
    }

    @Test
    @DisplayName("Test 8: Verify historical verification and rejection audit records remain intact")
    void test8_AuditTrailRecordsIntact() throws Exception {
        Application app = createAndSubmitApplication();
        Long appId = app.getId();

        // 1. DO requests re-verification
        VerificationActionDto reverifyAction = new VerificationActionDto();
        reverifyAction.setApplicationId(appId);
        reverifyAction.setAction("REQUEST_RE_VERIFICATION");
        reverifyAction.setRemarks("Need clear bank passbook");
        verificationService.processVerification(doUser.getEmail(), "DISTRICT_OFFICER", reverifyAction);

        // 2. Beneficiary re-uploads doc
        MockMultipartFile passbookFile = new MockMultipartFile("docBankPassbookFile", "passbook_clear.pdf", "application/pdf", "Passbook Content".getBytes());
        Application latestApp = applicationRepository.findById(appId).orElseThrow();
        applicationService.reuploadDocuments(latestApp, null, passbookFile, documentService);

        // 3. FO approves re-verification
        VerificationActionDto foAction = new VerificationActionDto();
        foAction.setApplicationId(appId);
        foAction.setAction("APPROVE");
        foAction.setRemarks("Passbook clear and verified.");
        verificationService.processVerification(foUser.getEmail(), "FIELD_OFFICER", foAction);

        // 4. DO final rejects
        VerificationActionDto finalRejectAction = new VerificationActionDto();
        finalRejectAction.setApplicationId(appId);
        finalRejectAction.setAction("FINAL_REJECT");
        finalRejectAction.setRemarks("Failed final background verification");
        verificationService.processVerification(doUser.getEmail(), "DISTRICT_OFFICER", finalRejectAction);

        // Verify all 3 verification records exist
        Application currentApp = applicationRepository.findById(appId).orElseThrow();
        List<Verification> verifications = verificationRepository.findByApplicationOrderByVerifiedAtDesc(currentApp);
        assertEquals(3, verifications.size(), "All verification history records must be preserved.");

        // Verify audit logs were generated
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assertFalse(auditLogs.isEmpty());
        boolean hasReverifyLog = auditLogs.stream().anyMatch(l -> "RE_VERIFICATION_REQUESTED".equalsIgnoreCase(l.getAction()) || l.getAction().contains("REQUEST_RE_VERIFICATION"));
        boolean hasCorrectionLog = auditLogs.stream().anyMatch(l -> "BENEFICIARY_CORRECTION_SUBMITTED".equalsIgnoreCase(l.getAction()));
        boolean hasFieldReverifyLog = auditLogs.stream().anyMatch(l -> "FIELD_REVERIFICATION_COMPLETED".equalsIgnoreCase(l.getAction()));
        boolean hasFinalRejectLog = auditLogs.stream().anyMatch(l -> "FINAL_REJECTION".equalsIgnoreCase(l.getAction()));

        assertTrue(hasReverifyLog, "Audit log for RE_VERIFICATION_REQUESTED must exist.");
        assertTrue(hasCorrectionLog, "Audit log for BENEFICIARY_CORRECTION_SUBMITTED must exist.");
        assertTrue(hasFieldReverifyLog, "Audit log for FIELD_REVERIFICATION_COMPLETED must exist.");
        assertTrue(hasFinalRejectLog, "Audit log for FINAL_REJECTION must exist.");
    }
}
