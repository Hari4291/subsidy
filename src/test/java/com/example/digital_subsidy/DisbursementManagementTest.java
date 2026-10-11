package com.example.digital_subsidy;

import com.example.digital_subsidy.dto.DisbursementPlanDto;
import com.example.digital_subsidy.dto.StageConfigDto;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class DisbursementManagementTest {

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
    private DisbursementRepository disbursementRepository;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private DisbursementReleaseRepository releaseRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private User testUser;
    private Beneficiary testBeneficiary;
    private Scheme testScheme;
    private Application testApplication;

    @BeforeEach
    void setUp() {
        long timestamp = System.currentTimeMillis();

        testUser = new User();
        testUser.setEmail("ben_disb_" + timestamp + "@gov.in");
        testUser.setPassword("Password@123");
        testUser.setFullName("Test Disbursement Beneficiary");
        testUser.setMobileNumber("987654" + (timestamp % 10000));
        testUser = userRepository.save(testUser);

        testBeneficiary = new Beneficiary();
        testBeneficiary.setUser(testUser);
        testBeneficiary.setIdentityType("AADHAAR");
        testBeneficiary.setIdentityNumber("11223344" + (timestamp % 10000));
        testBeneficiary.setAddress("123 Test Street, Financial District");
        testBeneficiary.setAge(35);
        testBeneficiary.setGender("MALE");
        testBeneficiary.setPincode("400001");
        testBeneficiary.setCategory("GENERAL");
        testBeneficiary.setState("MAHARASHTRA");
        testBeneficiary.setDistrict("MUMBAI");
        testBeneficiary = beneficiaryRepository.save(testBeneficiary);

        testScheme = new Scheme();
        testScheme.setName("Disbursement Test Scheme " + timestamp);
        testScheme.setCategory("AGRICULTURE");
        testScheme.setTotalBudget(new BigDecimal("1000000.00"));
        testScheme.setRemainingBudget(new BigDecimal("1000000.00"));
        testScheme.setMinAmount(new BigDecimal("1000.00"));
        testScheme.setMaxAmount(new BigDecimal("100000.00"));
        testScheme.setStatus("ACTIVE");
        testScheme = schemeRepository.save(testScheme);

        testApplication = new Application();
        testApplication.setApplicationNumber("APP-DISB-" + timestamp);
        testApplication.setBeneficiary(testBeneficiary);
        testApplication.setScheme(testScheme);
        testApplication.setRequestedAmount(new BigDecimal("100000.00"));
        testApplication.setSanctionedAmount(new BigDecimal("100000.00"));
        testApplication.setStatus("APPROVED");
        testApplication.setCurrentStage("DISBURSEMENT");
        testApplication = applicationRepository.save(testApplication);
    }

    @Test
    @DisplayName("Test 1: Initialize Default Disbursement Plan")
    void testInitializeDisbursement() {
        Disbursement disbursement = disbursementService.initializeDisbursement(testApplication, testUser);

        assertNotNull(disbursement);
        assertNotNull(disbursement.getId());
        assertEquals("IN_PROGRESS", disbursement.getStatus());
        assertEquals(0, new BigDecimal("100000.00").compareTo(disbursement.getTotalSanctionedAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(disbursement.getDisbursedAmount()));

        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        assertEquals(3, milestones.size());
        assertEquals(30, milestones.get(0).getPercentage());
        assertEquals(40, milestones.get(1).getPercentage());
        assertEquals(30, milestones.get(2).getPercentage());
    }

    @Test
    @DisplayName("Test 2: Configure Custom Multi-Stage Plan with 100% Validation")
    void testConfigureCustomPlanSuccess() {
        DisbursementPlanDto planDto = new DisbursementPlanDto();
        planDto.setApplicationId(testApplication.getId());
        planDto.setCalculationType("PERCENTAGE");

        List<StageConfigDto> stages = new ArrayList<>();
        stages.add(new StageConfigDto(1, "Phase 1 - Advance", "Initial 20%", 20, null, LocalDate.now().plusDays(10), "Doc Verification"));
        stages.add(new StageConfigDto(2, "Phase 2 - Intermediate", "Mid-term 50%", 50, null, LocalDate.now().plusDays(30), "Inspection Report"));
        stages.add(new StageConfigDto(3, "Phase 3 - Final Completion", "Final 30%", 30, null, LocalDate.now().plusDays(60), "Completion Certificate"));
        planDto.setStages(stages);

        Disbursement plan = disbursementService.createOrUpdateCustomPlan(planDto, testUser);

        assertNotNull(plan);
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(plan);
        assertEquals(3, milestones.size());
        assertEquals(0, new BigDecimal("20000.00").compareTo(milestones.get(0).getMilestoneAmount()));
        assertEquals(0, new BigDecimal("50000.00").compareTo(milestones.get(1).getMilestoneAmount()));
        assertEquals(0, new BigDecimal("30000.00").compareTo(milestones.get(2).getMilestoneAmount()));
    }

    @Test
    @DisplayName("Test 3: Reject Custom Plan when Stage Percentages do not sum to 100%")
    void testConfigureCustomPlanInvalidPercentage() {
        DisbursementPlanDto planDto = new DisbursementPlanDto();
        planDto.setApplicationId(testApplication.getId());
        planDto.setCalculationType("PERCENTAGE");

        List<StageConfigDto> stages = new ArrayList<>();
        stages.add(new StageConfigDto(1, "Phase 1", "30%", 30, null, LocalDate.now().plusDays(10), "Proof 1"));
        stages.add(new StageConfigDto(2, "Phase 2", "30%", 30, null, LocalDate.now().plusDays(30), "Proof 2"));
        planDto.setStages(stages); // Total = 60%, invalid!

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            disbursementService.createOrUpdateCustomPlan(planDto, testUser);
        });

        assertTrue(ex.getMessage().contains("Total stage percentage must equal 100%"));
    }

    @Test
    @DisplayName("Test 4: Sequential Stage Release and Payment Record Generation")
    void testSequentialStageRelease() {
        Disbursement disbursement = disbursementService.initializeDisbursement(testApplication, testUser);
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);

        // Stage 1 Release
        milestones.get(0).setComplianceVerificationStatus("VERIFIED");
        milestoneRepository.save(milestones.get(0));
        StageReleaseDto dto1 = new StageReleaseDto();
        dto1.setMilestoneId(milestones.get(0).getId());
        dto1.setApplicationId(testApplication.getId());
        dto1.setReleaseAmount(milestones.get(0).getMilestoneAmount());
        dto1.setTransactionReference("UTR-90001");
        dto1.setPaymentMethod("DBT_BANK_TRANSFER");
        dto1.setRemarks("Stage 1 Released");

        Milestone released1 = disbursementService.releaseMilestone(dto1, "finance_officer@gov.in");

        assertEquals("COMPLETED", released1.getCompletionStatus());
        assertEquals(0, new BigDecimal("30000.00").compareTo(released1.getReleasedAmount()));

        // Verify Payment Release Record
        List<DisbursementRelease> releases = releaseRepository.findByApplicationIdOrderByReleaseDateDesc(testApplication.getId());
        assertFalse(releases.isEmpty());
        assertEquals("UTR-90001", releases.get(0).getTransactionReference());
        assertEquals(0, new BigDecimal("30000.00").compareTo(releases.get(0).getReleasedAmount()));

        // Check Updated Disbursement State
        Disbursement updatedDisb = disbursementRepository.findById(disbursement.getId()).orElseThrow();
        assertEquals("PARTIALLY_RELEASED", updatedDisb.getStatus());
        assertEquals(0, new BigDecimal("30000.00").compareTo(updatedDisb.getDisbursedAmount()));
        assertEquals(0, new BigDecimal("70000.00").compareTo(updatedDisb.getRemainingAmount()));
    }

    @Test
    @DisplayName("Test 5: Block Out-of-Order Stage Release (Stage 2 before Stage 1)")
    void testBlockOutOfOrderRelease() {
        Disbursement disbursement = disbursementService.initializeDisbursement(testApplication, testUser);
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        for (Milestone m : milestones) {
            m.setComplianceVerificationStatus("VERIFIED");
            milestoneRepository.save(m);
        }

        // Attempt releasing Stage 2 without releasing Stage 1
        StageReleaseDto dto2 = new StageReleaseDto();
        dto2.setMilestoneId(milestones.get(1).getId()); // Stage 2
        dto2.setApplicationId(testApplication.getId());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            disbursementService.releaseMilestone(dto2, "finance_officer@gov.in");
        });

        assertTrue(ex.getMessage().contains("must be released/completed before releasing stage"));
    }

    @Test
    @DisplayName("Test 6: Prevent Over-Release Exceeding Sanctioned Amount")
    void testPreventOverRelease() {
        Disbursement disbursement = disbursementService.initializeDisbursement(testApplication, testUser);
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        milestones.get(0).setComplianceVerificationStatus("VERIFIED");
        milestoneRepository.save(milestones.get(0));

        // Stage 1 Release with invalid excessive amount (₹2,00,000)
        StageReleaseDto excessiveDto = new StageReleaseDto();
        excessiveDto.setMilestoneId(milestones.get(0).getId());
        excessiveDto.setApplicationId(testApplication.getId());
        excessiveDto.setReleaseAmount(new BigDecimal("200000.00")); // Exceeds sanctioned 100k

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            disbursementService.releaseMilestone(excessiveDto, "finance_officer@gov.in");
        });

        assertTrue(ex.getMessage().contains("exceeds remaining sanctioned balance"));
    }

    @Test
    @DisplayName("Test 7: Prevent Duplicate Milestone Release")
    void testPreventDuplicateRelease() {
        Disbursement disbursement = disbursementService.initializeDisbursement(testApplication, testUser);
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        milestones.get(0).setComplianceVerificationStatus("VERIFIED");
        milestoneRepository.save(milestones.get(0));

        StageReleaseDto dto = new StageReleaseDto();
        dto.setMilestoneId(milestones.get(0).getId());
        dto.setApplicationId(testApplication.getId());
        dto.setReleaseAmount(milestones.get(0).getMilestoneAmount());

        disbursementService.releaseMilestone(dto, "finance_officer@gov.in");

        // Duplicate release attempt
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            disbursementService.releaseMilestone(dto, "finance_officer@gov.in");
        });

        assertTrue(ex.getMessage().contains("fund has already been released"));
    }

    @Test
    @DisplayName("Test 8: Complete Final Stage updates Disbursement & Application status to COMPLETED")
    void testFinalStageCompletion() {
        Disbursement disbursement = disbursementService.initializeDisbursement(testApplication, testUser);
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        for (Milestone m : milestones) {
            m.setComplianceVerificationStatus("VERIFIED");
            milestoneRepository.save(m);
        }

        // Release Stage 1
        StageReleaseDto dto1 = new StageReleaseDto();
        dto1.setMilestoneId(milestones.get(0).getId());
        disbursementService.releaseMilestone(dto1, "finance@gov.in");

        // Release Stage 2
        StageReleaseDto dto2 = new StageReleaseDto();
        dto2.setMilestoneId(milestones.get(1).getId());
        disbursementService.releaseMilestone(dto2, "finance@gov.in");

        // Release Stage 3 (Final)
        StageReleaseDto dto3 = new StageReleaseDto();
        dto3.setMilestoneId(milestones.get(2).getId());
        disbursementService.releaseMilestone(dto3, "finance@gov.in");

        Disbursement updatedDisb = disbursementRepository.findById(disbursement.getId()).orElseThrow();
        assertEquals("COMPLETED", updatedDisb.getStatus());
        assertEquals(0, new BigDecimal("100000.00").compareTo(updatedDisb.getDisbursedAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(updatedDisb.getRemainingAmount()));

        Application updatedApp = applicationRepository.findById(testApplication.getId()).orElseThrow();
        assertEquals("COMPLETED", updatedApp.getCurrentStage());
    }

    @Test
    @DisplayName("Test 9: Stage Hold and Resume Lifecycle")
    void testStageHoldAndResume() {
        Disbursement disbursement = disbursementService.initializeDisbursement(testApplication, testUser);
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        Long stage1Id = milestones.get(0).getId();
        milestones.get(0).setComplianceVerificationStatus("VERIFIED");
        milestoneRepository.save(milestones.get(0));

        // Hold Stage 1
        Milestone heldStage = disbursementService.holdMilestone(stage1Id, "Aadhaar bank link pending", "finance@gov.in");
        assertEquals("ON_HOLD", heldStage.getCompletionStatus());

        // Attempt release while on hold -> should fail
        StageReleaseDto dto = new StageReleaseDto();
        dto.setMilestoneId(stage1Id);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            disbursementService.releaseMilestone(dto, "finance@gov.in");
        });
        assertTrue(ex.getMessage().contains("Cannot release stage while it is ON HOLD"));

        // Unhold Stage 1
        Milestone resumedStage = disbursementService.unholdMilestone(stage1Id, "finance@gov.in");
        assertEquals("PENDING", resumedStage.getCompletionStatus());
    }

    @Test
    @DisplayName("Test 10: Audit Log Entries Created for Financial Actions")
    void testAuditLogEntriesCreated() {
        Disbursement disbursement = disbursementService.initializeDisbursement(testApplication, testUser);
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        milestones.get(0).setComplianceVerificationStatus("VERIFIED");
        milestoneRepository.save(milestones.get(0));

        StageReleaseDto dto = new StageReleaseDto();
        dto.setMilestoneId(milestones.get(0).getId());
        dto.setTransactionReference("UTR-AUDIT-99");
        disbursementService.releaseMilestone(dto, "finance@gov.in");

        List<AuditLog> auditLogs = auditLogRepository.findAll();
        boolean foundReleaseLog = auditLogs.stream().anyMatch(log -> "MILESTONE_RELEASED".equals(log.getAction()) || "TERM_RELEASED".equals(log.getAction()));
        assertTrue(foundReleaseLog, "Audit log for MILESTONE_RELEASED or TERM_RELEASED must exist");
    }
}
