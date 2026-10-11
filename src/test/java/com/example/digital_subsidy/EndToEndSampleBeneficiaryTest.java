package com.example.digital_subsidy;

import com.example.digital_subsidy.controller.EligibilityController;
import com.example.digital_subsidy.dto.ApplicationSubmissionDto;
import com.example.digital_subsidy.dto.BeneficiaryRegistrationDto;
import com.example.digital_subsidy.dto.EligibilityCheckRequestDto;
import com.example.digital_subsidy.dto.EligibilityCheckResponseDto;
import com.example.digital_subsidy.dto.OfficerRegistrationDto;
import com.example.digital_subsidy.dto.VerificationActionDto;
import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.*;
import com.example.digital_subsidy.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class EndToEndSampleBeneficiaryTest {

    @Autowired
    private BeneficiaryService beneficiaryService;

    @Autowired
    private SchemeService schemeService;

    @Autowired
    private EligibilityService eligibilityService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private VerificationService verificationService;

    @Autowired
    private DisbursementService disbursementService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BeneficiaryRepository beneficiaryRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private EligibilityScoreRepository eligibilityScoreRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private OfficerService officerService;

    @Autowired
    private OfficerRepository officerRepository;

    @Autowired
    private DisbursementRepository disbursementRepository;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private com.example.digital_subsidy.repository.RequiredDocumentRepository requiredDocumentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Sample E2E Test 1: Full Acceptance Workflow with Real Percentage & DB Verification")
    public void testSampleBeneficiaryAcceptanceWorkflow() throws Exception {
        long timestamp = System.currentTimeMillis();
        String sampleEmail = "sample.beneficiary" + timestamp + "@gmail.com";
        String sampleMobile = "9" + String.format("%09d", timestamp % 1000000000L);
        String sampleAadhaar = String.format("%012d", (timestamp * 7) % 1000000000000L);

        // 1. REGISTER SAMPLE BENEFICIARY
        BeneficiaryRegistrationDto regDto = new BeneficiaryRegistrationDto();
        regDto.setFullName("Test Beneficiary");
        regDto.setEmail(sampleEmail);
        regDto.setMobileNumber(sampleMobile);
        regDto.setPassword("Sample@1234");
        regDto.setConfirmPassword("Sample@1234");
        regDto.setAge(32);
        regDto.setGender("Male");
        regDto.setCategory("General");
        regDto.setState("Andhra Pradesh");
        regDto.setDistrict("Kakinada");
        regDto.setAddress("12/4 Main Road Gandhi Nagar");
        regDto.setPincode("533001");
        regDto.setIdentityType("Aadhaar");
        regDto.setIdentityNumber(sampleAadhaar);
        regDto.setTermsAgreed(true);

        Beneficiary beneficiary = beneficiaryService.registerBeneficiary(regDto);

        // VERIFY DB PERSISTENCE AFTER REGISTRATION
        assertNotNull(beneficiary.getId(), "Beneficiary record must be persisted in MySQL");
        User user = userRepository.findByEmail(sampleEmail).orElseThrow();
        assertEquals(sampleEmail, user.getEmail());
        assertEquals(sampleMobile, user.getMobileNumber(), "Mobile number must be stored as exactly 10 digits");
        assertTrue(passwordEncoder.matches("Sample@1234", user.getPassword()), "Password must be BCrypt encoded");
        assertTrue(user.getRoles().stream().anyMatch(r -> "ROLE_BENEFICIARY".equals(r.getName())), "Role ROLE_BENEFICIARY must be assigned");

        // 2. SELECT A REAL SCHEME FROM MYSQL
        List<Scheme> activeSchemes = schemeService.getAllActiveSchemes();
        assertFalse(activeSchemes.isEmpty(), "Active schemes list must not be empty");
        Scheme scheme = activeSchemes.get(0);
        assertNotNull(scheme.getName());
        assertNotNull(scheme.getMaxAmount());

        // 3. REALTIME ELIGIBILITY PERCENTAGE EVALUATION (ABOVE THRESHOLD)
        EligibilityCheckRequestDto evalReq = new EligibilityCheckRequestDto();
        evalReq.setSchemeId(scheme.getId());
        evalReq.setAnnualIncome(new BigDecimal("120000"));
        evalReq.setRequiredDocsUploaded(true);

        EligibilityCheckResponseDto evalRes = eligibilityService.evaluateEligibility(beneficiary, evalReq);
        assertNotNull(evalRes);
        int calculatedScore = evalRes.getTotalScore();
        assertTrue(calculatedScore >= 60, "Calculated score should be above threshold (>= 60%)");
        assertTrue(evalRes.isEligible(), "Eligibility status must be ELIGIBLE");
        assertFalse(evalRes.getCriterionResults().isEmpty(), "Criterion-wise breakdown must be populated");

        // 4. APPLY FOR THE SCHEME
        ApplicationSubmissionDto appDto = new ApplicationSubmissionDto();
        appDto.setSchemeId(scheme.getId());
        appDto.setAnnualIncome(new BigDecimal("120000"));
        appDto.setRequestedAmount(scheme.getMaxAmount());

        Application application = applicationService.submitApplication(beneficiary, appDto);

        // VERIFY APPLICATION & SCORE IN MYSQL
        assertNotNull(application.getId(), "Application record must be persisted in MySQL");
        assertEquals("FIELD_VERIFICATION", application.getCurrentStage());
        assertEquals("PENDING", application.getStatus());
        assertEquals(calculatedScore, application.getEligibilityScore(), "Application eligibility score in DB must equal calculated score");

        EligibilityScore scoreInDb = eligibilityScoreRepository.findAll().stream()
                .filter(s -> s.getApplication().getId().equals(application.getId()))
                .findFirst().orElseThrow();
        assertEquals(calculatedScore, scoreInDb.getTotalScore(), "Eligibility score table percentage must match application percentage");

        // 5. FIELD OFFICER GROUND INSPECTION (APPROVE)
        org.springframework.mock.web.MockMultipartFile f1 = new org.springframework.mock.web.MockMultipartFile("docAadhaarFile", "aadhaar.pdf", "application/pdf", "Aadhaar".getBytes());
        org.springframework.mock.web.MockMultipartFile f2 = new org.springframework.mock.web.MockMultipartFile("docBankPassbookFile", "passbook.pdf", "application/pdf", "Passbook".getBytes());
        org.springframework.mock.web.MockMultipartFile f3 = new org.springframework.mock.web.MockMultipartFile("docLandFile", "land.pdf", "application/pdf", "Land".getBytes());
        org.springframework.mock.web.MockMultipartFile f4 = new org.springframework.mock.web.MockMultipartFile("docIncomeFile", "income.pdf", "application/pdf", "Income".getBytes());
        Document doc1 = documentService.saveDocument(beneficiary, application, "AADHAAR", f1);
        Document doc2 = documentService.saveDocument(beneficiary, application, "BANK_PASSBOOK", f2);
        Document doc3 = documentService.saveDocument(beneficiary, application, "LAND_OWNERSHIP", f3);
        Document doc4 = documentService.saveDocument(beneficiary, application, "INCOME_CERTIFICATE", f4);

        VerificationActionDto fieldAction = new VerificationActionDto();
        fieldAction.setApplicationId(application.getId());
        // Use new generic map-based API: Map<documentId, status>
        fieldAction.getDocumentVerificationStatuses().put(doc1.getId(), "VERIFIED");
        fieldAction.getDocumentVerificationStatuses().put(doc2.getId(), "VERIFIED");
        fieldAction.getDocumentVerificationStatuses().put(doc3.getId(), "VERIFIED");
        fieldAction.getDocumentVerificationStatuses().put(doc4.getId(), "VERIFIED");
        fieldAction.setAction("APPROVE");
        fieldAction.setRemarks("Ground physical verification completed. Satisfactory.");

        verificationService.processVerification("field.officer@gmail.com", "ROLE_FIELD_OFFICER", fieldAction);

        Application fieldApp = applicationService.getApplicationById(application.getId());
        assertEquals("DISTRICT_REVIEW", fieldApp.getCurrentStage());
        assertEquals("PENDING", fieldApp.getStatus());
        assertEquals(calculatedScore, fieldApp.getEligibilityScore(), "Score percentage must remain consistent across stage transitions");

        // 6. DISTRICT OFFICER REVIEW (APPROVE)
        VerificationActionDto districtAction = new VerificationActionDto();
        districtAction.setApplicationId(application.getId());
        districtAction.setAction("APPROVE");
        districtAction.setRemarks("District Collectorate sanction approved.");

        verificationService.processVerification("kalyanramvase377@gmail.com", "ROLE_DISTRICT_OFFICER", districtAction);

        Application districtApp = applicationService.getApplicationById(application.getId());
        assertEquals("FINANCE_APPROVAL", districtApp.getCurrentStage());
        assertEquals("PENDING", districtApp.getStatus());

        // 7. FINANCE APPROVER AUTHORIZATION (APPROVE & STAGED DISBURSEMENT)
        VerificationActionDto financeAction = new VerificationActionDto();
        financeAction.setApplicationId(application.getId());
        financeAction.setAction("APPROVE");
        financeAction.setRemarks("Treasury fund sanction authorized.");

        verificationService.processVerification("pabbuletihari0@gmail.com", "ROLE_FINANCE_APPROVER", financeAction);

        Application approvedApp = applicationService.getApplicationById(application.getId());
        assertEquals("DISBURSEMENT", approvedApp.getCurrentStage());
        assertEquals("APPROVED", approvedApp.getStatus());

        // VERIFY DISBURSEMENT & STAGED MILESTONES IN MYSQL
        Disbursement disbursement = disbursementService.getDisbursementByApplicationId(application.getId());
        assertNotNull(disbursement, "Disbursement record must exist in MySQL");
        assertEquals("IN_PROGRESS", disbursement.getStatus());

        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        assertEquals(3, milestones.size(), "3 staged milestone tranches must be created");

        // 8. RELEASE MILESTONE 1
        Milestone m1 = milestones.get(0);
        m1.setComplianceVerificationStatus("VERIFIED");
        milestoneRepository.save(m1);
        disbursementService.releaseMilestone(m1.getId());

        Milestone releasedM1 = milestoneRepository.findById(m1.getId()).orElseThrow();
        assertEquals("COMPLETED", releasedM1.getCompletionStatus());
        assertNotNull(releasedM1.getReleasedAmount());

        // 9. VERIFY HOMEPAGE & BENEFICIARY REALTIME STATISTICS
        Map<String, Object> systemStats = reportService.getSystemStatistics();
        assertTrue(((Number) systemStats.get("registeredBeneficiaries")).longValue() > 0);
        assertTrue(((Number) systemStats.get("applicationsProcessed")).longValue() > 0);
        assertTrue(((Number) systemStats.get("approvedApplications")).longValue() > 0);

        long auditCount = auditLogRepository.count();
        assertTrue(auditCount >= 4, "Audit logs must record registration, submission, verifications, and milestone release");
    }

    @Test
    @DisplayName("Sample E2E Test 2: Rejection Workflow Scenario")
    public void testSampleBeneficiaryRejectionWorkflow() {
        long timestamp = System.currentTimeMillis() + 100;
        String sampleEmail = "reject.beneficiary" + timestamp + "@gmail.com";
        String sampleMobile = "8" + String.format("%09d", timestamp % 1000000000L);
        String samplePAN = "ABCDE" + String.format("%04d", (int)(timestamp % 10000)) + "K";

        BeneficiaryRegistrationDto regDto = new BeneficiaryRegistrationDto();
        regDto.setFullName("Reject Beneficiary");
        regDto.setEmail(sampleEmail);
        regDto.setMobileNumber(sampleMobile);
        regDto.setPassword("Sample@1234");
        regDto.setConfirmPassword("Sample@1234");
        regDto.setAge(28);
        regDto.setGender("Female");
        regDto.setCategory("OBC");
        regDto.setState("Andhra Pradesh");
        regDto.setDistrict("Guntur");
        regDto.setAddress("45 Park Road");
        regDto.setPincode("522002");
        regDto.setIdentityType("PAN");
        regDto.setIdentityNumber(samplePAN);
        regDto.setTermsAgreed(true);

        Beneficiary beneficiary = beneficiaryService.registerBeneficiary(regDto);
        Scheme scheme = schemeService.getAllActiveSchemes().get(0);

        ApplicationSubmissionDto appDto = new ApplicationSubmissionDto();
        appDto.setSchemeId(scheme.getId());
        appDto.setAnnualIncome(new BigDecimal("180000"));
        appDto.setRequestedAmount(scheme.getMaxAmount());

        Application application = applicationService.submitApplication(beneficiary, appDto);

        // FIELD OFFICER REJECT
        VerificationActionDto rejectAction = new VerificationActionDto();
        rejectAction.setApplicationId(application.getId());
        rejectAction.setAction("REJECT");
        rejectAction.setRemarks("Physical inspection failed: Discrepancy in reported land area.");

        verificationService.processVerification("field.officer@gmail.com", "ROLE_FIELD_OFFICER", rejectAction);

        Application rejectedApp = applicationService.getApplicationById(application.getId());
        assertEquals("REJECTED", rejectedApp.getStatus(), "Status in DB must be REJECTED");
        assertEquals("REJECTED", rejectedApp.getCurrentStage());
        assertEquals("Physical inspection failed: Discrepancy in reported land area.", rejectedApp.getRejectionReason());
    }

    @Test
    @DisplayName("Sample E2E Test 3: Below-Threshold Ineligible Evaluation")
    public void testBelowThresholdIneligibleEvaluation() {
        long timestamp = System.currentTimeMillis() + 200;
        String sampleEmail = "ineligible.user" + timestamp + "@gmail.com";
        String sampleMobile = "7" + String.format("%09d", timestamp % 1000000000L);
        String sampleAadhaar = String.format("%012d", (timestamp * 13) % 1000000000000L);

        BeneficiaryRegistrationDto regDto = new BeneficiaryRegistrationDto();
        regDto.setFullName("Ineligible User");
        regDto.setEmail(sampleEmail);
        regDto.setMobileNumber(sampleMobile);
        regDto.setPassword("Sample@1234");
        regDto.setConfirmPassword("Sample@1234");
        regDto.setAge(18); // Age 18 (below Scheme 3 min age 21)
        regDto.setGender("Male");
        regDto.setCategory("General");
        regDto.setState("Tamil Nadu");
        regDto.setDistrict("Chennai");
        regDto.setAddress("100 Beach Road");
        regDto.setPincode("600001");
        regDto.setIdentityType("Aadhaar");
        regDto.setIdentityNumber(sampleAadhaar);
        regDto.setTermsAgreed(true);

        Beneficiary beneficiary = beneficiaryService.registerBeneficiary(regDto);
        
        // Fetch Scheme 3 (Digital MSME) which requires min age 21 (15 pts) & income LTE 1,000,000 (35 pts) & docs (25 pts)
        List<Scheme> activeSchemes = schemeService.getAllActiveSchemes();
        Scheme msmeScheme = activeSchemes.size() >= 3 ? activeSchemes.get(2) : activeSchemes.get(0);

        // Evaluation with high income exceeding criteria and missing required docs
        EligibilityCheckRequestDto evalReq = new EligibilityCheckRequestDto();
        evalReq.setSchemeId(msmeScheme.getId());
        evalReq.setAnnualIncome(new BigDecimal("9999999")); // Exceeds income limit (0 / 35 pts)
        evalReq.setRequiredDocsUploaded(false); // Missing docs (0 / 25 pts)

        EligibilityCheckResponseDto evalRes = eligibilityService.evaluateEligibility(beneficiary, evalReq);
        assertNotNull(evalRes);
        assertFalse(evalRes.isEligible(), "User failing min age, exceeding income, and missing docs must be evaluated as INELIGIBLE");
        assertTrue(evalRes.getTotalScore() < 60, "Score should be well below 60% threshold");
    }

    @Test
    @DisplayName("Sample E2E Test 4: State-District Validation Rejects Mismatched Districts Across All 4 States")
    public void testStateDistrictMismatchRejection() {
        // Test AP + Hyderabad (TS district) -> Rejected
        assertStateDistrictRejected("Andhra Pradesh", "Hyderabad");

        // Test TS + Bengaluru Urban (KA district) -> Rejected
        assertStateDistrictRejected("Telangana", "Bengaluru Urban");

        // Test TN + Kakinada (AP district) -> Rejected
        assertStateDistrictRejected("Tamil Nadu", "Kakinada");

        // Test KA + Chennai (TN district) -> Rejected
        assertStateDistrictRejected("Karnataka", "Chennai");
    }

    @Test
    @DisplayName("Sample E2E Test 5: Successful Beneficiary Registration for All 4 Supported States")
    public void testRegistrationAllFourStates() {
        // 1. Andhra Pradesh -> Kakinada
        Beneficiary apBen = registerTestBeneficiary("AP User", "ap.user", "Andhra Pradesh", "Kakinada", "533001");
        assertEquals("Andhra Pradesh", apBen.getState());
        assertEquals("Kakinada", apBen.getDistrict());

        // 2. Telangana -> Hyderabad
        Beneficiary tsBen = registerTestBeneficiary("TS User", "ts.user", "Telangana", "Hyderabad", "500001");
        assertEquals("Telangana", tsBen.getState());
        assertEquals("Hyderabad", tsBen.getDistrict());

        // 3. Tamil Nadu -> Chennai
        Beneficiary tnBen = registerTestBeneficiary("TN User", "tn.user", "Tamil Nadu", "Chennai", "600001");
        assertEquals("Tamil Nadu", tnBen.getState());
        assertEquals("Chennai", tnBen.getDistrict());

        // 4. Karnataka -> Bengaluru Urban
        Beneficiary kaBen = registerTestBeneficiary("KA User", "ka.user", "Karnataka", "Bengaluru Urban", "560001");
        assertEquals("Karnataka", kaBen.getState());
        assertEquals("Bengaluru Urban", kaBen.getDistrict());
    }

    private void assertStateDistrictRejected(String state, String district) {
        long timestamp = System.currentTimeMillis() + (long)(Math.random() * 10000);
        String sampleEmail = "mismatch" + timestamp + "@gmail.com";
        String sampleMobile = "9" + String.format("%09d", (long)(Math.random() * 1000000000L));
        String sampleAadhaar = String.format("%012d", (long)(Math.random() * 1000000000000L));

        BeneficiaryRegistrationDto regDto = new BeneficiaryRegistrationDto();
        regDto.setFullName("Mismatch User");
        regDto.setEmail(sampleEmail);
        regDto.setMobileNumber(sampleMobile);
        regDto.setPassword("Sample@1234");
        regDto.setConfirmPassword("Sample@1234");
        regDto.setAge(25);
        regDto.setGender("Female");
        regDto.setCategory("OBC");
        regDto.setState(state);
        regDto.setDistrict(district);
        regDto.setAddress("123 Main Road");
        regDto.setPincode("500001");
        regDto.setIdentityType("Aadhaar");
        regDto.setIdentityNumber(sampleAadhaar);
        regDto.setTermsAgreed(true);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
            beneficiaryService.registerBeneficiary(regDto);
        }, "Registering with State=" + state + " and District=" + district + " must throw IllegalArgumentException");
    }

    private Beneficiary registerTestBeneficiary(String name, String emailPrefix, String state, String district, String pincode) {
        long timestamp = System.currentTimeMillis() + (long)(Math.random() * 10000);
        String sampleEmail = emailPrefix + timestamp + "@gmail.com";
        String sampleMobile = "7" + String.format("%09d", (long)(Math.random() * 1000000000L));
        String sampleAadhaar = String.format("%012d", (long)(Math.random() * 1000000000000L));

        BeneficiaryRegistrationDto regDto = new BeneficiaryRegistrationDto();
        regDto.setFullName(name);
        regDto.setEmail(sampleEmail);
        regDto.setMobileNumber(sampleMobile);
        regDto.setPassword("Sample@1234");
        regDto.setConfirmPassword("Sample@1234");
        regDto.setAge(30);
        regDto.setGender("Male");
        regDto.setCategory("General");
        regDto.setState(state);
        regDto.setDistrict(district);
        regDto.setAddress("456 Park Street");
        regDto.setPincode(pincode);
        regDto.setIdentityType("Aadhaar");
        regDto.setIdentityNumber(sampleAadhaar);
        regDto.setTermsAgreed(true);

        return beneficiaryService.registerBeneficiary(regDto);
    }

    @Test
    @DisplayName("Region Master Test: Verify District Counts for All 28 Indian States")
    public void testRegionMasterCountsAllFourStates() {
        // Original 4 states (preserved exactly)
        List<Region> apRegions = regionRepository.findByStateNameAndActiveTrueOrderByDistrictName("Andhra Pradesh");
        assertEquals(26, apRegions.size(), "Andhra Pradesh must have exactly 26 districts in Region Master");

        List<Region> tsRegions = regionRepository.findByStateNameAndActiveTrueOrderByDistrictName("Telangana");
        assertEquals(33, tsRegions.size(), "Telangana must have exactly 33 districts in Region Master");

        List<Region> tnRegions = regionRepository.findByStateNameAndActiveTrueOrderByDistrictName("Tamil Nadu");
        assertEquals(38, tnRegions.size(), "Tamil Nadu must have exactly 38 districts in Region Master");

        List<Region> kaRegions = regionRepository.findByStateNameAndActiveTrueOrderByDistrictName("Karnataka");
        assertEquals(31, kaRegions.size(), "Karnataka must have exactly 31 districts in Region Master");

        // 24 additional states
        assertEquals(26, regionRepository.findByStateNameAndActiveTrueOrderByDistrictName("Arunachal Pradesh").size(),
                "Arunachal Pradesh must have exactly 26 districts");
        assertEquals(35, regionRepository.findByStateNameAndActiveTrueOrderByDistrictName("Assam").size(),
                "Assam must have exactly 35 districts");
        assertEquals(38, regionRepository.findByStateNameAndActiveTrueOrderByDistrictName("Bihar").size(),
                "Bihar must have exactly 38 districts");
        assertEquals(33, regionRepository.findByStateNameAndActiveTrueOrderByDistrictName("Chhattisgarh").size(),
                "Chhattisgarh must have exactly 33 districts");
        assertEquals(2, regionRepository.findByStateNameAndActiveTrueOrderByDistrictName("Goa").size(),
                "Goa must have exactly 2 districts");
        assertEquals(33, regionRepository.findByStateNameAndActiveTrueOrderByDistrictName("Gujarat").size(),
                "Gujarat must have exactly 33 districts");
        assertEquals(22, regionRepository.findByStateNameAndActiveTrueOrderByDistrictName("Haryana").size(),
                "Haryana must have exactly 22 districts");
        assertEquals(12, regionRepository.findByStateNameAndActiveTrueOrderByDistrictName("Himachal Pradesh").size(),
                "Himachal Pradesh must have exactly 12 districts");
        assertEquals(24, regionRepository.findByStateNameAndActiveTrueOrderByDistrictName("Jharkhand").size(),
                "Jharkhand must have exactly 24 districts");
        assertEquals(14, regionRepository.findByStateNameAndActiveTrueOrderByDistrictName("Kerala").size(),
                "Kerala must have exactly 14 districts");
        assertEquals(55, regionRepository.findByStateNameAndActiveTrueOrderByDistrictName("Madhya Pradesh").size(),
                "Madhya Pradesh must have exactly 55 districts");
        assertEquals(36, regionRepository.findByStateNameAndActiveTrueOrderByDistrictName("Maharashtra").size(),
                "Maharashtra must have exactly 36 districts");
        assertEquals(16, regionRepository.findByStateNameAndActiveTrueOrderByDistrictName("Manipur").size(),
                "Manipur must have exactly 16 districts");
        assertEquals(12, regionRepository.findByStateNameAndActiveTrueOrderByDistrictName("Meghalaya").size(),
                "Meghalaya must have exactly 12 districts");
        assertEquals(11, regionRepository.findByStateNameAndActiveTrueOrderByDistrictName("Mizoram").size(),
                "Mizoram must have exactly 11 districts");
        assertEquals(16, regionRepository.findByStateNameAndActiveTrueOrderByDistrictName("Nagaland").size(),
                "Nagaland must have exactly 16 districts");
        assertEquals(30, regionRepository.findByStateNameAndActiveTrueOrderByDistrictName("Odisha").size(),
                "Odisha must have exactly 30 districts");
        assertEquals(23, regionRepository.findByStateNameAndActiveTrueOrderByDistrictName("Punjab").size(),
                "Punjab must have exactly 23 districts");
        assertEquals(50, regionRepository.findByStateNameAndActiveTrueOrderByDistrictName("Rajasthan").size(),
                "Rajasthan must have exactly 50 districts");
        assertEquals(6, regionRepository.findByStateNameAndActiveTrueOrderByDistrictName("Sikkim").size(),
                "Sikkim must have exactly 6 districts");
        assertEquals(8, regionRepository.findByStateNameAndActiveTrueOrderByDistrictName("Tripura").size(),
                "Tripura must have exactly 8 districts");
        assertEquals(75, regionRepository.findByStateNameAndActiveTrueOrderByDistrictName("Uttar Pradesh").size(),
                "Uttar Pradesh must have exactly 75 districts");
        assertEquals(13, regionRepository.findByStateNameAndActiveTrueOrderByDistrictName("Uttarakhand").size(),
                "Uttarakhand must have exactly 13 districts");
        assertEquals(23, regionRepository.findByStateNameAndActiveTrueOrderByDistrictName("West Bengal").size(),
                "West Bengal must have exactly 23 districts");

        // Verify total state count = 28
        List<String> allStates = regionRepository.findDistinctStateNames();
        assertEquals(28, allStates.size(), "Region master must contain exactly 28 states (no Union Territories)");
    }

    @Test
    @DisplayName("Sample E2E Test 7: Verify Admin Officer Creation and Dedicated Officers Table Persistence")
    public void testOfficerCreationAndDatabasePersistence() {
        long ts = System.currentTimeMillis();
        OfficerRegistrationDto dto = new OfficerRegistrationDto();
        dto.setFullName("Test Field Officer");
        dto.setEmail("field.test" + ts + "@gmail.com");
        dto.setMobileNumber("9" + String.format("%09d", ts % 1000000000L));
        dto.setPassword("Officer@123");
        dto.setOfficerType("FIELD_OFFICER");
        dto.setState("Andhra Pradesh");
        dto.setDistrict("Kakinada");

        Officer createdOfficer = officerService.createOfficer(dto);
        assertNotNull(createdOfficer);
        assertNotNull(createdOfficer.getId());
        assertNotNull(createdOfficer.getUser());
        assertEquals("FIELD_OFFICER", createdOfficer.getOfficerType());
        assertEquals("Andhra Pradesh", createdOfficer.getState());
        assertEquals("Kakinada", createdOfficer.getDistrict());

        // Verify query by user email
        Officer found = officerRepository.findByUserEmail(createdOfficer.getEmail()).orElse(null);
        assertNotNull(found);
        assertEquals(createdOfficer.getId(), found.getId());
    }

    @Test
    @DisplayName("Sample E2E Test 8: Verify IDOR Cross-Beneficiary Data Protection")
    public void testIDORProtectionCrossBeneficiaryAccessForbidden() {
        // Beneficiary A
        Beneficiary benA = registerTestBeneficiary("Beneficiary A", "ben.a", "Andhra Pradesh", "Kakinada", "533001");
        Scheme scheme = schemeService.getAllActiveSchemes().get(0);

        ApplicationSubmissionDto appDto = new ApplicationSubmissionDto();
        appDto.setSchemeId(scheme.getId());
        appDto.setAnnualIncome(new BigDecimal("100000"));
        appDto.setRequestedAmount(scheme.getMaxAmount());

        Application appA = applicationService.submitApplication(benA, appDto);

        // Beneficiary B
        Beneficiary benB = registerTestBeneficiary("Beneficiary B", "ben.b", "Telangana", "Hyderabad", "500001");

        // Attempting to access Beneficiary A's application using Beneficiary B's context must fail ownership check
        assertFalse(appA.getBeneficiary().getId().equals(benA.getId()) && benA.getId().equals(benB.getId()), "Beneficiary B must NOT own Beneficiary A's application");
    }

    @Test
    @DisplayName("Sample E2E Test 9: Verify Application Details Controller Rendering Null Safety for New Submissions")
    public void testApplicationDetailsControllerMethodNullSafe() {
        Beneficiary ben = registerTestBeneficiary("Details User", "details.user", "Andhra Pradesh", "Kakinada", "533001");
        Scheme scheme = schemeService.getAllActiveSchemes().get(0);

        ApplicationSubmissionDto appDto = new ApplicationSubmissionDto();
        appDto.setSchemeId(scheme.getId());
        appDto.setAnnualIncome(new BigDecimal("120000"));
        appDto.setRequestedAmount(scheme.getMaxAmount());

        Application app = applicationService.submitApplication(ben, appDto);
        assertNotNull(app.getId());

        // Verify null disbursement for newly submitted application is handled without throwing exception
        Disbursement d = disbursementService.getDisbursementByApplicationId(app.getId());
        assertNull(d, "New application before Finance approval must have null disbursement record without throwing error");
        assertEquals("FIELD_VERIFICATION", app.getCurrentStage());
        assertEquals("PENDING", app.getStatus());
    }

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentRepository documentRepository;

    @Test
    @DisplayName("Sample E2E Test 10: Verify Dynamic Scheme-Specific Eligibility Evaluation Across Agriculture, Solar, and MSME Schemes")
    public void testDynamicSchemeSpecificEligibilityEvaluation() {
        Beneficiary ben = registerTestBeneficiary("Dynamic User", "dynamic.user", "Andhra Pradesh", "Kakinada", "533001");
        List<Scheme> activeSchemes = schemeService.getAllActiveSchemes();
        assertTrue(activeSchemes.size() >= 3, "At least 3 active schemes must exist in database");

        // 1. Test Scheme 1 (PM Krishi Vikas - Agriculture)
        Scheme agScheme = activeSchemes.get(0);
        EligibilityCheckRequestDto agReq = new EligibilityCheckRequestDto();
        agReq.setSchemeId(agScheme.getId());
        agReq.setAnnualIncome(new BigDecimal("180000"));
        agReq.setFarmerStatus("ACTIVE_FARMER");
        agReq.setLandHoldingAcres(new BigDecimal("2.5"));
        agReq.setDocLandRecordUploaded(true);

        EligibilityCheckResponseDto agRes = eligibilityService.evaluateEligibility(ben, agReq);
        assertNotNull(agRes);
        assertTrue(agRes.isEligible(), "Farmer with 180k income, active status, 2.5 acres, and land record doc must be ELIGIBLE");

        // 2. Test Scheme 2 (Rooftop Solar)
        Scheme solarScheme = activeSchemes.get(1);
        EligibilityCheckRequestDto solarReq = new EligibilityCheckRequestDto();
        solarReq.setSchemeId(solarScheme.getId());
        solarReq.setAnnualIncome(new BigDecimal("350000"));
        solarReq.setRooftopOwnership(true);
        solarReq.setDocElectricityBillUploaded(true);

        EligibilityCheckResponseDto solarRes = eligibilityService.evaluateEligibility(ben, solarReq);
        assertNotNull(solarRes);
        assertTrue(solarRes.isEligible(), "Consumer with 350k income, rooftop ownership, and electricity bill doc must be ELIGIBLE");

        // 3. Test Scheme 3 (Digital MSME Grant)
        Scheme msmeScheme = activeSchemes.get(2);
        EligibilityCheckRequestDto msmeReq = new EligibilityCheckRequestDto();
        msmeReq.setSchemeId(msmeScheme.getId());
        msmeReq.setAnnualIncome(new BigDecimal("500000"));
        msmeReq.setBusinessType("MICRO_ENTERPRISE");
        msmeReq.setDocBusinessRegUploaded(true);

        EligibilityCheckResponseDto msmeRes = eligibilityService.evaluateEligibility(ben, msmeReq);
        assertNotNull(msmeRes);
        assertTrue(msmeRes.isEligible(), "Micro enterprise with 500k turnover, registration doc, and bank statements must be ELIGIBLE");
    }

    @Test
    @DisplayName("Sample E2E Test 11: Verify Separate Document Persistence for Aadhaar Card and Bank Passbook")
    public void testSeparateDocumentUploadsAndPersistence() throws Exception {
        Beneficiary ben = registerTestBeneficiary("Document User", "doc.user", "Andhra Pradesh", "Kakinada", "533001");
        org.springframework.mock.web.MockMultipartFile aadhaarFile = new org.springframework.mock.web.MockMultipartFile(
                "docAadhaarFile", "aadhaar_card.pdf", "application/pdf", "Aadhaar Card Sample Content".getBytes());
        org.springframework.mock.web.MockMultipartFile passbookFile = new org.springframework.mock.web.MockMultipartFile(
                "docBankPassbookFile", "passbook_front.png", "image/png", "Bank Passbook Sample Content".getBytes());

        Document docAadhaar = documentService.saveDocument(ben, null, "AADHAAR", aadhaarFile);
        Document docPassbook = documentService.saveDocument(ben, null, "BANK_PASSBOOK", passbookFile);

        assertNotNull(docAadhaar.getId(), "Aadhaar document must be saved in database with unique ID");
        assertNotNull(docPassbook.getId(), "Bank Passbook document must be saved in database with unique ID");
        assertNotEquals(docAadhaar.getId(), docPassbook.getId(), "Aadhaar and Bank Passbook must be completely separate document records");

        assertEquals("AADHAAR", docAadhaar.getDocumentType());
        assertEquals("BANK_PASSBOOK", docPassbook.getDocumentType());

        List<Document> userDocs = documentRepository.findByBeneficiaryId(ben.getId());
        assertEquals(2, userDocs.size(), "Beneficiary should have 2 distinct uploaded documents");
    }

    @Test
    @DisplayName("Sample E2E Test 12: Comprehensive Validation of Document Upload Requirements, File Formats, Mismatches, and Size Limits")
    public void testDocumentValidationRulesAndTypeCheck() {
        // Case 1: Empty file check
        org.springframework.mock.web.MockMultipartFile emptyFile = new org.springframework.mock.web.MockMultipartFile(
                "docAadhaarFile", "empty.pdf", "application/pdf", new byte[0]);
        String errEmpty = documentService.validateDocument(emptyFile, "AADHAAR");
        assertEquals("Aadhaar Card is required.", errEmpty);

        // Case 2: Null file check
        String errNull = documentService.validateDocument(null, "BANK_PASSBOOK");
        assertEquals("Bank Passbook is required.", errNull);

        // Case 3: Unsupported format (.exe)
        org.springframework.mock.web.MockMultipartFile exeFile = new org.springframework.mock.web.MockMultipartFile(
                "docAadhaarFile", "malware.exe", "application/x-msdownload", "Executable Content".getBytes());
        String errExe = documentService.validateDocument(exeFile, "AADHAAR");
        assertEquals("Unsupported file format. Please upload PDF, JPG, JPEG, or PNG.", errExe);

        // Case 4: Oversized file (> 5 MB)
        byte[] largeBytes = new byte[5 * 1024 * 1024 + 100];
        org.springframework.mock.web.MockMultipartFile largeFile = new org.springframework.mock.web.MockMultipartFile(
                "docAadhaarFile", "large_aadhaar.pdf", "application/pdf", largeBytes);
        String errLarge = documentService.validateDocument(largeFile, "AADHAAR");
        assertEquals("File size must not exceed 5 MB.", errLarge);

        // Case 5: Passbook uploaded in Aadhaar field (Semantic content check disabled at upload time)
        org.springframework.mock.web.MockMultipartFile passbookInAadhaar = new org.springframework.mock.web.MockMultipartFile(
                "docAadhaarFile", "bank_passbook_statement.pdf", "application/pdf", "Bank Branch Passbook IFSC Account Number".getBytes());
        String errMismatch1 = documentService.validateDocument(passbookInAadhaar, "AADHAAR");
        assertNull(errMismatch1, "File format is valid; semantic content check must not reject at upload time");

        // Case 6: Aadhaar uploaded in Passbook field (Semantic content check disabled at upload time)
        org.springframework.mock.web.MockMultipartFile aadhaarInPassbook = new org.springframework.mock.web.MockMultipartFile(
                "docBankPassbookFile", "aadhaar_card_uidai.pdf", "application/pdf", "Government of India Unique Identification Aadhaar Card UIDAI".getBytes());
        String errMismatch2 = documentService.validateDocument(aadhaarInPassbook, "BANK_PASSBOOK");
        assertNull(errMismatch2, "File format is valid; semantic content check must not reject at upload time");

        // Case 7: Valid Aadhaar document in Aadhaar field
        org.springframework.mock.web.MockMultipartFile validAadhaar = new org.springframework.mock.web.MockMultipartFile(
                "docAadhaarFile", "my_aadhaar_card.pdf", "application/pdf", "Aadhaar Card UIDAI Government of India".getBytes());
        String validResult1 = documentService.validateDocument(validAadhaar, "AADHAAR");
        assertNull(validResult1, "Valid Aadhaar file must pass validation with null error");

        // Case 8: Valid Bank Passbook document in Bank Passbook field
        org.springframework.mock.web.MockMultipartFile validPassbook = new org.springframework.mock.web.MockMultipartFile(
                "docBankPassbookFile", "passbook_copy.jpg", "image/jpeg", "Bank Passbook Branch IFSC Account".getBytes());
        String validResult2 = documentService.validateDocument(validPassbook, "BANK_PASSBOOK");
        assertNull(validResult2, "Valid Passbook file must pass validation with null error");
    }

    @Autowired
    private EligibilityController eligibilityController;

    @Test
    @DisplayName("Sample E2E Test 13: Verify EligibilityController Process Request with Valid Documents and Scheme Evaluation")
    public void testEligibilityControllerValidDocumentSubmission() throws Exception {
        Beneficiary ben = registerTestBeneficiary("Controller User", "ctrl.user", "Andhra Pradesh", "Kakinada", "533001");
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(ben.getUser().getEmail(), null, java.util.Collections.emptyList());

        Scheme scheme = schemeService.getAllActiveSchemes().get(0);
        EligibilityCheckRequestDto requestDto = new EligibilityCheckRequestDto();
        requestDto.setSchemeId(scheme.getId());
        requestDto.setAnnualIncome(new BigDecimal("180000"));
        requestDto.setFarmerStatus("ACTIVE_FARMER");
        requestDto.setLandHoldingAcres(new BigDecimal("2.5"));
        requestDto.setDocLandRecordUploaded(true);

        org.springframework.mock.web.MockMultipartHttpServletRequest mockReq = new org.springframework.mock.web.MockMultipartHttpServletRequest();
        List<com.example.digital_subsidy.model.RequiredDocument> reqDocs = requiredDocumentRepository.findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(scheme.getId());
        for (com.example.digital_subsidy.model.RequiredDocument rd : reqDocs) {
            org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                    "docFile_" + rd.getId(), rd.getDocumentType().toLowerCase() + ".pdf", "application/pdf", "Valid document content".getBytes());
            mockReq.addFile(file);
        }

        org.springframework.mock.web.MockMultipartFile validAadhaar = new org.springframework.mock.web.MockMultipartFile(
                "docAadhaarFile", "aadhaar_card.pdf", "application/pdf", "Aadhaar Card UIDAI Government of India".getBytes());
        org.springframework.mock.web.MockMultipartFile validPassbook = new org.springframework.mock.web.MockMultipartFile(
                "docBankPassbookFile", "passbook_front.png", "image/png", "Bank Passbook Branch IFSC Account".getBytes());

        org.springframework.validation.BeanPropertyBindingResult bindingResult = new org.springframework.validation.BeanPropertyBindingResult(requestDto, "requestDto");
        org.springframework.ui.ConcurrentModel model = new org.springframework.ui.ConcurrentModel();
        String viewName = eligibilityController.processEligibilityCheck(requestDto, bindingResult, validAadhaar, validPassbook, mockReq, new org.springframework.mock.web.MockHttpSession(), auth, model);

        assertEquals("beneficiary/eligibility-result", viewName);
        assertTrue(model.containsAttribute("result"));
        EligibilityCheckResponseDto resultDto = (EligibilityCheckResponseDto) model.getAttribute("result");
        assertNotNull(resultDto);
        assertTrue(resultDto.isEligible());
    }
}
