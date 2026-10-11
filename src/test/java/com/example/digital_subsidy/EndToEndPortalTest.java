package com.example.digital_subsidy;

import com.example.digital_subsidy.dto.ApplicationSubmissionDto;
import com.example.digital_subsidy.dto.BeneficiaryRegistrationDto;
import com.example.digital_subsidy.dto.EligibilityCheckRequestDto;
import com.example.digital_subsidy.dto.EligibilityCheckResponseDto;
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
public class EndToEndPortalTest {

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
    private BeneficiaryRepository beneficiaryRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DocumentService documentService;

    @Test
    @DisplayName("Test 1: Full E2E Positive Workflow - Registration to Milestone Release")
    public void testFullEndToEndWorkflow() throws Exception {
        long timestamp = System.currentTimeMillis();
        String testEmail = "audit" + timestamp + "@gmail.com";
        String testMobile = "9" + String.format("%09d", timestamp % 1000000000L);
        String testAadhaar = String.format("%012d", timestamp % 1000000000000L);

        // 1. Beneficiary Registration
        BeneficiaryRegistrationDto regDto = new BeneficiaryRegistrationDto();
        regDto.setFullName("Audit Citizen");
        regDto.setEmail(testEmail);
        regDto.setMobileNumber(testMobile);
        regDto.setPassword("Citizen@123");
        regDto.setConfirmPassword("Citizen@123");
        regDto.setAge(30);
        regDto.setGender("Male");
        regDto.setCategory("General");
        regDto.setState("Andhra Pradesh");
        regDto.setDistrict("Kakinada");
        regDto.setAddress("Door 12-3 Main Road");
        regDto.setPincode("533001");
        regDto.setIdentityType("Aadhaar");
        regDto.setIdentityNumber(testAadhaar);
        regDto.setTermsAgreed(true);

        Beneficiary beneficiary = beneficiaryService.registerBeneficiary(regDto);
        assertNotNull(beneficiary.getId(), "Beneficiary ID should be generated");
        assertEquals(testEmail, beneficiary.getUser().getEmail());
        assertTrue(passwordEncoder.matches("Citizen@123", beneficiary.getUser().getPassword()), "Password should be BCrypt encoded");
        assertEquals(testMobile, beneficiary.getUser().getMobileNumber(), "Mobile should be strictly 10 raw digits");

        // 2. Fetch Active Schemes
        List<Scheme> activeSchemes = schemeService.getAllActiveSchemes();
        assertFalse(activeSchemes.isEmpty(), "Active schemes list should not be empty");
        Scheme scheme = activeSchemes.get(0);

        // 3. Eligibility Check
        EligibilityCheckRequestDto evalReq = new EligibilityCheckRequestDto();
        evalReq.setSchemeId(scheme.getId());
        evalReq.setAnnualIncome(new BigDecimal("150000"));
        evalReq.setRequiredDocsUploaded(true);

        EligibilityCheckResponseDto evalRes = eligibilityService.evaluateEligibility(beneficiary, evalReq);
        assertTrue(evalRes.isEligible(), "Citizen should be eligible for the scheme");
        assertTrue(evalRes.getTotalScore() >= 60, "Eligibility score percentage should be >= 60%");

        // 4. Submit Application
        ApplicationSubmissionDto appDto = new ApplicationSubmissionDto();
        appDto.setSchemeId(scheme.getId());
        appDto.setAnnualIncome(new BigDecimal("150000"));
        appDto.setRequestedAmount(scheme.getMaxAmount());

        Application application = applicationService.submitApplication(beneficiary, appDto);
        assertNotNull(application.getId(), "Application ID should be persisted");
        assertEquals("FIELD_VERIFICATION", application.getCurrentStage());
        assertEquals("PENDING", application.getStatus());

        // 5. Field Officer Verification (Approve)
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
        fieldAction.setRemarks("Ground inspection verified successfully");

        verificationService.processVerification("field.officer@gmail.com", "ROLE_FIELD_OFFICER", fieldAction);

        Application fieldApprovedApp = applicationService.getApplicationById(application.getId());
        assertEquals("DISTRICT_REVIEW", fieldApprovedApp.getCurrentStage(), "Stage should move to DISTRICT_REVIEW");
        assertEquals("PENDING", fieldApprovedApp.getStatus());

        // 6. District Officer Review (Approve)
        VerificationActionDto districtAction = new VerificationActionDto();
        districtAction.setApplicationId(application.getId());
        districtAction.setAction("APPROVE");
        districtAction.setRemarks("District collectorate sanction approved");

        verificationService.processVerification("kalyanramvase377@gmail.com", "ROLE_DISTRICT_OFFICER", districtAction);

        Application districtApprovedApp = applicationService.getApplicationById(application.getId());
        assertEquals("FINANCE_APPROVAL", districtApprovedApp.getCurrentStage(), "Stage should move to FINANCE_APPROVAL");
        assertEquals("PENDING", districtApprovedApp.getStatus());

        // 7. Finance Approver (Approve & Milestone Disbursement Generation)
        VerificationActionDto financeAction = new VerificationActionDto();
        financeAction.setApplicationId(application.getId());
        financeAction.setAction("APPROVE");
        financeAction.setRemarks("Treasury sanction approved for milestone disbursement");

        verificationService.processVerification("pabbuletihari0@gmail.com", "ROLE_FINANCE_APPROVER", financeAction);

        Application financeApprovedApp = applicationService.getApplicationById(application.getId());
        assertEquals("DISBURSEMENT", financeApprovedApp.getCurrentStage());
        assertEquals("APPROVED", financeApprovedApp.getStatus());

        // 8. Verify Disbursement & Staged Milestones in MySQL
        Disbursement disbursement = disbursementService.getDisbursementByApplicationId(application.getId());
        assertNotNull(disbursement, "Disbursement record should be created in MySQL");
        assertEquals("IN_PROGRESS", disbursement.getStatus());

        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        assertEquals(3, milestones.size(), "3 Staged milestones should be automatically generated");

        // 9. Release Milestone 1
        Milestone m1 = milestones.get(0);
        m1.setComplianceVerificationStatus("VERIFIED");
        milestoneRepository.save(m1);
        disbursementService.releaseMilestone(m1.getId());

        Milestone releasedM1 = milestoneRepository.findById(m1.getId()).orElseThrow();
        assertEquals("COMPLETED", releasedM1.getCompletionStatus());
        assertNotNull(releasedM1.getReleasedAmount());

        // 10. Audit Log & Report Statistics Verification
        Map<String, Object> stats = reportService.getSystemStatistics();
        assertTrue(((Number) stats.get("registeredBeneficiaries")).longValue() > 0);
        assertTrue(((Number) stats.get("applicationsProcessed")).longValue() > 0);

        long auditCount = auditLogRepository.count();
        assertTrue(auditCount > 0, "Audit logs should record every key transaction step");
    }

