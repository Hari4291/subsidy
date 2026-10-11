package com.example.digital_subsidy;

import com.example.digital_subsidy.dto.ApplicationSubmissionDto;
import com.example.digital_subsidy.dto.SchemeConfigDto;
import com.example.digital_subsidy.dto.VerificationActionDto;
import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.*;
import com.example.digital_subsidy.service.ApplicationService;
import com.example.digital_subsidy.service.SchemeService;
import com.example.digital_subsidy.service.VerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class RejectionCooldownTest {

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
    private VerificationService verificationService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private SchemeService schemeService;

    private User doUser;
    private User foUser;
    private User benUser;
    private Beneficiary testBeneficiary;
    private Scheme schemeA;
    private Scheme schemeB;

    @BeforeEach
    void setUp() {
        // 1. Create District Officer
        doUser = new User();
        doUser.setEmail("do_cooldown@gov.in");
        doUser.setFullName("District Officer Cooldown");
        doUser.setMobileNumber("9811111111");
        doUser.setPassword("password");
        doUser.setEnabled(true);
        userRepository.save(doUser);

        Officer doOfficer = new Officer(doUser, doUser.getFullName(), doUser.getEmail(), doUser.getMobileNumber(), "DISTRICT_OFFICER", "Telangana", "Hyderabad");
        officerRepository.save(doOfficer);

        // 2. Create Field Officer
        foUser = new User();
        foUser.setEmail("fo_cooldown@gov.in");
        foUser.setFullName("Field Officer Cooldown");
        foUser.setMobileNumber("9822222222");
        foUser.setPassword("password");
        foUser.setEnabled(true);
        userRepository.save(foUser);

        Officer foOfficer = new Officer(foUser, foUser.getFullName(), foUser.getEmail(), foUser.getMobileNumber(), "FIELD_OFFICER", "Telangana", "Hyderabad");
        officerRepository.save(foOfficer);

        // 3. Create Beneficiary
        benUser = new User();
        benUser.setEmail("ben_cooldown@gov.in");
        benUser.setFullName("Beneficiary Cooldown");
        benUser.setMobileNumber("9833333333");
        benUser.setPassword("password");
        benUser.setEnabled(true);
        userRepository.save(benUser);

        testBeneficiary = new Beneficiary();
        testBeneficiary.setUser(benUser);
        testBeneficiary.setIdentityNumber("ID-7766554433");
        testBeneficiary.setIdentityType("AADHAAR");
        testBeneficiary.setState("Telangana");
        testBeneficiary.setDistrict("Hyderabad");
        testBeneficiary.setAddress("789 Cooldown Ave");
        testBeneficiary.setPincode("500002");
        testBeneficiary.setAge(32);
        testBeneficiary.setGender("Female");
        testBeneficiary.setCategory("General");
        beneficiaryRepository.save(testBeneficiary);

        // 4. Create Scheme A with 30 days cooldown
        schemeA = new Scheme();
        schemeA.setName("Scheme A 30 Days Cooldown " + System.currentTimeMillis());
        schemeA.setMinAmount(new BigDecimal("5000"));
        schemeA.setMaxAmount(new BigDecimal("50000"));
        schemeA.setTotalBudget(new BigDecimal("1000000"));
        schemeA.setRemainingBudget(new BigDecimal("1000000"));
        schemeA.setRejectionCooldownDays(30);
        schemeA.setStatus("ACTIVE");
        schemeRepository.save(schemeA);

        // 5. Create Scheme B with 60 days cooldown
        schemeB = new Scheme();
        schemeB.setName("Scheme B 60 Days Cooldown " + System.currentTimeMillis());
        schemeB.setMinAmount(new BigDecimal("5000"));
        schemeB.setMaxAmount(new BigDecimal("60000"));
        schemeB.setTotalBudget(new BigDecimal("1000000"));
        schemeB.setRemainingBudget(new BigDecimal("1000000"));
        schemeB.setRejectionCooldownDays(60);
        schemeB.setStatus("ACTIVE");
        schemeRepository.save(schemeB);
    }

    @Test
    @DisplayName("Case 1: Application rejected with 30-day cooldown -> new application for same scheme blocked")
    void testRejection30DayCooldownBlocksNewApplication() {
        // Create and reject application on Scheme A
        Application app = new Application();
        app.setApplicationNumber("APP-COOL1-" + System.currentTimeMillis());
        app.setBeneficiary(testBeneficiary);
        app.setScheme(schemeA);
        app.setRequestedAmount(new BigDecimal("30000"));
        app.setCurrentStage("DISTRICT_REVIEW");
        app.setStatus("PENDING");
        applicationRepository.save(app);

        VerificationActionDto rejectDto = new VerificationActionDto();
        rejectDto.setApplicationId(app.getId());
        rejectDto.setAction("REJECT");
        rejectDto.setRemarks("Income documentation unverifiable");

        verificationService.processVerification(doUser.getEmail(), "DISTRICT_OFFICER", rejectDto);

        // Check restriction created
        List<ApplicationRestriction> restrictions = restrictionRepository.findActiveRestrictions(testBeneficiary.getId(), schemeA.getId());
        assertFalse(restrictions.isEmpty(), "Active restriction must be created");
        ApplicationRestriction r = restrictions.get(0);
        assertEquals(30, r.getCooldownPeriodDays());
        assertEquals("Income documentation unverifiable", r.getRejectionReason());

        // Attempting to submit new application for Scheme A must be blocked
        ApplicationSubmissionDto newDto = new ApplicationSubmissionDto();
        newDto.setSchemeId(schemeA.getId());
        newDto.setRequestedAmount(new BigDecimal("30000"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            applicationService.submitApplication(testBeneficiary, newDto);
        });
        assertTrue(ex.getMessage().contains("restricted until"));
    }

    @Test
    @DisplayName("Case 2: Same beneficiary applies to a different scheme -> allowed according to scheme-specific rule")
    void testBeneficiaryCanApplyToDifferentScheme() {
        // Reject on Scheme A
        Application app = new Application();
        app.setApplicationNumber("APP-COOL2-" + System.currentTimeMillis());
        app.setBeneficiary(testBeneficiary);
        app.setScheme(schemeA);
        app.setRequestedAmount(new BigDecimal("30000"));
        app.setCurrentStage("DISTRICT_REVIEW");
        app.setStatus("PENDING");
        applicationRepository.save(app);

        VerificationActionDto rejectDto = new VerificationActionDto();
        rejectDto.setApplicationId(app.getId());
        rejectDto.setAction("REJECT");
        rejectDto.setRemarks("Rejected on Scheme A");
        verificationService.processVerification(doUser.getEmail(), "DISTRICT_OFFICER", rejectDto);

        // Apply for Scheme B -> Should succeed!
        ApplicationSubmissionDto dtoB = new ApplicationSubmissionDto();
        dtoB.setSchemeId(schemeB.getId());
        dtoB.setRequestedAmount(new BigDecimal("40000"));

        Application appB = applicationService.submitApplication(testBeneficiary, dtoB);
        assertNotNull(appB.getId());
        assertEquals(schemeB.getId(), appB.getScheme().getId());
    }

    @Test
    @DisplayName("Case 3: Cooldown expires -> reapplication allowed")
    void testReapplicationAllowedAfterCooldownExpires() {
        // Create an expired restriction manually (reapplicationAllowedAt in past)
        Application app = new Application();
        app.setApplicationNumber("APP-EXPIRE-" + System.currentTimeMillis());
        app.setBeneficiary(testBeneficiary);
        app.setScheme(schemeA);
        app.setRequestedAmount(new BigDecimal("30000"));
        app.setCurrentStage("REJECTED");
        app.setStatus("REJECTED");
        applicationRepository.save(app);

        ApplicationRestriction expiredRestriction = new ApplicationRestriction();
        expiredRestriction.setBeneficiary(testBeneficiary);
        expiredRestriction.setApplication(app);
        expiredRestriction.setScheme(schemeA);
        expiredRestriction.setRejectionReason("Past rejection reason");
        expiredRestriction.setRejectedBy(doUser.getEmail());
        expiredRestriction.setRejectedRole("DISTRICT_OFFICER");
        expiredRestriction.setRejectedAt(LocalDateTime.now().minusDays(31));
        expiredRestriction.setCooldownPeriodDays(30);
        expiredRestriction.setReapplicationAllowedAt(LocalDateTime.now().minusDays(1)); // Past!
        expiredRestriction.setStatus("ACTIVE");
        restrictionRepository.save(expiredRestriction);

        // Reapplication should succeed
        ApplicationSubmissionDto dto = new ApplicationSubmissionDto();
        dto.setSchemeId(schemeA.getId());
        dto.setRequestedAmount(new BigDecimal("30000"));

        Application newApp = applicationService.submitApplication(testBeneficiary, dto);
        assertNotNull(newApp.getId());

        // Restriction status should be updated to EXPIRED
        ApplicationRestriction updatedRestriction = restrictionRepository.findById(expiredRestriction.getId()).orElseThrow();
        assertEquals("EXPIRED", updatedRestriction.getStatus());
    }

    @Test
    @DisplayName("Case 4: Cooldown period is 0 -> no restriction created")
    void testZeroCooldownPeriodNoRestriction() {
        // Create Scheme C with 0 days cooldown
        Scheme schemeZero = new Scheme();
        schemeZero.setName("Zero Cooldown Scheme " + System.currentTimeMillis());
        schemeZero.setMinAmount(new BigDecimal("1000"));
        schemeZero.setMaxAmount(new BigDecimal("20000"));
        schemeZero.setTotalBudget(new BigDecimal("500000"));
        schemeZero.setRemainingBudget(new BigDecimal("500000"));
        schemeZero.setRejectionCooldownDays(0);
        schemeZero.setStatus("ACTIVE");
        schemeRepository.save(schemeZero);

        Application app = new Application();
        app.setApplicationNumber("APP-ZERO-" + System.currentTimeMillis());
        app.setBeneficiary(testBeneficiary);
        app.setScheme(schemeZero);
        app.setRequestedAmount(new BigDecimal("10000"));
        app.setCurrentStage("DISTRICT_REVIEW");
        app.setStatus("PENDING");
        applicationRepository.save(app);

        VerificationActionDto rejectDto = new VerificationActionDto();
        rejectDto.setApplicationId(app.getId());
        rejectDto.setAction("REJECT");
        rejectDto.setRemarks("Rejected under 0-day cooldown scheme");
        verificationService.processVerification(doUser.getEmail(), "DISTRICT_OFFICER", rejectDto);

        // No active restriction should be created
        List<ApplicationRestriction> restrictions = restrictionRepository.findActiveRestrictions(testBeneficiary.getId(), schemeZero.getId());
        assertTrue(restrictions.isEmpty(), "No active restriction created when cooldown is 0");

        // Reapplication should succeed immediately
        ApplicationSubmissionDto dto = new ApplicationSubmissionDto();
        dto.setSchemeId(schemeZero.getId());
        dto.setRequestedAmount(new BigDecimal("10000"));
        Application newApp = applicationService.submitApplication(testBeneficiary, dto);
        assertNotNull(newApp.getId());
    }

    @Test
    @DisplayName("Case 5: Rejection without a reason -> validation failure")
    void testRejectionWithoutReasonThrowsException() {
        Application app = new Application();
        app.setApplicationNumber("APP-NOREASON-" + System.currentTimeMillis());
        app.setBeneficiary(testBeneficiary);
        app.setScheme(schemeA);
        app.setRequestedAmount(new BigDecimal("20000"));
        app.setCurrentStage("FIELD_VERIFICATION");
        app.setStatus("PENDING");
        applicationRepository.save(app);

        VerificationActionDto blankRemarksDto = new VerificationActionDto();
        blankRemarksDto.setApplicationId(app.getId());
        blankRemarksDto.setAction("REJECT");
        blankRemarksDto.setRemarks("   "); // Blank!

        assertThrows(IllegalArgumentException.class, () -> {
            verificationService.processVerification(foUser.getEmail(), "FIELD_OFFICER", blankRemarksDto);
        });
    }

    @Test
    @DisplayName("Case 6: Field Officer creates rejection -> correct restriction created")
    void testFieldOfficerRejectionCreatesRestriction() {
        Application app = new Application();
        app.setApplicationNumber("APP-FOREJECT-" + System.currentTimeMillis());
        app.setBeneficiary(testBeneficiary);
        app.setScheme(schemeA);
        app.setRequestedAmount(new BigDecimal("20000"));
        app.setCurrentStage("FIELD_VERIFICATION");
        app.setStatus("PENDING");
        applicationRepository.save(app);

        VerificationActionDto rejectDto = new VerificationActionDto();
        rejectDto.setApplicationId(app.getId());
        rejectDto.setAction("REJECT");
        rejectDto.setRemarks("Field inspection failed - fake land document");

        verificationService.processVerification(foUser.getEmail(), "FIELD_OFFICER", rejectDto);

        List<ApplicationRestriction> restrictions = restrictionRepository.findActiveRestrictions(testBeneficiary.getId(), schemeA.getId());
        assertFalse(restrictions.isEmpty());
        ApplicationRestriction r = restrictions.get(0);
        assertEquals("FIELD_OFFICER", r.getRejectedRole());
        assertEquals(foUser.getEmail(), r.getRejectedBy());
        assertEquals(30, r.getCooldownPeriodDays());
    }

    @Test
    @DisplayName("Case 7: District Officer creates rejection -> correct restriction created")
    void testDistrictOfficerRejectionCreatesRestriction() {
        Application app = new Application();
        app.setApplicationNumber("APP-DOREJECT-" + System.currentTimeMillis());
        app.setBeneficiary(testBeneficiary);
        app.setScheme(schemeA);
        app.setRequestedAmount(new BigDecimal("20000"));
        app.setCurrentStage("DISTRICT_REVIEW");
        app.setStatus("PENDING");
        applicationRepository.save(app);

        VerificationActionDto rejectDto = new VerificationActionDto();
        rejectDto.setApplicationId(app.getId());
        rejectDto.setAction("REJECT");
        rejectDto.setRemarks("District Review rejection - quota exceeded");

        verificationService.processVerification(doUser.getEmail(), "DISTRICT_OFFICER", rejectDto);

        List<ApplicationRestriction> restrictions = restrictionRepository.findActiveRestrictions(testBeneficiary.getId(), schemeA.getId());
        assertFalse(restrictions.isEmpty());
        ApplicationRestriction r = restrictions.get(0);
        assertEquals("DISTRICT_OFFICER", r.getRejectedRole());
        assertEquals(doUser.getEmail(), r.getRejectedBy());
    }

    @Test
    @DisplayName("Case 8: Unauthorized user tries to bypass restriction -> backend service blocks submission")
    void testDirectServiceSubmissionBypassBlocked() {
        // Reject application on Scheme A
        Application app = new Application();
        app.setApplicationNumber("APP-BYPASS-" + System.currentTimeMillis());
        app.setBeneficiary(testBeneficiary);
        app.setScheme(schemeA);
        app.setRequestedAmount(new BigDecimal("20000"));
        app.setCurrentStage("DISTRICT_REVIEW");
        app.setStatus("PENDING");
        applicationRepository.save(app);

        VerificationActionDto rejectDto = new VerificationActionDto();
        rejectDto.setApplicationId(app.getId());
        rejectDto.setAction("REJECT");
        rejectDto.setRemarks("Direct bypass test rejection");
        verificationService.processVerification(doUser.getEmail(), "DISTRICT_OFFICER", rejectDto);

        // Direct service invocation attempt
        ApplicationSubmissionDto bypassDto = new ApplicationSubmissionDto();
        bypassDto.setSchemeId(schemeA.getId());
        bypassDto.setRequestedAmount(new BigDecimal("20000"));

        assertThrows(IllegalStateException.class, () -> {
            applicationService.submitApplication(testBeneficiary, bypassDto);
        });
    }

    @Test
    @DisplayName("Case 9: Admin changes cooldown configuration -> saved and audit log created")
    void testAdminChangeCooldownConfiguration() {
        SchemeConfigDto configDto = new SchemeConfigDto();
        configDto.setId(schemeA.getId());
        configDto.setName(schemeA.getName());
        configDto.setCategory(schemeA.getCategory());
        configDto.setMaxAmount(schemeA.getMaxAmount());
        configDto.setMinQualifyingScore(60);
        configDto.setRejectionCooldownDays(45); // Admin updates to 45 days

        Scheme updated = schemeService.saveSchemeConfig(configDto, "johnmekala4277@gmail.com");
        assertEquals(45, updated.getRejectionCooldownDays());

        List<AuditLog> logs = auditLogRepository.findAll();
        boolean auditFound = logs.stream().anyMatch(log ->
                log.getRemarks() != null &&
                log.getRemarks().contains("Cooldown: 45 days"));
        assertTrue(auditFound, "Audit log must record cooldown configuration update");
    }

    @Test
    @DisplayName("Case 10: Authorized rejection reversal -> restriction cancellation handled correctly")
    void testRejectionReversalCancelsRestriction() {
        Application app = new Application();
        app.setApplicationNumber("APP-REVERSAL-" + System.currentTimeMillis());
        app.setBeneficiary(testBeneficiary);
        app.setScheme(schemeA);
        app.setRequestedAmount(new BigDecimal("20000"));
        app.setCurrentStage("DISTRICT_REVIEW");
        app.setStatus("PENDING");
        applicationRepository.save(app);

        VerificationActionDto rejectDto = new VerificationActionDto();
        rejectDto.setApplicationId(app.getId());
        rejectDto.setAction("REJECT");
        rejectDto.setRemarks("Rejection for reversal test");
        verificationService.processVerification(doUser.getEmail(), "DISTRICT_OFFICER", rejectDto);

        List<ApplicationRestriction> activeBefore = restrictionRepository.findActiveRestrictions(testBeneficiary.getId(), schemeA.getId());
        assertFalse(activeBefore.isEmpty());
        ApplicationRestriction r = activeBefore.get(0);

        // Officer overturns / cancels restriction
        verificationService.cancelRestriction(r.getId(), "Overturned upon grievance appeal approval", doUser.getEmail());

        ApplicationRestriction cancelledR = restrictionRepository.findById(r.getId()).orElseThrow();
        assertEquals("CANCELLED", cancelledR.getStatus());
        assertEquals("Overturned upon grievance appeal approval", cancelledR.getCancellationReason());
        assertEquals(doUser.getEmail(), cancelledR.getCancelledBy());

        // Reapplication is now allowed
        ApplicationSubmissionDto newDto = new ApplicationSubmissionDto();
        newDto.setSchemeId(schemeA.getId());
        newDto.setRequestedAmount(new BigDecimal("20000"));

        Application reapplication = applicationService.submitApplication(testBeneficiary, newDto);
        assertNotNull(reapplication.getId());
    }

    @Test
    @DisplayName("Case 11: Duplicate active application -> blocked according to existing rules")
    void testDuplicateActiveApplicationBlocked() {
        // Create an active application on Scheme A (in PENDING / FIELD_VERIFICATION stage)
        Application activeApp = new Application();
        activeApp.setApplicationNumber("APP-ACTIVE-" + System.currentTimeMillis());
        activeApp.setBeneficiary(testBeneficiary);
        activeApp.setScheme(schemeA);
        activeApp.setRequestedAmount(new BigDecimal("20000"));
        activeApp.setCurrentStage("FIELD_VERIFICATION");
        activeApp.setStatus("PENDING");
        applicationRepository.save(activeApp);

        // Attempting to submit another application for Scheme A while active exists must be blocked
        ApplicationSubmissionDto dto = new ApplicationSubmissionDto();
        dto.setSchemeId(schemeA.getId());
        dto.setRequestedAmount(new BigDecimal("20000"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            applicationService.submitApplication(testBeneficiary, dto);
        });
        assertTrue(ex.getMessage().contains("active application already exists"));
    }

    @Test
    @DisplayName("Case 12: Historical rejection records remain unchanged")
    void testHistoricalRejectionRecordsPreserved() {
        Application app1 = new Application();
        app1.setApplicationNumber("APP-HIST1-" + System.currentTimeMillis());
        app1.setBeneficiary(testBeneficiary);
        app1.setScheme(schemeA);
        app1.setRequestedAmount(new BigDecimal("20000"));
        app1.setCurrentStage("FIELD_VERIFICATION");
        app1.setStatus("PENDING");
        applicationRepository.save(app1);

        VerificationActionDto r1 = new VerificationActionDto();
        r1.setApplicationId(app1.getId());
        r1.setAction("REJECT");
        r1.setRemarks("First rejection history");
        verificationService.processVerification(foUser.getEmail(), "FIELD_OFFICER", r1);

        List<ApplicationRestriction> history = restrictionRepository.findByApplicationId(app1.getId());
        assertEquals(1, history.size());
        assertEquals("First rejection history", history.get(0).getRejectionReason());
        assertEquals("FIELD_OFFICER", history.get(0).getRejectedRole());
    }
}
