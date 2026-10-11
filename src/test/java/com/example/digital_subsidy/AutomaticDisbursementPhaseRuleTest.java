package com.example.digital_subsidy;

import com.example.digital_subsidy.dto.PartiallyReleasedApplicationDto;
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
public class AutomaticDisbursementPhaseRuleTest {

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
        testUser.setEmail("phase_test_" + timestamp + "@gov.in");
        testUser.setPassword("Password@123");
        testUser.setFullName("Phase Test User");
        testUser.setMobileNumber("98765" + (timestamp % 100000));
        testUser = userRepository.save(testUser);

        testBeneficiary = new Beneficiary();
        testBeneficiary.setUser(testUser);
        testBeneficiary.setIdentityType("AADHAAR");
        testBeneficiary.setIdentityNumber("88776655" + (timestamp % 10000));
        testBeneficiary.setAddress("789 Treasury Avenue");
        testBeneficiary.setAge(30);
        testBeneficiary.setGender("MALE");
        testBeneficiary.setPincode("500001");
        testBeneficiary.setCategory("OBC");
        testBeneficiary.setState("TELANGANA");
        testBeneficiary.setDistrict("HYDERABAD");
        testBeneficiary = beneficiaryRepository.save(testBeneficiary);

        testScheme = new Scheme();
        testScheme.setName("Auto Phase Rule Scheme " + timestamp);
        testScheme.setCategory("GENERAL");
        testScheme.setTotalBudget(new BigDecimal("5000000.00"));
        testScheme.setRemainingBudget(new BigDecimal("5000000.00"));
        testScheme.setMinAmount(new BigDecimal("1000.00"));
        testScheme.setMaxAmount(new BigDecimal("200000.00"));
        testScheme.setStatus("ACTIVE");
        testScheme = schemeRepository.save(testScheme);
    }

    private Application createApplicationWithAmounts(String suffix, BigDecimal requestedAmt, BigDecimal sanctionedAmt) {
        Application app = new Application();
        app.setApplicationNumber("APP-AUTO-" + suffix + "-" + System.currentTimeMillis());
        app.setBeneficiary(testBeneficiary);
        app.setScheme(testScheme);
        app.setRequestedAmount(requestedAmt);
        app.setSanctionedAmount(sanctionedAmt);
        app.setStatus("APPROVED");
        app.setCurrentStage("DISBURSEMENT");
        return applicationRepository.save(app);
    }

    @Test
    @DisplayName("Test 1: Sanctioned Amount = ₹40,000 -> 1 Phase, 100%, ₹40,000")
    void testSanctionedExactlyFortyThousandSinglePhase() {
        Application app = createApplicationWithAmounts("40K", new BigDecimal("40000.00"), new BigDecimal("40000.00"));
        Disbursement disbursement = disbursementService.initializeDisbursement(app, testUser);

        assertNotNull(disbursement);
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        assertEquals(1, milestones.size(), "Sanctioned amount <= ₹40,000 MUST generate exactly 1 phase");
        assertEquals(100, milestones.get(0).getPercentage());
        assertEquals(0, new BigDecimal("40000.00").compareTo(milestones.get(0).getMilestoneAmount()));
    }

    @Test
    @DisplayName("Test 2: Sanctioned Amount = ₹39,999 -> 1 Phase")
    void testSanctionedBelowFortyThousandSinglePhase() {
        Application app = createApplicationWithAmounts("39999", new BigDecimal("50000.00"), new BigDecimal("39999.00"));
        Disbursement disbursement = disbursementService.initializeDisbursement(app, testUser);

        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        assertEquals(1, milestones.size(), "Sanctioned amount <= ₹40,000 MUST generate 1 phase");
        assertEquals(0, new BigDecimal("39999.00").compareTo(milestones.get(0).getMilestoneAmount()));
    }

    @Test
    @DisplayName("Test 3: Sanctioned Amount = ₹40,001 -> 3 Phases")
    void testSanctionedAboveFortyThousandThreePhases() {
        Application app = createApplicationWithAmounts("40001", new BigDecimal("35000.00"), new BigDecimal("40001.00"));
        Disbursement disbursement = disbursementService.initializeDisbursement(app, testUser);

        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        assertEquals(3, milestones.size(), "Sanctioned amount > ₹40,000 MUST generate 3 phases");

        BigDecimal totalSum = milestones.stream().map(Milestone::getMilestoneAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("40001.00").compareTo(totalSum), "Sum of milestone amounts MUST equal sanctioned amount");
    }

    @Test
    @DisplayName("Test 4: Sanctioned Amount = ₹60,000 -> 3 Phases, Total = ₹60,000")
    void testSanctionedSixtyThousandThreePhases() {
        Application app = createApplicationWithAmounts("60K", new BigDecimal("60000.00"), new BigDecimal("60000.00"));
        Disbursement disbursement = disbursementService.initializeDisbursement(app, testUser);

        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        assertEquals(3, milestones.size());

        BigDecimal sum = milestones.stream().map(Milestone::getMilestoneAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("60000.00").compareTo(sum));
    }

    @Test
    @DisplayName("Test 5: Sanctioned Amount = ₹75,000 -> 3 Phases, Total = ₹75,000")
    void testSanctionedSeventyFiveThousandThreePhases() {
        Application app = createApplicationWithAmounts("75K", new BigDecimal("75000.00"), new BigDecimal("75000.00"));
        Disbursement disbursement = disbursementService.initializeDisbursement(app, testUser);

        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        assertEquals(3, milestones.size());

        BigDecimal sum = milestones.stream().map(Milestone::getMilestoneAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("75000.00").compareTo(sum));
    }

    @Test
    @DisplayName("Test 6: Sanctioned Amount = ₹1,00,000 -> 3 Phases, Total = ₹1,00,000")
    void testSanctionedOneLakhThreePhases() {
        Application app = createApplicationWithAmounts("100K", new BigDecimal("100000.00"), new BigDecimal("100000.00"));
        Disbursement disbursement = disbursementService.initializeDisbursement(app, testUser);

        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        assertEquals(3, milestones.size());

        BigDecimal sum = milestones.stream().map(Milestone::getMilestoneAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("100000.00").compareTo(sum));
    }

    @Test
    @DisplayName("Test 7: Rounding edge cases sum exactly equals sanctioned amount")
    void testRoundingEdgeCasesExactSum() {
        BigDecimal[] testAmounts = {
            new BigDecimal("40001.33"),
            new BigDecimal("50000.77"),
            new BigDecimal("99999.99"),
            new BigDecimal("123456.78")
        };

        for (int i = 0; i < testAmounts.length; i++) {
            BigDecimal sanctioned = testAmounts[i];
            Application app = createApplicationWithAmounts("ROUND-" + i, sanctioned, sanctioned);
            Disbursement disbursement = disbursementService.initializeDisbursement(app, testUser);

            List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
            assertEquals(3, milestones.size());

            BigDecimal sum = milestones.stream().map(Milestone::getMilestoneAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, sanctioned.compareTo(sum), "Sum of phases MUST equal sanctioned amount for " + sanctioned);
        }
    }

    @Test
    @DisplayName("Test 8: Prevent duplicate plan/milestones creation")
    void testPreventDuplicatePlanCreation() {
        Application app = createApplicationWithAmounts("DUP", new BigDecimal("60000.00"), new BigDecimal("60000.00"));
        Disbursement d1 = disbursementService.initializeDisbursement(app, testUser);
        List<Milestone> m1 = milestoneRepository.findByDisbursementOrderByIdAsc(d1);

        Disbursement d2 = disbursementService.initializeDisbursement(app, testUser);
        List<Milestone> m2 = milestoneRepository.findByDisbursementOrderByIdAsc(d2);

        assertEquals(d1.getId(), d2.getId());
        assertEquals(m1.size(), m2.size(), "Initializing disbursement twice MUST NOT duplicate milestones");
    }

    @Test
    @DisplayName("Test 9: 3-Phase Plan release transition from Active Queue to Partial Queue")
    void testThreePhasePlanActiveToPartialQueueTransition() {
        Application app = createApplicationWithAmounts("QUEUE3P", new BigDecimal("60000.00"), new BigDecimal("60000.00"));
        Disbursement disbursement = disbursementService.initializeDisbursement(app, testUser);
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);

        // Before release -> Active Queue = YES, Partial Queue = NO
        assertTrue(disbursementService.getUnreleasedActiveDisbursementApplications().stream().anyMatch(a -> a.getId().equals(app.getId())));
        assertFalse(disbursementService.getPartiallyReleasedApplications().stream().anyMatch(p -> p.getApplicationId().equals(app.getId())));

        // Release Phase 1
        milestones.get(0).setComplianceVerificationStatus("VERIFIED");
        milestoneRepository.save(milestones.get(0));
        StageReleaseDto dto1 = new StageReleaseDto();
        dto1.setMilestoneId(milestones.get(0).getId());
        disbursementService.releaseMilestone(dto1, testUser.getEmail());

        // After Phase 1 release -> Active Queue = NO, Partial Queue = YES
        assertFalse(disbursementService.getUnreleasedActiveDisbursementApplications().stream().anyMatch(a -> a.getId().equals(app.getId())));
        assertTrue(disbursementService.getPartiallyReleasedApplications().stream().anyMatch(p -> p.getApplicationId().equals(app.getId())));
    }

    @Test
    @DisplayName("Test 10: 3-Phase Plan final release leaves Partial Queue")
    void testThreePhasePlanFinalReleaseLeavesQueues() {
        Application app = createApplicationWithAmounts("QUEUE3PFINAL", new BigDecimal("60000.00"), new BigDecimal("60000.00"));
        Disbursement disbursement = disbursementService.initializeDisbursement(app, testUser);
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);

        for (Milestone m : milestones) {
            m.setComplianceVerificationStatus("VERIFIED");
            milestoneRepository.save(m);
            StageReleaseDto dto = new StageReleaseDto();
            dto.setMilestoneId(m.getId());
            disbursementService.releaseMilestone(dto, testUser.getEmail());
        }

        assertFalse(disbursementService.getUnreleasedActiveDisbursementApplications().stream().anyMatch(a -> a.getId().equals(app.getId())));
        assertFalse(disbursementService.getPartiallyReleasedApplications().stream().anyMatch(p -> p.getApplicationId().equals(app.getId())));
    }

    @Test
    @DisplayName("Test 11: Single-phase plan after release immediately becomes COMPLETED and leaves queues")
    void testSinglePhasePlanCompletedAfterRelease() {
        Application app = createApplicationWithAmounts("1PCOMPLETE", new BigDecimal("40000.00"), new BigDecimal("40000.00"));
        Disbursement disbursement = disbursementService.initializeDisbursement(app, testUser);
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        assertEquals(1, milestones.size());

        milestones.get(0).setComplianceVerificationStatus("VERIFIED");
        milestoneRepository.save(milestones.get(0));
        StageReleaseDto dto = new StageReleaseDto();
        dto.setMilestoneId(milestones.get(0).getId());
        disbursementService.releaseMilestone(dto, testUser.getEmail());

        // Status checks
        Application updatedApp = applicationRepository.findById(app.getId()).orElseThrow();
        assertEquals("COMPLETED", updatedApp.getCurrentStage());

        assertFalse(disbursementService.getUnreleasedActiveDisbursementApplications().stream().anyMatch(a -> a.getId().equals(app.getId())));
        assertFalse(disbursementService.getPartiallyReleasedApplications().stream().anyMatch(p -> p.getApplicationId().equals(app.getId())));
    }

    @Test
    @DisplayName("Test 12: Requested ₹50,000 + Sanctioned ₹35,000 -> 1 Phase (Sanctioned amount is authoritative)")
    void testRequested50kSanctioned35kOnePhase() {
        Application app = createApplicationWithAmounts("50K-35K", new BigDecimal("50000.00"), new BigDecimal("35000.00"));
        Disbursement disbursement = disbursementService.initializeDisbursement(app, testUser);

        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        assertEquals(1, milestones.size(), "Requested ₹50,000 + Sanctioned ₹35,000 MUST generate exactly 1 phase based on sanctioned amount");
        assertEquals(0, new BigDecimal("35000.00").compareTo(milestones.get(0).getMilestoneAmount()));
    }

    @Test
    @DisplayName("Test 13: Requested ₹35,000 + Sanctioned ₹45,000 -> 3 Phases (Sanctioned amount is authoritative)")
    void testRequested35kSanctioned45kThreePhases() {
        Application app = createApplicationWithAmounts("35K-45K", new BigDecimal("35000.00"), new BigDecimal("45000.00"));
        Disbursement disbursement = disbursementService.initializeDisbursement(app, testUser);

        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        assertEquals(3, milestones.size(), "Requested ₹35,000 + Sanctioned ₹45,000 MUST generate 3 phases based on sanctioned amount");

        BigDecimal totalSum = milestones.stream().map(Milestone::getMilestoneAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("45000.00").compareTo(totalSum));
    }

    @Test
    @DisplayName("Test 14: Requested ₹50,000 + Sanctioned null -> Plan creation rejected, no fallback to requested amount")
    void testSanctionedNullRejectedWithoutFallback() {
        Application app = createApplicationWithAmounts("NULL-SANC", new BigDecimal("50000.00"), null);

        // 1. Verify initializeDisbursement rejects with IllegalArgumentException
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () -> {
            disbursementService.initializeDisbursement(app, testUser);
        });
        assertTrue(ex1.getMessage().contains("sanctioned amount"), "Exception message should mention sanctioned amount requirement");

        // 2. Verify custom plan creation also rejects with IllegalArgumentException
        com.example.digital_subsidy.dto.DisbursementPlanDto customDto = new com.example.digital_subsidy.dto.DisbursementPlanDto();
        customDto.setApplicationId(app.getId());
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () -> {
            disbursementService.createOrUpdateCustomPlan(customDto, testUser);
        });
        assertTrue(ex2.getMessage().contains("sanctioned amount"));

        // 3. Verify no milestones or disbursement plans exist for the application
        assertTrue(disbursementService.getDisbursementByApplicationId(app.getId()) == null ||
                   milestoneRepository.findByDisbursementOrderByIdAsc(disbursementService.getDisbursementByApplicationId(app.getId())).isEmpty());
    }
}
