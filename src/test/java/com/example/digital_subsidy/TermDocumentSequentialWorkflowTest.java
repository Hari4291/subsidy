package com.example.digital_subsidy;

import com.example.digital_subsidy.dto.ComplianceSubmissionDto;
import com.example.digital_subsidy.dto.PartiallyReleasedApplicationDto;
import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.*;
import com.example.digital_subsidy.service.DisbursementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class TermDocumentSequentialWorkflowTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BeneficiaryRepository beneficiaryRepository;

    @Autowired
    private OfficerRepository officerRepository;

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private DisbursementRepository disbursementRepository;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private DisbursementService disbursementService;

    private User beneficiaryUser;
    private Beneficiary beneficiary;
    private User foUser;
    private Officer foOfficer;
    private User doUser;
    private Officer doOfficer;
    private Scheme testScheme;
    private Application testApp;
    private Disbursement testDisbursement;
    private Milestone term1;
    private Milestone term2;

    @BeforeEach
    void setUp() {
        long ts = System.currentTimeMillis();

        // Beneficiary user & record
        beneficiaryUser = new User();
        beneficiaryUser.setEmail("seq_ben_" + ts + "@gov.in");
        beneficiaryUser.setPassword("Password@123");
        beneficiaryUser.setFullName("Sequential Beneficiary");
        beneficiaryUser.setMobileNumber("900" + (ts % 10000000));
        beneficiaryUser = userRepository.save(beneficiaryUser);

        beneficiary = new Beneficiary();
        beneficiary.setUser(beneficiaryUser);
        beneficiary.setIdentityType("AADHAAR");
        beneficiary.setIdentityNumber("123456" + (ts % 1000000));
        beneficiary.setAddress("123 Test Street");
        beneficiary.setAge(30);
        beneficiary.setGender("MALE");
        beneficiary.setPincode("500001");
        beneficiary.setCategory("GENERAL");
        beneficiary.setState("TELANGANA");
        beneficiary.setDistrict("HYDERABAD");
        beneficiary = beneficiaryRepository.save(beneficiary);

        // Field Officer
        foUser = new User();
        foUser.setEmail("seq_fo_" + ts + "@gov.in");
        foUser.setPassword("Password@123");
        foUser.setFullName("Field Officer Test");
        foUser.setMobileNumber("901" + (ts % 10000000));
        foUser = userRepository.save(foUser);

        foOfficer = new Officer();
        foOfficer.setUser(foUser);
        foOfficer.setFullName(foUser.getFullName());
        foOfficer.setEmail(foUser.getEmail());
        foOfficer.setMobileNumber(foUser.getMobileNumber());
        foOfficer.setOfficerType("FIELD_OFFICER");
        foOfficer.setState("TELANGANA");
        foOfficer.setDistrict("HYDERABAD");
        foOfficer = officerRepository.save(foOfficer);

        // District Officer
        doUser = new User();
        doUser.setEmail("seq_do_" + ts + "@gov.in");
        doUser.setPassword("Password@123");
        doUser.setFullName("District Officer Test");
        doUser.setMobileNumber("902" + (ts % 10000000));
        doUser = userRepository.save(doUser);

        doOfficer = new Officer();
        doOfficer.setUser(doUser);
        doOfficer.setFullName(doUser.getFullName());
        doOfficer.setEmail(doUser.getEmail());
        doOfficer.setMobileNumber(doUser.getMobileNumber());
        doOfficer.setOfficerType("DISTRICT_OFFICER");
        doOfficer.setState("TELANGANA");
        doOfficer.setDistrict("HYDERABAD");
        doOfficer = officerRepository.save(doOfficer);

        // Scheme & Application
        testScheme = new Scheme();
        testScheme.setName("Sequential Term Scheme " + ts);
        testScheme.setCategory("AGRICULTURE");
        testScheme.setTotalBudget(new BigDecimal("1000000.00"));
        testScheme.setRemainingBudget(new BigDecimal("1000000.00"));
        testScheme.setMinAmount(new BigDecimal("1000.00"));
        testScheme.setMaxAmount(new BigDecimal("200000.00"));
        testScheme.setStatus("ACTIVE");
        testScheme = schemeRepository.save(testScheme);

        testApp = new Application();
        testApp.setApplicationNumber("APP-SEQ-" + ts);
        testApp.setBeneficiary(beneficiary);
        testApp.setScheme(testScheme);
        testApp.setRequestedAmount(new BigDecimal("50000.00"));
        testApp.setSanctionedAmount(new BigDecimal("50000.00"));
        testApp.setStatus("FINANCE_APPROVED");
        testApp.setCurrentStage("DISBURSEMENT");
        testApp = applicationRepository.save(testApp);

        // Disbursement with 2 terms
        testDisbursement = disbursementService.initializeDisbursement(testApp, foUser);
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(testDisbursement);
        assertTrue(milestones.size() >= 2, "Disbursement should have at least 2 milestone terms");

        term1 = milestones.get(0);
        term2 = milestones.get(1);
    }

    @Test
    @DisplayName("Test Case 1: Before Term 1 release, Term 2 is locked/inactive")
    void testBeforeTerm1Release_Term2IsLocked() {
        // Beneficiary action required should return Term 1, NOT Term 2
        List<Milestone> benMilestones = disbursementService.getActionRequiredTermMilestonesForBeneficiary(beneficiary.getId());
        assertTrue(benMilestones.stream().anyMatch(m -> m.getId().equals(term1.getId())), "Term 1 must be action required for beneficiary");
        assertFalse(benMilestones.stream().anyMatch(m -> m.getId().equals(term2.getId())), "Term 2 must NOT be active before Term 1 release");

        // Finance partially released applications should NOT include this app yet
        List<PartiallyReleasedApplicationDto> partiallyReleased = disbursementService.getPartiallyReleasedApplications();
        assertFalse(partiallyReleased.stream().anyMatch(dto -> dto.getApplicationId().equals(testApp.getId())), "App should NOT be in partially released list before Term 1 release");
    }

    @Test
    @DisplayName("Test Case 2: Term 1 Release triggers Term 2 Activation & Due Date Persistence")
    void testTerm1Release_TriggersTerm2Activation() {
        // Mark Term 1 verified and release it
        term1.setComplianceVerificationStatus("VERIFIED");
        milestoneRepository.save(term1);

        disbursementService.releaseMilestone(term1.getId());

        // Verify Term 1 status = RELEASED / COMPLETED
        Milestone updatedTerm1 = milestoneRepository.findById(term1.getId()).orElseThrow();
        assertTrue("RELEASED".equalsIgnoreCase(updatedTerm1.getCompletionStatus()) || "COMPLETED".equalsIgnoreCase(updatedTerm1.getCompletionStatus()));
        assertNotNull(updatedTerm1.getReleaseDate(), "Term 1 release date must be set");

        // Verify Term 2 is ACTIVATED with generated due date stored in DB
        Milestone updatedTerm2 = milestoneRepository.findById(term2.getId()).orElseThrow();
        assertNotNull(updatedTerm2.getDueDate(), "Term 2 due date must be generated and persisted in DB");
        assertTrue(updatedTerm2.getDueDate().isAfter(LocalDate.now().minusDays(1)), "Term 2 due date must be today or future");

        // User Dashboard: Term 2 becomes visible under actionRequiredTermMilestones
        List<Milestone> benMilestones = disbursementService.getActionRequiredTermMilestonesForBeneficiary(beneficiary.getId());
        assertTrue(benMilestones.stream().anyMatch(m -> m.getId().equals(term2.getId())), "Term 2 must become visible to beneficiary after Term 1 release");

        // Finance Dashboard: App becomes visible in partially released applications with Term 2 due date
        List<PartiallyReleasedApplicationDto> partiallyReleased = disbursementService.getPartiallyReleasedApplications();
        PartiallyReleasedApplicationDto appDto = partiallyReleased.stream()
                .filter(dto -> dto.getApplicationId().equals(testApp.getId()))
                .findFirst().orElse(null);
        assertNotNull(appDto, "App must appear in Finance partially released dashboard section");
        assertEquals(term2.getDueDate(), appDto.getNextDueDate(), "Finance dashboard must display Term 2 due date from database");
    }

    @Test
    @DisplayName("Test Cases 3 to 7: Term 2 End-to-End Submission -> Field Officer -> District Officer -> Finance Approver -> Release")
    void testTerm2FullSequentialWorkflow() throws Exception {
        // Setup: Release Term 1
        term1.setComplianceVerificationStatus("VERIFIED");
        milestoneRepository.save(term1);
        disbursementService.releaseMilestone(term1.getId());

        // Step 3 & 4: User uploads & submits Term 2 document
        MockMultipartFile docFile = new MockMultipartFile("file", "term2_proof.pdf", "application/pdf", "Term 2 Compliance Proof Content".getBytes());
        ComplianceSubmissionDto subDto = new ComplianceSubmissionDto();
        subDto.setMilestoneId(term2.getId());
        subDto.setBeneficiaryRemarks("Term 2 installation completed");

        disbursementService.submitComplianceProof(subDto, docFile, beneficiaryUser.getEmail());

        // Field Officer Pending Queue = 1
        List<Milestone> foPendingStep1 = disbursementService.getPendingTermDocumentVerificationForFieldOfficer("TELANGANA", "HYDERABAD");
        List<Milestone> doPendingStep1 = disbursementService.getTermComplianceReviewsForDistrictOfficer("TELANGANA", "HYDERABAD");
        List<Milestone> financePendingStep1 = disbursementService.getVerifiedTermsPendingRelease();

        assertTrue(foPendingStep1.stream().anyMatch(m -> m.getId().equals(term2.getId())), "Term 2 must appear in Field Officer pending queue after submission");
        assertFalse(doPendingStep1.stream().anyMatch(m -> m.getId().equals(term2.getId())), "Term 2 must NOT appear in District Officer queue before FO verification");
        assertFalse(financePendingStep1.stream().anyMatch(m -> m.getId().equals(term2.getId())), "Term 2 must NOT appear in Finance Approver queue before DO approval");

        // Step 5: Field Officer verifies Term 2
        disbursementService.verifyCompliance(term2.getId(), "VERIFY", "FO Verified document", foUser.getEmail());

        List<Milestone> foPendingStep2 = disbursementService.getPendingTermDocumentVerificationForFieldOfficer("TELANGANA", "HYDERABAD");
        List<Milestone> doPendingStep2 = disbursementService.getTermComplianceReviewsForDistrictOfficer("TELANGANA", "HYDERABAD");
        List<Milestone> financePendingStep2 = disbursementService.getVerifiedTermsPendingRelease();

        assertFalse(foPendingStep2.stream().anyMatch(m -> m.getId().equals(term2.getId())), "Term 2 must disappear from Field Officer queue after FO verification");
        assertTrue(doPendingStep2.stream().anyMatch(m -> m.getId().equals(term2.getId())), "Term 2 must appear in District Officer queue after FO verification");
        assertFalse(financePendingStep2.stream().anyMatch(m -> m.getId().equals(term2.getId())), "Term 2 must NOT appear in Finance Approver queue before DO approval");

        // Step 6: District Officer approves Term 2
        disbursementService.verifyCompliance(term2.getId(), "VERIFY", "DO Approved document", doUser.getEmail());

        List<Milestone> foPendingStep3 = disbursementService.getPendingTermDocumentVerificationForFieldOfficer("TELANGANA", "HYDERABAD");
        List<Milestone> doPendingStep3 = disbursementService.getTermComplianceReviewsForDistrictOfficer("TELANGANA", "HYDERABAD");
        List<Milestone> financePendingStep3 = disbursementService.getVerifiedTermsPendingRelease();

        assertFalse(foPendingStep3.stream().anyMatch(m -> m.getId().equals(term2.getId())), "Term 2 must NOT appear in Field Officer queue after DO approval");
        assertFalse(doPendingStep3.stream().anyMatch(m -> m.getId().equals(term2.getId())), "Term 2 must disappear from District Officer queue after DO approval");
        assertTrue(financePendingStep3.stream().anyMatch(m -> m.getId().equals(term2.getId())), "Term 2 must appear in Finance Approver queue after DO approval");

        // Step 7: Finance Approver releases Term 2
        disbursementService.releaseMilestone(term2.getId());

        List<Milestone> financePendingStep4 = disbursementService.getVerifiedTermsPendingRelease();
        assertFalse(financePendingStep4.stream().anyMatch(m -> m.getId().equals(term2.getId())), "Term 2 must disappear from Finance Approver queue after financial release");

        Milestone finalTerm2 = milestoneRepository.findById(term2.getId()).orElseThrow();
        assertTrue("RELEASED".equalsIgnoreCase(finalTerm2.getCompletionStatus()) || "COMPLETED".equalsIgnoreCase(finalTerm2.getCompletionStatus()),
                "Term 2 completion status must be RELEASED or COMPLETED");
    }
}