    @Test
    @DisplayName("Test 2: Rejection Workflow Path")
    public void testRejectionWorkflow() {
        long timestamp = System.currentTimeMillis() + 1;
        String testEmail = "reject" + timestamp + "@gmail.com";
        String testMobile = "8" + String.format("%09d", timestamp % 1000000000L);

        // Beneficiary Registration
        BeneficiaryRegistrationDto regDto = new BeneficiaryRegistrationDto();
        regDto.setFullName("Reject Citizen");
        regDto.setEmail(testEmail);
        regDto.setMobileNumber(testMobile);
        regDto.setPassword("Citizen@123");
        regDto.setConfirmPassword("Citizen@123");
        regDto.setAge(25);
        regDto.setGender("Female");
        regDto.setCategory("OBC");
        regDto.setState("Andhra Pradesh");
        regDto.setDistrict("Guntur");
        regDto.setAddress("Street 42");
        regDto.setPincode("522002");
        regDto.setIdentityType("PAN");
        regDto.setIdentityNumber("ABCDE" + String.format("%04d", (int)(timestamp % 10000)) + "F");
        regDto.setTermsAgreed(true);

        Beneficiary beneficiary = beneficiaryService.registerBeneficiary(regDto);
        Scheme scheme = schemeService.getAllActiveSchemes().get(0);

        ApplicationSubmissionDto appDto = new ApplicationSubmissionDto();
        appDto.setSchemeId(scheme.getId());
        appDto.setAnnualIncome(new BigDecimal("200000"));
        appDto.setRequestedAmount(scheme.getMaxAmount());

        Application application = applicationService.submitApplication(beneficiary, appDto);

        // Field Officer Reject Action
        VerificationActionDto rejectAction = new VerificationActionDto();
        rejectAction.setApplicationId(application.getId());
        rejectAction.setAction("REJECT");
        rejectAction.setRemarks("Field inspection failed: Discrepancy in address proof");

        verificationService.processVerification("field.officer@gmail.com", "ROLE_FIELD_OFFICER", rejectAction);

        Application rejectedApp = applicationService.getApplicationById(application.getId());
        assertEquals("REJECTED", rejectedApp.getStatus());
        assertEquals("REJECTED", rejectedApp.getCurrentStage());
        assertNotNull(rejectedApp.getRejectionReason());
    }
}
