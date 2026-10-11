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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class PartiallyReleasedApplicationsTest {

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
        testUser.setEmail("finance_test_" + timestamp + "@gov.in");
        testUser.setPassword("Password@123");
        testUser.setFullName("Finance Test User");
        testUser.setMobileNumber("98765" + (timestamp % 100000));
        testUser = userRepository.save(testUser);

        testBeneficiary = new Beneficiary();
        testBeneficiary.setUser(testUser);
        testBeneficiary.setIdentityType("AADHAAR");
        testBeneficiary.setIdentityNumber("99887766" + (timestamp % 10000));
        testBeneficiary.setAddress("456 Treasury Road");
        testBeneficiary.setAge(40);
        testBeneficiary.setGender("FEMALE");
        testBeneficiary.setPincode("110001");
        testBeneficiary.setCategory("GENERAL");
        testBeneficiary.setState("DELHI");
        testBeneficiary.setDistrict("NEW DELHI");
        testBeneficiary = beneficiaryRepository.save(testBeneficiary);

        testScheme = new Scheme();
        testScheme.setName("Partially Released Test Scheme " + timestamp);
        testScheme.setCategory("AGRICULTURE");
        testScheme.setTotalBudget(new BigDecimal("1000000.00"));
        testScheme.setRemainingBudget(new BigDecimal("1000000.00"));
        testScheme.setMinAmount(new BigDecimal("1000.00"));
        testScheme.setMaxAmount(new BigDecimal("100000.00"));
        testScheme.setStatus("ACTIVE");
        testScheme = schemeRepository.save(testScheme);
    }

    private Application createTestApplication(String suffix, BigDecimal sanctionedAmount) {
        Application app = new Application();
        app.setApplicationNumber("APP-PR-" + suffix + "-" + System.currentTimeMillis());
        app.setBeneficiary(testBeneficiary);
        app.setScheme(testScheme);
        app.setRequestedAmount(sanctionedAmount);
        app.setSanctionedAmount(sanctionedAmount);
        app.setStatus("APPROVED");
        app.setCurrentStage("DISBURSEMENT");
        return applicationRepository.save(app);
    }

    private void verifyMilestoneCompliance(List<Milestone> milestones) {
        for (Milestone m : milestones) {
            m.setComplianceVerificationStatus("VERIFIED");
            milestoneRepository.save(m);
        }
    }

    @Test
    @DisplayName("Queue Separation Test 1: 0 released / 3 pending -> Active Queue = YES, Partial Queue = NO")
    void testZeroReleasedQueueSeparation() {
        Application app = createTestApplication("0REL", new BigDecimal("100000.00"));
        disbursementService.initializeDisbursement(app, testUser);

        List<Application> activeQueue = disbursementService.getUnreleasedActiveDisbursementApplications();
        List<PartiallyReleasedApplicationDto> partialQueue = disbursementService.getPartiallyReleasedApplications();

        boolean inActive = activeQueue.stream().anyMatch(a -> a.getId().equals(app.getId()));
        boolean inPartial = partialQueue.stream().anyMatch(p -> p.getApplicationId().equals(app.getId()));

        assertTrue(inActive, "0 released / 3 pending MUST be in Active Queue");
        assertFalse(inPartial, "0 released / 3 pending MUST NOT be in Partial Queue");
    }

    @Test
    @DisplayName("Queue Separation Test 2: 1 released / 2 pending -> Active Queue = NO, Partial Queue = YES")
    void testOneReleasedQueueSeparation() {
        Application app = createTestApplication("1REL", new BigDecimal("100000.00"));
        Disbursement disbursement = disbursementService.initializeDisbursement(app, testUser);
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        verifyMilestoneCompliance(milestones);

        // Release 1st Milestone (₹30,000)
        StageReleaseDto dto1 = new StageReleaseDto();
        dto1.setMilestoneId(milestones.get(0).getId());
        dto1.setApplicationId(app.getId());
        disbursementService.releaseMilestone(dto1, testUser.getEmail());

        List<Application> activeQueue = disbursementService.getUnreleasedActiveDisbursementApplications();
        List<PartiallyReleasedApplicationDto> partialQueue = disbursementService.getPartiallyReleasedApplications();

        boolean inActive = activeQueue.stream().anyMatch(a -> a.getId().equals(app.getId()));
        boolean inPartial = partialQueue.stream().anyMatch(p -> p.getApplicationId().equals(app.getId()));

        assertFalse(inActive, "1 released / 2 pending MUST NOT be in Active Queue");
        assertTrue(inPartial, "1 released / 2 pending MUST be in Partial Queue");

        PartiallyReleasedApplicationDto matched = partialQueue.stream()
                .filter(p -> p.getApplicationId().equals(app.getId()))
                .findFirst().orElseThrow();

        assertEquals(3, matched.getTotalTerms());
        assertEquals(1, matched.getReleasedTerms());
        assertEquals(2, matched.getPendingTerms());
        assertEquals(0, new BigDecimal("30000.00").compareTo(matched.getReleasedAmount()));
        assertEquals(0, new BigDecimal("70000.00").compareTo(matched.getRemainingAmount()));
        assertEquals(milestones.get(1).getId(), matched.getNextMilestoneId());
    }

    @Test
    @DisplayName("Queue Separation Test 3: 2 released / 1 pending -> Active Queue = NO, Partial Queue = YES")
    void testTwoReleasedQueueSeparation() {
        Application app = createTestApplication("2REL", new BigDecimal("100000.00"));
        Disbursement disbursement = disbursementService.initializeDisbursement(app, testUser);
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        verifyMilestoneCompliance(milestones);

        // Release 1st & 2nd Milestones
        StageReleaseDto dto1 = new StageReleaseDto();
        dto1.setMilestoneId(milestones.get(0).getId());
        disbursementService.releaseMilestone(dto1, testUser.getEmail());

        StageReleaseDto dto2 = new StageReleaseDto();
        dto2.setMilestoneId(milestones.get(1).getId());
        disbursementService.releaseMilestone(dto2, testUser.getEmail());

        List<Application> activeQueue = disbursementService.getUnreleasedActiveDisbursementApplications();
        List<PartiallyReleasedApplicationDto> partialQueue = disbursementService.getPartiallyReleasedApplications();

        boolean inActive = activeQueue.stream().anyMatch(a -> a.getId().equals(app.getId()));
        boolean inPartial = partialQueue.stream().anyMatch(p -> p.getApplicationId().equals(app.getId()));

        assertFalse(inActive, "2 released / 1 pending MUST NOT be in Active Queue");
        assertTrue(inPartial, "2 released / 1 pending MUST be in Partial Queue");

        PartiallyReleasedApplicationDto matched = partialQueue.stream()
                .filter(p -> p.getApplicationId().equals(app.getId()))
                .findFirst().orElseThrow();

        assertEquals(3, matched.getTotalTerms());
        assertEquals(2, matched.getReleasedTerms());
        assertEquals(1, matched.getPendingTerms());
        assertEquals(0, new BigDecimal("70000.00").compareTo(matched.getReleasedAmount()));
        assertEquals(0, new BigDecimal("30000.00").compareTo(matched.getRemainingAmount()));
        assertEquals(milestones.get(2).getId(), matched.getNextMilestoneId());
    }

    @Test
    @DisplayName("Queue Separation Test 4: 3 released / 0 pending -> Active Queue = NO, Partial Queue = NO")
    void testThreeReleasedQueueSeparation() {
        Application app = createTestApplication("3REL", new BigDecimal("100000.00"));
        Disbursement disbursement = disbursementService.initializeDisbursement(app, testUser);
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        verifyMilestoneCompliance(milestones);

        for (Milestone m : milestones) {
            StageReleaseDto dto = new StageReleaseDto();
            dto.setMilestoneId(m.getId());
            disbursementService.releaseMilestone(dto, testUser.getEmail());
        }

        List<Application> activeQueue = disbursementService.getUnreleasedActiveDisbursementApplications();
        List<PartiallyReleasedApplicationDto> partialQueue = disbursementService.getPartiallyReleasedApplications();

        boolean inActive = activeQueue.stream().anyMatch(a -> a.getId().equals(app.getId()));
        boolean inPartial = partialQueue.stream().anyMatch(p -> p.getApplicationId().equals(app.getId()));

        assertFalse(inActive, "Fully released application MUST NOT be in Active Queue");
        assertFalse(inPartial, "Fully released application MUST NOT be in Partial Queue");
    }

    @Test
    @DisplayName("Queue Separation Test 5 & 6-8: Complete Lifecycle Transition across release events")
    void testFullDisbursementLifecycleTransition() {
        Application app = createTestApplication("LIFECYCLE", new BigDecimal("100000.00"));
        Disbursement disbursement = disbursementService.initializeDisbursement(app, testUser);
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        verifyMilestoneCompliance(milestones);

        // Step 1: Initial (0 released) -> Active Queue = YES, Partial Queue = NO
        List<Application> active1 = disbursementService.getUnreleasedActiveDisbursementApplications();
        List<PartiallyReleasedApplicationDto> partial1 = disbursementService.getPartiallyReleasedApplications();
        assertTrue(active1.stream().anyMatch(a -> a.getId().equals(app.getId())));
        assertFalse(partial1.stream().anyMatch(p -> p.getApplicationId().equals(app.getId())));

        // Step 2: Release Term 1 -> Moves from Active Queue to Partial Queue
        StageReleaseDto dto1 = new StageReleaseDto();
        dto1.setMilestoneId(milestones.get(0).getId());
        disbursementService.releaseMilestone(dto1, testUser.getEmail());

        List<Application> active2 = disbursementService.getUnreleasedActiveDisbursementApplications();
        List<PartiallyReleasedApplicationDto> partial2 = disbursementService.getPartiallyReleasedApplications();
        assertFalse(active2.stream().anyMatch(a -> a.getId().equals(app.getId())));
        assertTrue(partial2.stream().anyMatch(p -> p.getApplicationId().equals(app.getId())));

        // Step 3: Release Term 2 -> Remains ONLY in Partial Queue
        StageReleaseDto dto2 = new StageReleaseDto();
        dto2.setMilestoneId(milestones.get(1).getId());
        disbursementService.releaseMilestone(dto2, testUser.getEmail());

        List<Application> active3 = disbursementService.getUnreleasedActiveDisbursementApplications();
        List<PartiallyReleasedApplicationDto> partial3 = disbursementService.getPartiallyReleasedApplications();
        assertFalse(active3.stream().anyMatch(a -> a.getId().equals(app.getId())));
        assertTrue(partial3.stream().anyMatch(p -> p.getApplicationId().equals(app.getId())));

        // Step 4: Release Term 3 (Final) -> Disappears from BOTH Queues
        StageReleaseDto dto3 = new StageReleaseDto();
        dto3.setMilestoneId(milestones.get(2).getId());
        disbursementService.releaseMilestone(dto3, testUser.getEmail());

        List<Application> active4 = disbursementService.getUnreleasedActiveDisbursementApplications();
        List<PartiallyReleasedApplicationDto> partial4 = disbursementService.getPartiallyReleasedApplications();
        assertFalse(active4.stream().anyMatch(a -> a.getId().equals(app.getId())));
        assertFalse(partial4.stream().anyMatch(p -> p.getApplicationId().equals(app.getId())));
    }

    @Test
    @DisplayName("Queue Separation Test 9: No duplicate application entries in queues")
    void testNoDuplicateApplicationRows() {
        Application app = createTestApplication("NODUP", new BigDecimal("100000.00"));
        Disbursement disbursement = disbursementService.initializeDisbursement(app, testUser);
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        verifyMilestoneCompliance(milestones);

        // Active Queue check for duplicates
        List<Application> activeQueue = disbursementService.getUnreleasedActiveDisbursementApplications();
        long activeCount = activeQueue.stream().filter(a -> a.getId().equals(app.getId())).count();
        assertEquals(1, activeCount, "Application should appear exactly ONCE in Active Queue");

        // Partial Queue check for duplicates after 1 release
        StageReleaseDto dto1 = new StageReleaseDto();
        dto1.setMilestoneId(milestones.get(0).getId());
        disbursementService.releaseMilestone(dto1, testUser.getEmail());

        List<PartiallyReleasedApplicationDto> partialQueue = disbursementService.getPartiallyReleasedApplications();
        long partialCount = partialQueue.stream().filter(p -> p.getApplicationId().equals(app.getId())).count();
        assertEquals(1, partialCount, "Application should appear exactly ONCE in Partial Queue");
    }

    @Test
    @DisplayName("Queue Separation Test 10: Sequential milestone rule remains enforced")
    void testSequentialMilestoneEnforced() {
        Application app = createTestApplication("SEQ", new BigDecimal("100000.00"));
        Disbursement disbursement = disbursementService.initializeDisbursement(app, testUser);
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);

        // Attempting to release Milestone 2 before Milestone 1
        StageReleaseDto dto2 = new StageReleaseDto();
        dto2.setMilestoneId(milestones.get(1).getId());

        assertThrows(IllegalStateException.class, () -> {
            disbursementService.releaseMilestone(dto2, testUser.getEmail());
        });
    }
}
