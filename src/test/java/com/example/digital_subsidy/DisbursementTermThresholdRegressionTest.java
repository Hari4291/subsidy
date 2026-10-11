package com.example.digital_subsidy;

import com.example.digital_subsidy.dto.StageReleaseDto;
import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.*;
import com.example.digital_subsidy.service.DisbursementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class DisbursementTermThresholdRegressionTest {

    @Autowired
    private DisbursementService disbursementService;

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

    private User testUser;
    private Beneficiary testBeneficiary;
    private Scheme testScheme;

    @BeforeEach
    void setUp() {
        long timestamp = System.currentTimeMillis();

        testUser = new User();
        testUser.setEmail("term_test_" + timestamp + "@gov.in");
        testUser.setPassword("Password@123");
        testUser.setFullName("Term Threshold Test User");
        testUser.setMobileNumber("98765" + (timestamp % 100000));
        testUser = userRepository.save(testUser);

        testBeneficiary = new Beneficiary();
        testBeneficiary.setUser(testUser);
        testBeneficiary.setIdentityType("AADHAAR");
        testBeneficiary.setIdentityNumber("77665544" + (timestamp % 10000));
        testBeneficiary.setAddress("456 Treasury Road");
        testBeneficiary.setAge(28);
        testBeneficiary.setGender("FEMALE");
        testBeneficiary.setPincode("500002");
        testBeneficiary.setCategory("GENERAL");
        testBeneficiary.setState("TELANGANA");
        testBeneficiary.setDistrict("HYDERABAD");
        testBeneficiary = beneficiaryRepository.save(testBeneficiary);

        testScheme = new Scheme();
        testScheme.setName("Term Threshold Scheme " + timestamp);
        testScheme.setCategory("GENERAL");
        testScheme.setTotalBudget(new BigDecimal("10000000.00"));
        testScheme.setRemainingBudget(new BigDecimal("10000000.00"));
        testScheme.setMinAmount(new BigDecimal("1000.00"));
        testScheme.setMaxAmount(new BigDecimal("200000.00"));
        testScheme.setStatus("ACTIVE");
        testScheme = schemeRepository.save(testScheme);
    }

    private Application createApplication(String suffix, BigDecimal requestedAmt, BigDecimal sanctionedAmt) {
        Application app = new Application();
        app.setApplicationNumber("APP-TERM-" + suffix + "-" + System.currentTimeMillis());
        app.setBeneficiary(testBeneficiary);
        app.setScheme(testScheme);
        app.setRequestedAmount(requestedAmt);
        app.setSanctionedAmount(sanctionedAmt);
        app.setStatus("APPROVED");
        app.setCurrentStage("DISBURSEMENT");
        return applicationRepository.save(app);
    }

    @Test
    @DisplayName("1. ₹10,000 -> 1 term")
    void testTenThousandOneTerm() {
        Application app = createApplication("10K", new BigDecimal("10000.00"), new BigDecimal("10000.00"));
        Disbursement d = disbursementService.getDisbursementByApplicationId(app.getId());
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(d);
        assertEquals(1, milestones.size());
        assertEquals(0, new BigDecimal("10000.00").compareTo(milestones.get(0).getMilestoneAmount()));
        assertEquals(100, milestones.get(0).getPercentage());
    }

    @Test
    @DisplayName("2. ₹25,000 -> 1 term")
    void testTwentyFiveThousandOneTerm() {
        Application app = createApplication("25K", new BigDecimal("25000.00"), new BigDecimal("25000.00"));
        Disbursement d = disbursementService.getDisbursementByApplicationId(app.getId());
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(d);
        assertEquals(1, milestones.size());
        assertEquals(0, new BigDecimal("25000.00").compareTo(milestones.get(0).getMilestoneAmount()));
    }

    @Test
    @DisplayName("3. ₹39,999 -> 1 term")
    void testThirtyNineThousandNineHundredNinetyNineOneTerm() {
        Application app = createApplication("39999", new BigDecimal("39999.00"), new BigDecimal("39999.00"));
        Disbursement d = disbursementService.getDisbursementByApplicationId(app.getId());
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(d);
        assertEquals(1, milestones.size());
        assertEquals(0, new BigDecimal("39999.00").compareTo(milestones.get(0).getMilestoneAmount()));
    }

    @Test
    @DisplayName("4. ₹40,000 -> 1 term")
    void testFortyThousandOneTerm() {
        Application app = createApplication("40K", new BigDecimal("40000.00"), new BigDecimal("40000.00"));
        Disbursement d = disbursementService.getDisbursementByApplicationId(app.getId());
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(d);
        assertEquals(1, milestones.size());
        assertEquals(0, new BigDecimal("40000.00").compareTo(milestones.get(0).getMilestoneAmount()));
        assertEquals(100, milestones.get(0).getPercentage());
    }

    @Test
    @DisplayName("5. ₹40,001 -> 3 terms")
    void testFortyThousandOneThreeTerms() {
        Application app = createApplication("40001", new BigDecimal("40001.00"), new BigDecimal("40001.00"));
        Disbursement d = disbursementService.getDisbursementByApplicationId(app.getId());
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(d);
        assertEquals(3, milestones.size());
    }

    @Test
    @DisplayName("6. ₹50,000 -> 3 terms")
    void testFiftyThousandThreeTerms() {
        Application app = createApplication("50K", new BigDecimal("50000.00"), new BigDecimal("50000.00"));
        Disbursement d = disbursementService.getDisbursementByApplicationId(app.getId());
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(d);
        assertEquals(3, milestones.size());
    }

    @Test
    @DisplayName("7. ₹60,000 -> 3 terms")
    void testSixtyThousandThreeTerms() {
        Application app = createApplication("60K", new BigDecimal("60000.00"), new BigDecimal("60000.00"));
        Disbursement d = disbursementService.getDisbursementByApplicationId(app.getId());
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(d);
        assertEquals(3, milestones.size());
    }

    @Test
    @DisplayName("8. Requested ₹50,000 / Sanctioned ₹35,000 -> 1 term")
    void testRequested50kSanctioned35kOneTerm() {
        Application app = createApplication("REQ50SANC35", new BigDecimal("50000.00"), new BigDecimal("35000.00"));
        Disbursement d = disbursementService.getDisbursementByApplicationId(app.getId());
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(d);
        assertEquals(1, milestones.size());
        assertEquals(0, new BigDecimal("35000.00").compareTo(milestones.get(0).getMilestoneAmount()));
    }

    @Test
    @DisplayName("9. Requested ₹35,000 / Sanctioned ₹45,000 -> 3 terms")
    void testRequested35kSanctioned45kThreeTerms() {
        Application app = createApplication("REQ35SANC45", new BigDecimal("35000.00"), new BigDecimal("45000.00"));
        Disbursement d = disbursementService.getDisbursementByApplicationId(app.getId());
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(d);
        assertEquals(3, milestones.size());
    }

    @Test
    @DisplayName("10. Reopening schedule does not create duplicate terms")
    void testReopeningScheduleNoDuplicates() {
        Application app = createApplication("REOPEN", new BigDecimal("30000.00"), new BigDecimal("30000.00"));
        Disbursement d1 = disbursementService.getDisbursementByApplicationId(app.getId());
        List<Milestone> m1 = milestoneRepository.findByDisbursementOrderByIdAsc(d1);
        assertEquals(1, m1.size());

        Disbursement d2 = disbursementService.getDisbursementByApplicationId(app.getId());
        List<Milestone> m2 = milestoneRepository.findByDisbursementOrderByIdAsc(d2);
        assertEquals(1, m2.size());
        assertEquals(m1.get(0).getId(), m2.get(0).getId());
    }

    @Test
    @DisplayName("11. <= ₹40,000 plan contains no stage 2/stage 3")
    void testSingleTermPlanContainsNoStage2Or3() {
        Application app = createApplication("NOSTAGE23", new BigDecimal("40000.00"), new BigDecimal("40000.00"));
        Disbursement d = disbursementService.getDisbursementByApplicationId(app.getId());
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(d);
        assertEquals(1, milestones.size());

        boolean hasStage2Or3 = milestones.stream().anyMatch(m -> 
            (m.getStageNumber() != null && (m.getStageNumber() == 2 || m.getStageNumber() == 3)) ||
            (m.getMilestoneName() != null && (m.getMilestoneName().contains("Phase 2") || m.getMilestoneName().contains("Phase 3")))
        );
        assertFalse(hasStage2Or3, "Single term plan MUST NOT contain Stage 2 or Stage 3");
    }

    @Test
    @DisplayName("12 & 13. Single-term release releases full amount, remaining=0, pending=0")
    void testSingleTermReleaseFullAmountAndPendingZero() {
        Application app = createApplication("RELEASE_FULL", new BigDecimal("40000.00"), new BigDecimal("40000.00"));
        Disbursement d = disbursementService.getDisbursementByApplicationId(app.getId());
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(d);
        assertEquals(1, milestones.size());

        Milestone m = milestones.get(0);
        m.setComplianceVerificationStatus("VERIFIED");
        milestoneRepository.save(m);

        StageReleaseDto dto = new StageReleaseDto();
        dto.setMilestoneId(m.getId());
        disbursementService.releaseMilestone(dto, testUser.getEmail());

        Disbursement updatedD = disbursementService.getDisbursementByApplicationId(app.getId());
        assertEquals(0, new BigDecimal("40000.00").compareTo(updatedD.getDisbursedAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(updatedD.getRemainingAmount()));

        List<Milestone> updatedMilestones = milestoneRepository.findByDisbursementOrderByIdAsc(updatedD);
        long pendingCount = updatedMilestones.stream()
                .filter(ms -> !"COMPLETED".equalsIgnoreCase(ms.getCompletionStatus()) && !"RELEASED".equalsIgnoreCase(ms.getCompletionStatus()))
                .count();
        assertEquals(0, pendingCount, "Pending terms count MUST be 0 after full release");
    }

    @Test
    @DisplayName("14. Duplicate full release is blocked")
    void testDuplicateFullReleaseBlocked() {
        Application app = createApplication("DUP_RELEASE", new BigDecimal("25000.00"), new BigDecimal("25000.00"));
        Disbursement d = disbursementService.getDisbursementByApplicationId(app.getId());
        Milestone m = milestoneRepository.findByDisbursementOrderByIdAsc(d).get(0);
        m.setComplianceVerificationStatus("VERIFIED");
        milestoneRepository.save(m);

        StageReleaseDto dto = new StageReleaseDto();
        dto.setMilestoneId(m.getId());
        disbursementService.releaseMilestone(dto, testUser.getEmail());

        // Attempting second release on the same milestone MUST fail
        assertThrows(IllegalStateException.class, () -> {
            disbursementService.releaseMilestone(dto, testUser.getEmail());
        });
    }

    @Test
    @DisplayName("15. Existing 3-term plans for >₹40,000 remain unchanged")
    void testExistingThreeTermPlanForAbove40kUnchanged() {
        Application app = createApplication("ABOVE40K_UNCHANGED", new BigDecimal("70000.00"), new BigDecimal("70000.00"));
        Disbursement d = disbursementService.getDisbursementByApplicationId(app.getId());
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(d);
        assertEquals(3, milestones.size());
        assertEquals(1, milestones.get(0).getStageNumber());
        assertEquals(2, milestones.get(1).getStageNumber());
        assertEquals(3, milestones.get(2).getStageNumber());
    }
}
