package com.example.digital_subsidy;

import com.example.digital_subsidy.dto.VerificationActionDto;
import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.*;
import com.example.digital_subsidy.service.DisbursementService;
import com.example.digital_subsidy.service.VerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
public class AdjustableSanctionAmountTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

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
    private RequiredDocumentRepository requiredDocumentRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private DisbursementRepository disbursementRepository;

    @Autowired
    private VerificationService verificationService;

    @Autowired
    private DisbursementService disbursementService;

    private User doUser;
    private User foUser;
    private User finUser;
    private User benUser;
    private Scheme testScheme;
    private Beneficiary testBeneficiary;
    private Application testApplication;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // 1. Create District Officer User
        doUser = new User();
        doUser.setEmail("do_amount_test@gov.in");
        doUser.setFullName("District Officer Amount Test");
        doUser.setMobileNumber("9800000001");
        doUser.setPassword("password");
        doUser.setEnabled(true);
        userRepository.save(doUser);

        Officer doOfficer = new Officer(doUser, doUser.getFullName(), doUser.getEmail(), doUser.getMobileNumber(), "DISTRICT_OFFICER", "Telangana", "Hyderabad");
        officerRepository.save(doOfficer);

        // 2. Create Field Officer User
        foUser = new User();
        foUser.setEmail("fo_amount_test@gov.in");
        foUser.setFullName("Field Officer Amount Test");
        foUser.setMobileNumber("9800000002");
        foUser.setPassword("password");
        foUser.setEnabled(true);
        userRepository.save(foUser);

        Officer foOfficer = new Officer(foUser, foUser.getFullName(), foUser.getEmail(), foUser.getMobileNumber(), "FIELD_OFFICER", "Telangana", "Hyderabad");
        officerRepository.save(foOfficer);

        // 3. Create Finance Approver User
        finUser = new User();
        finUser.setEmail("fin_amount_test@gov.in");
        finUser.setFullName("Finance Approver Test");
        finUser.setMobileNumber("9800000003");
        finUser.setPassword("password");
        finUser.setEnabled(true);
        userRepository.save(finUser);

        // 4. Create Beneficiary User
        benUser = new User();
        benUser.setEmail("ben_amount_test@gov.in");
        benUser.setFullName("Beneficiary Amount Test");
        benUser.setMobileNumber("9800000004");
        benUser.setPassword("password");
        benUser.setEnabled(true);
        userRepository.save(benUser);

        testBeneficiary = new Beneficiary();
        testBeneficiary.setUser(benUser);
        testBeneficiary.setIdentityNumber("ID-9988776655");
        testBeneficiary.setIdentityType("AADHAAR");
        testBeneficiary.setState("Telangana");
        testBeneficiary.setDistrict("Hyderabad");
        testBeneficiary.setAddress("123 Test Street, Hyderabad");
        testBeneficiary.setPincode("500001");
        testBeneficiary.setAge(30);
        testBeneficiary.setGender("Male");
        testBeneficiary.setCategory("General");
        beneficiaryRepository.save(testBeneficiary);

        // 5. Create Scheme with Max Amount ₹80,000 and Remaining Budget ₹5,00,000
        testScheme = new Scheme();
        testScheme.setName("Adjustable Subsidy Test Scheme " + System.currentTimeMillis());
        testScheme.setMinAmount(new BigDecimal("10000"));
        testScheme.setMaxAmount(new BigDecimal("80000"));
        testScheme.setTotalBudget(new BigDecimal("500000"));
        testScheme.setRemainingBudget(new BigDecimal("500000"));
        testScheme.setStatus("ACTIVE");
        schemeRepository.save(testScheme);

        // 6. Create Application with Requested Amount ₹1,00,000
        testApplication = new Application();
        testApplication.setApplicationNumber("APP-AMT-" + System.currentTimeMillis());
        testApplication.setBeneficiary(testBeneficiary);
        testApplication.setScheme(testScheme);
        testApplication.setRequestedAmount(new BigDecimal("100000"));
        testApplication.setEligibilityScore(85);
        testApplication.setCurrentStage("DISTRICT_REVIEW");
        testApplication.setStatus("PENDING");
        applicationRepository.save(testApplication);
    }

    @Test
    @DisplayName("Case 1: Requested ₹1,00,000 -> Sanctioned ₹75,000 with valid reason -> Valid")
    void testValidSanctionAmountReduction() {
        VerificationActionDto dto = new VerificationActionDto();
        dto.setApplicationId(testApplication.getId());
        dto.setAction("APPROVE");
        dto.setRemarks("District Officer approval notes");
        dto.setSanctionedAmount(new BigDecimal("75000"));
        dto.setAdjustmentReason("Amount limited according to scheme max limit and eligibility score");

        verificationService.processVerification(doUser.getEmail(), "DISTRICT_OFFICER", dto);

        Application updated = applicationRepository.findById(testApplication.getId()).orElseThrow();
        assertEquals(new BigDecimal("75000"), updated.getSanctionedAmount());
        assertEquals("Amount limited according to scheme max limit and eligibility score", updated.getAmountAdjustmentReason());
        assertEquals("FINANCE_APPROVAL", updated.getCurrentStage());
        assertEquals("PENDING", updated.getStatus());
        assertEquals(doUser.getEmail(), updated.getSanctionedBy());
        assertNotNull(updated.getSanctionedDate());
    }

    @Test
    @DisplayName("Case 2: Sanctioned amount greater than requested -> Rejected with IllegalArgumentException")
    void testSanctionedAmountExceedsRequestedAmount() {
        VerificationActionDto dto = new VerificationActionDto();
        dto.setApplicationId(testApplication.getId());
        dto.setAction("APPROVE");
        dto.setSanctionedAmount(new BigDecimal("120000")); // Requested is 100,000
        dto.setAdjustmentReason("Attempt to over-sanction");

        assertThrows(IllegalArgumentException.class, () -> {
            verificationService.processVerification(doUser.getEmail(), "DISTRICT_OFFICER", dto);
        });
    }

    @Test
    @DisplayName("Case 3: Sanctioned amount greater than scheme maximum -> Rejected with IllegalArgumentException")
    void testSanctionedAmountExceedsSchemeMax() {
        // Scheme max is 80,000. Requested is 100,000. DO tries to sanction 90,000
        VerificationActionDto dto = new VerificationActionDto();
        dto.setApplicationId(testApplication.getId());
        dto.setAction("APPROVE");
        dto.setSanctionedAmount(new BigDecimal("90000"));
        dto.setAdjustmentReason("Exceeds scheme limit");

        assertThrows(IllegalArgumentException.class, () -> {
            verificationService.processVerification(doUser.getEmail(), "DISTRICT_OFFICER", dto);
        });
    }

    @Test
    @DisplayName("Case 4: Reduced amount without reason -> Rejected with IllegalArgumentException")
    void testReducedAmountWithoutReason() {
        VerificationActionDto dto = new VerificationActionDto();
        dto.setApplicationId(testApplication.getId());
        dto.setAction("APPROVE");
        dto.setSanctionedAmount(new BigDecimal("75000"));
        dto.setAdjustmentReason("   "); // Blank reason

        assertThrows(IllegalArgumentException.class, () -> {
            verificationService.processVerification(doUser.getEmail(), "DISTRICT_OFFICER", dto);
        });
    }

    @Test
    @DisplayName("Case 5: Zero or negative amount -> Rejected with IllegalArgumentException")
    void testZeroOrNegativeSanctionedAmount() {
        VerificationActionDto dtoZero = new VerificationActionDto();
        dtoZero.setApplicationId(testApplication.getId());
        dtoZero.setAction("APPROVE");
        dtoZero.setSanctionedAmount(BigDecimal.ZERO);

        assertThrows(IllegalArgumentException.class, () -> {
            verificationService.processVerification(doUser.getEmail(), "DISTRICT_OFFICER", dtoZero);
        });

        VerificationActionDto dtoNegative = new VerificationActionDto();
        dtoNegative.setApplicationId(testApplication.getId());
        dtoNegative.setAction("APPROVE");
        dtoNegative.setSanctionedAmount(new BigDecimal("-5000"));

        assertThrows(IllegalArgumentException.class, () -> {
            verificationService.processVerification(doUser.getEmail(), "DISTRICT_OFFICER", dtoNegative);
        });
    }

    @Test
    @DisplayName("Case 6: Field Officer attempts to sanction -> Sanction amount ignored")
    void testFieldOfficerCannotModifySanctionedAmount() {
        // Set stage to FIELD_VERIFICATION
        testApplication.setCurrentStage("FIELD_VERIFICATION");
        applicationRepository.save(testApplication);

        VerificationActionDto dto = new VerificationActionDto();
        dto.setApplicationId(testApplication.getId());
        dto.setAction("APPROVE");
        dto.setSanctionedAmount(new BigDecimal("50000")); // FO attempt

        verificationService.processVerification(foUser.getEmail(), "FIELD_OFFICER", dto);

        Application updated = applicationRepository.findById(testApplication.getId()).orElseThrow();
        assertNull(updated.getSanctionedAmount()); // Not altered by FO
        assertEquals("DISTRICT_REVIEW", updated.getCurrentStage());
    }

    @Test
    @DisplayName("Case 7: District Officer attempts to modify after Finance approval -> Rejected")
    void testDistrictOfficerCannotModifyAfterFinanceApproval() {
        testApplication.setCurrentStage("DISBURSEMENT");
        testApplication.setStatus("APPROVED");
        testApplication.setFinanceApprovalStatus("APPROVED");
        applicationRepository.save(testApplication);

        VerificationActionDto dto = new VerificationActionDto();
        dto.setApplicationId(testApplication.getId());
        dto.setAction("APPROVE");
        dto.setSanctionedAmount(new BigDecimal("70000"));
        dto.setAdjustmentReason("Late change attempt");

        assertThrows(IllegalStateException.class, () -> {
            verificationService.processVerification(doUser.getEmail(), "DISTRICT_OFFICER", dto);
        });
    }

    @Test
    @DisplayName("Case 8: Finance Approver reviews and authorizes correct sanctioned amount")
    void testFinanceApproverSanctionsCorrectAmount() {
        // DO sanctions 75,000 first
        VerificationActionDto doDto = new VerificationActionDto();
        doDto.setApplicationId(testApplication.getId());
        doDto.setAction("APPROVE");
        doDto.setSanctionedAmount(new BigDecimal("75000"));
        doDto.setAdjustmentReason("Valid reduction to 75k");
        verificationService.processVerification(doUser.getEmail(), "DISTRICT_OFFICER", doDto);

        // Finance Approver approves
        VerificationActionDto finDto = new VerificationActionDto();
        finDto.setApplicationId(testApplication.getId());
        finDto.setAction("APPROVE");
        finDto.setRemarks("Treasury financial authorization complete");
        verificationService.processVerification(finUser.getEmail(), "FINANCE_APPROVER", finDto);

        Application updated = applicationRepository.findById(testApplication.getId()).orElseThrow();
        assertEquals("APPROVED", updated.getStatus());
        assertEquals("DISBURSEMENT", updated.getCurrentStage());
        assertEquals("APPROVED", updated.getFinanceApprovalStatus());
        assertEquals(finUser.getEmail(), updated.getFinanceApprovedBy());

        // Check initialized Disbursement record totalSanctionedAmount
        Disbursement disbursement = disbursementRepository.findByApplication(testApplication).orElseThrow();
        assertEquals(new BigDecimal("75000"), disbursement.getTotalSanctionedAmount());
    }

    @Test
    @DisplayName("Case 9: Audit log contains original amount, adjusted amount, reason and officer")
    void testAuditLogContainsAdjustedAmountDetails() {
        VerificationActionDto dto = new VerificationActionDto();
        dto.setApplicationId(testApplication.getId());
        dto.setAction("APPROVE");
        dto.setSanctionedAmount(new BigDecimal("75000"));
        dto.setAdjustmentReason("Audit test reduction");

        verificationService.processVerification(doUser.getEmail(), "DISTRICT_OFFICER", dto);

        List<AuditLog> logs = auditLogRepository.findAll();
        boolean foundAudit = logs.stream().anyMatch(log ->
                log.getRemarks() != null &&
                log.getRemarks().contains("Requested: ₹100000") &&
                log.getRemarks().contains("Sanctioned: ₹75000") &&
                log.getRemarks().contains("Audit test reduction"));

        assertTrue(foundAudit, "Audit log must contain original requested amount, sanctioned amount, and adjustment reason.");
    }

    @Test
    @WithMockUser(username = "do_amount_test@gov.in", roles = {"DISTRICT_OFFICER"})
    @DisplayName("Case 10: Unauthorized district application access -> Blocked with redirect/error")
    void testUnauthorizedDistrictAccessBlocked() throws Exception {
        // Create beneficiary in a different state (e.g. Maharashtra)
        User otherBenUser = new User();
        otherBenUser.setEmail("other_state_ben@gov.in");
        otherBenUser.setFullName("Other State Beneficiary");
        otherBenUser.setMobileNumber("9800000099");
        otherBenUser.setPassword("password");
        userRepository.save(otherBenUser);

        Beneficiary otherBen = new Beneficiary();
        otherBen.setUser(otherBenUser);
        otherBen.setState("Maharashtra"); // Different from DO's state Telangana
        otherBen.setDistrict("Mumbai");
        otherBen.setAddress("456 Mumbai Road");
        otherBen.setPincode("400001");
        otherBen.setAge(28);
        otherBen.setGender("Female");
        otherBen.setCategory("General");
        otherBen.setIdentityNumber("ID-1122334455");
        otherBen.setIdentityType("AADHAAR");
        beneficiaryRepository.save(otherBen);

        Application otherApp = new Application();
        otherApp.setApplicationNumber("APP-MH-" + System.currentTimeMillis());
        otherApp.setBeneficiary(otherBen);
        otherApp.setScheme(testScheme);
        otherApp.setRequestedAmount(new BigDecimal("50000"));
        otherApp.setCurrentStage("DISTRICT_REVIEW");
        otherApp.setStatus("PENDING");
        applicationRepository.save(otherApp);

        mockMvc.perform(get("/district-officer/review/" + otherApp.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/district-officer/dashboard"))
                .andExpect(flash().attributeExists("errorMessage"));
    }
}
