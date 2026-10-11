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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class EducationSubsidyWorkflowTest {

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
    private DocumentService documentService;

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private EligibilityCriteriaRepository criteriaRepository;

    @Autowired
    private RequiredDocumentRepository requiredDocumentRepository;

    @Autowired
    private ApplicationEligibilityAnswerRepository answerRepository;

    @Autowired
    private BeneficiaryRepository beneficiaryRepository;

    @Test
    @DisplayName("Test 1: Verify Education Subsidy Seeding & Idempotency")
    public void testEducationSubsidySeeding() {
        List<Scheme> educationSchemes = schemeRepository.findAll().stream()
                .filter(s -> "Education".equalsIgnoreCase(s.getCategory()) || s.getName().toLowerCase().contains("education"))
                .toList();

        assertFalse(educationSchemes.isEmpty(), "Education Subsidy scheme must be present in database");
        Scheme eduScheme = educationSchemes.get(0);

        assertEquals("ACTIVE", eduScheme.getStatus());
        assertEquals(60, eduScheme.getMinQualifyingScore());

        List<EligibilityCriteria> criteria = criteriaRepository.findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(eduScheme.getId());
        assertTrue(criteria.size() >= 8, "Should have at least 8 Education eligibility criteria configured");

        // Verify mandatory Annual Family Income criteria exists
        boolean hasIncomeCriterion = criteria.stream().anyMatch(c -> "MAX_INCOME".equalsIgnoreCase(c.getCriterionName()));
        assertTrue(hasIncomeCriterion, "MAX_INCOME (Annual Family Income) criterion must exist for Education Subsidy");

        List<RequiredDocument> reqDocs = requiredDocumentRepository.findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(eduScheme.getId());
        assertTrue(reqDocs.size() >= 6, "Should have at least 6 Education required documents configured");

        // Verify Income Certificate is present
        boolean hasIncomeCert = reqDocs.stream().anyMatch(d -> "INCOME_CERTIFICATE".equalsIgnoreCase(d.getDocumentType()));
        assertTrue(hasIncomeCert, "Income Certificate must be a required document for Education Subsidy");
    }

    @Test
    @DisplayName("Test 2: Full End-to-End Education Subsidy Workflow")
    public void testEducationSubsidyFullWorkflow() throws Exception {
        long timestamp = System.currentTimeMillis();
        String studentEmail = "student" + timestamp + "@gmail.com";
        String studentMobile = "9" + String.format("%09d", timestamp % 1000000000L);
        String studentAadhaar = String.format("%012d", timestamp % 1000000000000L);

        // 1. Beneficiary Registration (Student)
        BeneficiaryRegistrationDto regDto = new BeneficiaryRegistrationDto();
        regDto.setFullName("Ananya Student");
        regDto.setEmail(studentEmail);
        regDto.setMobileNumber(studentMobile);
        regDto.setPassword("Student@123");
        regDto.setConfirmPassword("Student@123");
        regDto.setAge(20);
        regDto.setGender("Female");
        regDto.setCategory("OBC");
        regDto.setState("Andhra Pradesh");
        regDto.setDistrict("Kakinada");
        regDto.setAddress("College Road 45");
        regDto.setPincode("533003");
        regDto.setIdentityType("Aadhaar");
        regDto.setIdentityNumber(studentAadhaar);
        regDto.setTermsAgreed(true);

        Beneficiary beneficiary = beneficiaryService.registerBeneficiary(regDto);
        assertNotNull(beneficiary.getId(), "Student beneficiary should be registered");

        // 2. Fetch Education Subsidy
        Scheme eduScheme = schemeRepository.findAll().stream()
                .filter(s -> "Education".equalsIgnoreCase(s.getCategory()) || s.getName().toLowerCase().contains("education"))
                .findFirst().orElseThrow();

        List<EligibilityCriteria> criteria = criteriaRepository.findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(eduScheme.getId());
        List<RequiredDocument> reqDocs = requiredDocumentRepository.findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(eduScheme.getId());

        // 3. Prepare Answers & Evaluate Eligibility
        EligibilityCheckRequestDto checkReq = new EligibilityCheckRequestDto();
        checkReq.setSchemeId(eduScheme.getId());
        checkReq.setAnnualIncome(new BigDecimal("150000")); // Within ₹3,00,000 ceiling

        Map<Long, String> criterionAnswers = new HashMap<>();
        Map<Long, String> questionSnapshots = new HashMap<>();
        Map<Long, Integer> displayOrders = new HashMap<>();

        for (EligibilityCriteria c : criteria) {
            String ans = "YES";
            if ("MAX_INCOME".equalsIgnoreCase(c.getCriterionName())) {
                ans = "150000";
            } else if ("ACADEMIC_PERFORMANCE".equalsIgnoreCase(c.getCriterionName())) {
                ans = "85.5";
            } else if ("EDUCATION_LEVEL".equalsIgnoreCase(c.getCriterionName())) {
                ans = "Undergraduate";
            } else if ("INSTITUTION_TYPE".equalsIgnoreCase(c.getCriterionName())) {
                ans = "Government";
            } else if ("STUDY_YEAR".equalsIgnoreCase(c.getCriterionName())) {
                ans = "2nd Year";
            } else if ("COURSE_DURATION".equalsIgnoreCase(c.getCriterionName())) {
                ans = "4";
            } else if ("SCHOLARSHIP_STATUS".equalsIgnoreCase(c.getCriterionName())) {
                ans = "NO";
            }
            criterionAnswers.put(c.getId(), ans);
            questionSnapshots.put(c.getId(), c.getQuestionText());
            displayOrders.put(c.getId(), c.getDisplayOrder());
        }
        checkReq.setCriterionAnswers(criterionAnswers);

        EligibilityCheckResponseDto checkRes = eligibilityService.evaluateEligibility(beneficiary, checkReq);
        assertTrue(checkRes.isEligible(), "Student meeting criteria must be ELIGIBLE");
        assertTrue(checkRes.getTotalScore() >= eduScheme.getMinQualifyingScore(), "Score must meet minimum threshold");

        // 4. Upload Required Documents
        for (RequiredDocument rd : reqDocs) {
            MockMultipartFile mockFile = new MockMultipartFile(
                    "docFile_" + rd.getId(),
                    rd.getDocumentType().toLowerCase() + "_sample.pdf",
                    "application/pdf",
                    ("Sample content for " + rd.getDocumentName()).getBytes()
            );
            documentService.saveDocument(beneficiary, null, rd.getDocumentType(), mockFile);
        }

        List<Document> uploadedDocs = documentService.getDocumentsByBeneficiary(beneficiary.getId());
        assertEquals(reqDocs.size(), uploadedDocs.size(), "All required documents should be uploaded");
        for (Document d : uploadedDocs) {
            assertEquals("PENDING", d.getVerificationStatus(), "Initial doc status must be PENDING");
        }

        // 5. Submit Application
        ApplicationSubmissionDto appDto = new ApplicationSubmissionDto();
        appDto.setSchemeId(eduScheme.getId());
        appDto.setRequestedAmount(new BigDecimal("50000"));
        appDto.setAnnualIncome(new BigDecimal("150000"));
        appDto.setCriterionAnswers(criterionAnswers);
        appDto.setCriterionQuestionSnapshots(questionSnapshots);
        appDto.setCriterionDisplayOrders(displayOrders);

        Application application = applicationService.submitApplication(beneficiary, appDto);
        assertNotNull(application.getId(), "Application should be created");
        assertEquals("FIELD_VERIFICATION", application.getCurrentStage());
        assertEquals("PENDING", application.getStatus());

        // Verify stored answers snapshot
        List<ApplicationEligibilityAnswer> savedAnswers = answerRepository.findByApplicationIdOrderByDisplayOrderAscIdAsc(application.getId());
        assertEquals(criteria.size(), savedAnswers.size(), "All eligibility answers must be snapshotted");

        // 6. Field Officer Document Review & Approval
        List<Document> appDocs = documentService.getDocumentsByApplication(application.getId());
        assertFalse(appDocs.isEmpty(), "Application documents must be linked");

        VerificationActionDto foAction = new VerificationActionDto();
        foAction.setApplicationId(application.getId());
        foAction.setAction("APPROVE");
        foAction.setRemarks("Physical ground verification and student document inspection completed successfully.");

        Map<Long, String> docStatuses = new HashMap<>();
        Map<Long, String> docRemarks = new HashMap<>();
        for (Document d : appDocs) {
            docStatuses.put(d.getId(), "VERIFIED");
            docRemarks.put(d.getId(), "Document verified authentic");
        }
        foAction.setDocumentVerificationStatuses(docStatuses);
        foAction.setDocumentVerificationRemarks(docRemarks);

        verificationService.processVerification("field.officer@gmail.com", "ROLE_FIELD_OFFICER", foAction);

        Application foApp = applicationService.getApplicationById(application.getId());
        assertEquals("DISTRICT_REVIEW", foApp.getCurrentStage(), "App approved by Field Officer must transition to DISTRICT_REVIEW");

        // 7. District Officer Sanction
        VerificationActionDto doAction = new VerificationActionDto();
        doAction.setApplicationId(application.getId());
        doAction.setAction("APPROVE");
        doAction.setRemarks("Sanctioned by District Collectorate after verifying student credentials and income certificate.");

        verificationService.processVerification("kalyanramvase377@gmail.com", "ROLE_DISTRICT_OFFICER", doAction);

        Application doApp = applicationService.getApplicationById(application.getId());
        assertEquals("FINANCE_APPROVAL", doApp.getCurrentStage(), "App approved by District Officer must transition to FINANCE_APPROVAL");
    }

    @Test
    @DisplayName("Test 3: Annual Family Income Validation (Exceeding Ceiling & Negative Values)")
    public void testAnnualFamilyIncomeValidation() {
        Scheme eduScheme = schemeRepository.findAll().stream()
                .filter(s -> "Education".equalsIgnoreCase(s.getCategory()) || s.getName().toLowerCase().contains("education"))
                .findFirst().orElseThrow();

        List<Beneficiary> bens = beneficiaryRepository.findAll();
        assertFalse(bens.isEmpty(), "Beneficiary repository should contain seeded beneficiary");
        Beneficiary beneficiary = bens.get(0);

        // High Income Exceeding ₹3,00,000 ceiling
        EligibilityCheckRequestDto highIncReq = new EligibilityCheckRequestDto();
        highIncReq.setSchemeId(eduScheme.getId());
        highIncReq.setAnnualIncome(new BigDecimal("500000")); // ₹5,00,000 > ₹3,00,000

        List<EligibilityCriteria> criteria = criteriaRepository.findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(eduScheme.getId());
        Map<Long, String> criterionAnswers = new HashMap<>();
        for (EligibilityCriteria c : criteria) {
            if ("MAX_INCOME".equalsIgnoreCase(c.getCriterionName())) {
                criterionAnswers.put(c.getId(), "500000");
            } else {
                criterionAnswers.put(c.getId(), "YES");
            }
        }
        highIncReq.setCriterionAnswers(criterionAnswers);

        EligibilityCheckResponseDto res = eligibilityService.evaluateEligibility(beneficiary, highIncReq);
        assertFalse(res.isEligible(), "Applicant with income exceeding ceiling must be marked INELIGIBLE");
    }
}
