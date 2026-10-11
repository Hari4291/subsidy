package com.example.digital_subsidy;

import com.example.digital_subsidy.dto.StageReleaseDto;
import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.*;
import com.example.digital_subsidy.service.DisbursementService;
import com.example.digital_subsidy.service.DocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class TermDocumentQueueWorkflowTest {

    @Autowired
    private DisbursementService disbursementService;

    @Autowired
    private DocumentService documentService;

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
    private DisbursementRepository disbursementRepository;

    @Autowired
    private DisbursementReleaseRepository releaseRepository;

    private Application testAppSingle;
    private Application testAppThree;
    private Beneficiary testBeneficiary;
    private Scheme singleScheme;
    private Scheme threeScheme;
    private User testUser;

    @BeforeEach
    void setUp() {
        long ts = System.currentTimeMillis();

        testUser = new User();
        testUser.setEmail("queue_beneficiary_" + ts + "@gov.in");
        testUser.setPassword("Password@123");
        testUser.setFullName("Queue Test Beneficiary");
        testUser.setMobileNumber("987" + (ts % 10000000));
        testUser = userRepository.save(testUser);

        testBeneficiary = new Beneficiary();
        testBeneficiary.setUser(testUser);
        testBeneficiary.setIdentityType("AADHAAR");
        testBeneficiary.setIdentityNumber("99" + (ts % 10000000000L));
        testBeneficiary.setState("Andhra Pradesh");
        testBeneficiary.setDistrict("Kakinada");
        testBeneficiary.setAddress("456 Queue Colony");
        testBeneficiary.setPincode("533001");
        testBeneficiary.setCategory("GENERAL");
        testBeneficiary.setGender("MALE");
        testBeneficiary.setAge(29);
        testBeneficiary = beneficiaryRepository.save(testBeneficiary);

        singleScheme = new Scheme();
        singleScheme.setName("Single-Phase Test Scheme " + ts);
        singleScheme.setCategory("AGRICULTURE");
        singleScheme.setMinAmount(new BigDecimal("10000.00"));
        singleScheme.setMaxAmount(new BigDecimal("40000.00"));
        singleScheme.setTotalBudget(new BigDecimal("1000000.00"));
        singleScheme.setRemainingBudget(new BigDecimal("1000000.00"));
        singleScheme.setStatus("ACTIVE");
        singleScheme = schemeRepository.save(singleScheme);

        threeScheme = new Scheme();
        threeScheme.setName("Three-Phase Test Scheme " + ts);
        threeScheme.setCategory("SOLAR");
        threeScheme.setMinAmount(new BigDecimal("45000.00"));
        threeScheme.setMaxAmount(new BigDecimal("100000.00"));
        threeScheme.setTotalBudget(new BigDecimal("2000000.00"));
        threeScheme.setRemainingBudget(new BigDecimal("2000000.00"));
        threeScheme.setStatus("ACTIVE");
        threeScheme = schemeRepository.save(threeScheme);

        // Application 1: Single Phase (<= ₹40,000)
        testAppSingle = new Application();
        testAppSingle.setApplicationNumber("APP-SINGLE-" + ts);
        testAppSingle.setBeneficiary(testBeneficiary);
        testAppSingle.setScheme(singleScheme);
        testAppSingle.setRequestedAmount(new BigDecimal("40000.00"));
        testAppSingle.setSanctionedAmount(new BigDecimal("40000.00"));
        testAppSingle.setStatus("APPROVED");
        testAppSingle.setCurrentStage("DISBURSEMENT");
        testAppSingle = applicationRepository.save(testAppSingle);
        disbursementService.initializeDisbursement(testAppSingle, null);

        // Application 2: Three Phase (> ₹40,000)
        testAppThree = new Application();
        testAppThree.setApplicationNumber("APP-THREE-" + ts);
        testAppThree.setBeneficiary(testBeneficiary);
        testAppThree.setScheme(threeScheme);
        testAppThree.setRequestedAmount(new BigDecimal("60000.00"));
        testAppThree.setSanctionedAmount(new BigDecimal("60000.00"));
        testAppThree.setStatus("APPROVED");
        testAppThree.setCurrentStage("DISBURSEMENT");
        testAppThree = applicationRepository.save(testAppThree);
        disbursementService.initializeDisbursement(testAppThree, null);
    }

    @Test
    @DisplayName("TEST 1: Upload document -> SUBMITTED status -> Appears in Field Officer queue")
    void test1_UploadDocument_AppearsInFieldOfficerQueue() throws Exception {
        Disbursement d = disbursementRepository.findByApplication(testAppSingle).orElseThrow();
        Milestone m1 = milestoneRepository.findByDisbursementOrderByIdAsc(d).get(0);

        MockMultipartFile file = new MockMultipartFile("file", "test_report.pdf", "application/pdf", "Dummy pdf content".getBytes());
        disbursementService.submitComplianceProof(m1.getId(), testAppSingle.getId(), file, "Uploaded report", testUser.getEmail());

        Milestone updated = milestoneRepository.findById(m1.getId()).orElseThrow();
        assertEquals("SUBMITTED", updated.getComplianceVerificationStatus());
        assertNotNull(updated.getSubmittedDocument());

        List<Milestone> foQueue = disbursementService.getPendingTermDocumentVerificationForFieldOfficer("Andhra Pradesh", "Kakinada");
        assertTrue(foQueue.stream().anyMatch(m -> m.getId().equals(m1.getId())));
    }

    @Test
    @DisplayName("TEST 2: District Officer queue does NOT contain application before Field Officer verification")
    void test2_DistrictOfficerQueue_DoesNotContainBeforeFOVerification() throws Exception {
        Disbursement d = disbursementRepository.findByApplication(testAppSingle).orElseThrow();
        Milestone m1 = milestoneRepository.findByDisbursementOrderByIdAsc(d).get(0);

        MockMultipartFile file = new MockMultipartFile("file", "test_report.pdf", "application/pdf", "Dummy pdf content".getBytes());
        disbursementService.submitComplianceProof(m1.getId(), testAppSingle.getId(), file, "Uploaded report", testUser.getEmail());

        List<Milestone> doQueue = disbursementService.getTermComplianceReviewsForDistrictOfficer("Andhra Pradesh", "Kakinada");
        assertFalse(doQueue.stream().anyMatch(m -> m.getId().equals(m1.getId())));
    }

    @Test
    @DisplayName("TEST 3: Field Officer verifies document -> Removed from FO queue -> Appears in DO queue")
    void test3_FOVerifiesDocument_MovesToDOQueue() throws Exception {
        Disbursement d = disbursementRepository.findByApplication(testAppSingle).orElseThrow();
        Milestone m1 = milestoneRepository.findByDisbursementOrderByIdAsc(d).get(0);

        MockMultipartFile file = new MockMultipartFile("file", "test_report.pdf", "application/pdf", "Dummy pdf content".getBytes());
        disbursementService.submitComplianceProof(m1.getId(), testAppSingle.getId(), file, "Uploaded report", testUser.getEmail());

        // Field Officer verifies
        disbursementService.verifyCompliance(m1.getId(), "FIELD_VERIFIED", "FO verified OK", "field.officer@gmail.com");

        List<Milestone> foQueue = disbursementService.getPendingTermDocumentVerificationForFieldOfficer("Andhra Pradesh", "Kakinada");
        assertFalse(foQueue.stream().anyMatch(m -> m.getId().equals(m1.getId())));

        List<Milestone> doQueue = disbursementService.getTermComplianceReviewsForDistrictOfficer("Andhra Pradesh", "Kakinada");
        assertTrue(doQueue.stream().anyMatch(m -> m.getId().equals(m1.getId())));
    }

    @Test
    @DisplayName("TEST 4: District Officer verifies -> Removed from DO queue -> Appears in Finance verified queue")
    void test4_DOVerifiesDocument_MovesToFinanceQueue() throws Exception {
        Disbursement d = disbursementRepository.findByApplication(testAppSingle).orElseThrow();
        Milestone m1 = milestoneRepository.findByDisbursementOrderByIdAsc(d).get(0);

        MockMultipartFile file = new MockMultipartFile("file", "test_report.pdf", "application/pdf", "Dummy pdf content".getBytes());
        disbursementService.submitComplianceProof(m1.getId(), testAppSingle.getId(), file, "Uploaded report", testUser.getEmail());

        disbursementService.verifyCompliance(m1.getId(), "FIELD_VERIFIED", "FO verified", "field.officer@gmail.com");
        disbursementService.verifyCompliance(m1.getId(), "DISTRICT_APPROVED", "DO approved", "kalyanramvase377@gmail.com");

        List<Milestone> doQueue = disbursementService.getTermComplianceReviewsForDistrictOfficer("Andhra Pradesh", "Kakinada");
        assertFalse(doQueue.stream().anyMatch(m -> m.getId().equals(m1.getId())));

        List<Milestone> financeQueue = disbursementService.getVerifiedTermsPendingRelease();
        assertTrue(financeQueue.stream().anyMatch(m -> m.getId().equals(m1.getId())));
    }

    @Test
    @DisplayName("TEST 5: Finance releases term -> Removed from Finance verified queue -> Release record exists")
    void test5_FinanceReleasesTerm_RemovesFromFinanceQueueAndCreatesReleaseRecord() throws Exception {
        Disbursement d = disbursementRepository.findByApplication(testAppSingle).orElseThrow();
        Milestone m1 = milestoneRepository.findByDisbursementOrderByIdAsc(d).get(0);

        MockMultipartFile file = new MockMultipartFile("file", "test_report.pdf", "application/pdf", "Dummy pdf content".getBytes());
        disbursementService.submitComplianceProof(m1.getId(), testAppSingle.getId(), file, "Uploaded report", testUser.getEmail());

        disbursementService.verifyCompliance(m1.getId(), "FIELD_VERIFIED", "FO verified", "field.officer@gmail.com");
        disbursementService.verifyCompliance(m1.getId(), "DISTRICT_APPROVED", "DO approved", "kalyanramvase377@gmail.com");

        StageReleaseDto releaseDto = new StageReleaseDto();
        releaseDto.setMilestoneId(m1.getId());
        releaseDto.setApplicationId(testAppSingle.getId());
        releaseDto.setReleaseAmount(new BigDecimal("40000.00"));

        disbursementService.releaseMilestone(releaseDto, "pabbuletihari0@gmail.com");

        List<Milestone> financeQueue = disbursementService.getVerifiedTermsPendingRelease();
        assertFalse(financeQueue.stream().anyMatch(m -> m.getId().equals(m1.getId())));

        List<DisbursementRelease> releases = releaseRepository.findByApplicationIdOrderByReleaseDateDesc(testAppSingle.getId());
        assertFalse(releases.isEmpty());
        assertEquals(new BigDecimal("40000.00"), releases.get(0).getReleasedAmount());
    }

    @Test
    @DisplayName("TEST 6: Single-phase <= ₹40,000 -> Exactly 1 milestone tranche")
    void test6_SinglePhaseLogic_ExactlyOneMilestone() {
        Disbursement d = disbursementRepository.findByApplication(testAppSingle).orElseThrow();
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(d);

        assertEquals(1, milestones.size());
        assertEquals(100, milestones.get(0).getPercentage());
        assertEquals(new BigDecimal("40000.00"), milestones.get(0).getMilestoneAmount());
    }

    @Test
    @DisplayName("TEST 7: Three-phase > ₹40,000 -> Term 1 release activates Term 2 -> Term 2 upload appears in FO queue")
    void test7_ThreePhaseLogic_Term1ReleaseActivatesTerm2AndAppearsInFOQueue() throws Exception {
        Disbursement d = disbursementRepository.findByApplication(testAppThree).orElseThrow();
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(d);
        assertEquals(3, milestones.size());

        Milestone m1 = milestones.get(0);
        Milestone m2 = milestones.get(1);

        MockMultipartFile file1 = new MockMultipartFile("file", "term1_report.pdf", "application/pdf", "Term 1 pdf".getBytes());
        disbursementService.submitComplianceProof(m1.getId(), testAppThree.getId(), file1, "Term 1 report", testUser.getEmail());
        disbursementService.verifyCompliance(m1.getId(), "FIELD_VERIFIED", "FO verified", "field.officer@gmail.com");
        disbursementService.verifyCompliance(m1.getId(), "DISTRICT_APPROVED", "DO approved", "kalyanramvase377@gmail.com");

        StageReleaseDto releaseDto1 = new StageReleaseDto();
        releaseDto1.setMilestoneId(m1.getId());
        releaseDto1.setApplicationId(testAppThree.getId());
        releaseDto1.setReleaseAmount(m1.getMilestoneAmount());
        disbursementService.releaseMilestone(releaseDto1, "pabbuletihari0@gmail.com");

        // Term 2 is now active. Upload Term 2 document
        MockMultipartFile file2 = new MockMultipartFile("file", "term2_report.pdf", "application/pdf", "Term 2 pdf".getBytes());
        disbursementService.submitComplianceProof(m2.getId(), testAppThree.getId(), file2, "Term 2 report", testUser.getEmail());

        List<Milestone> foQueue = disbursementService.getPendingTermDocumentVerificationForFieldOfficer("Andhra Pradesh", "Kakinada");
        assertTrue(foQueue.stream().anyMatch(m -> m.getId().equals(m2.getId())));
    }

    @Test
    @DisplayName("TEST 8: No uploaded document -> FO queue does NOT contain the milestone")
    void test8_NoUploadedDocument_NotInFOQueue() {
        Disbursement d = disbursementRepository.findByApplication(testAppSingle).orElseThrow();
        Milestone m1 = milestoneRepository.findByDisbursementOrderByIdAsc(d).get(0);

        List<Milestone> foQueue = disbursementService.getPendingTermDocumentVerificationForFieldOfficer("Andhra Pradesh", "Kakinada");
        assertFalse(foQueue.stream().anyMatch(m -> m.getId().equals(m1.getId())));
    }

    @Test
    @DisplayName("TEST 9: Wrong application document / milestone mismatch rejected")
    void test9_WrongApplicationDocument_Rejected() throws Exception {
        Disbursement dSingle = disbursementRepository.findByApplication(testAppSingle).orElseThrow();
        Milestone mSingle = milestoneRepository.findByDisbursementOrderByIdAsc(dSingle).get(0);

        MockMultipartFile file = new MockMultipartFile("file", "wrong_report.pdf", "application/pdf", "Wrong pdf".getBytes());

        // Attempt to submit milestone belonging to testAppSingle under testAppThree ID
        assertThrows(IllegalArgumentException.class, () -> {
            disbursementService.submitComplianceProof(mSingle.getId(), testAppThree.getId(), file, "Mismatch attempt", testUser.getEmail());
        });
    }

    @Test
    @DisplayName("TEST 10: Duplicate successful release prevented and no duplicate Finance queue row")
    void test10_DuplicateReleasePrevented_NoDuplicateFinanceQueueRow() throws Exception {
        Disbursement d = disbursementRepository.findByApplication(testAppSingle).orElseThrow();
        Milestone m1 = milestoneRepository.findByDisbursementOrderByIdAsc(d).get(0);

        MockMultipartFile file = new MockMultipartFile("file", "test_report.pdf", "application/pdf", "Dummy pdf content".getBytes());
        disbursementService.submitComplianceProof(m1.getId(), testAppSingle.getId(), file, "Uploaded report", testUser.getEmail());

        disbursementService.verifyCompliance(m1.getId(), "FIELD_VERIFIED", "FO verified", "field.officer@gmail.com");
        disbursementService.verifyCompliance(m1.getId(), "DISTRICT_APPROVED", "DO approved", "kalyanramvase377@gmail.com");

        StageReleaseDto releaseDto = new StageReleaseDto();
        releaseDto.setMilestoneId(m1.getId());
        releaseDto.setApplicationId(testAppSingle.getId());
        releaseDto.setReleaseAmount(new BigDecimal("40000.00"));

        disbursementService.releaseMilestone(releaseDto, "finance.approver@gmail.com");

        // Attempting a second release on the same milestone must throw IllegalStateException
        assertThrows(IllegalStateException.class, () -> {
            disbursementService.releaseMilestone(releaseDto, "finance.approver@gmail.com");
        });

        // Ensure finance queue has 0 rows for this milestone
        List<Milestone> financeQueue = disbursementService.getVerifiedTermsPendingRelease();
        assertFalse(financeQueue.stream().anyMatch(m -> m.getId().equals(m1.getId())));
    }
}
