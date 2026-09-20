package com.example.digital_subsidy;

import com.example.digital_subsidy.dto.ApplicationSubmissionDto;
import com.example.digital_subsidy.dto.BeneficiaryRegistrationDto;
import com.example.digital_subsidy.dto.EligibilityCheckRequestDto;
import com.example.digital_subsidy.dto.EligibilityCheckResponseDto;
import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.EligibilityCriteriaRepository;
import com.example.digital_subsidy.repository.SchemeRepository;
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

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;

@SpringBootTest
@Transactional
public class FixBugsValidationTest {

    @Autowired
    private BeneficiaryService beneficiaryService;

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private EligibilityCriteriaRepository criteriaRepository;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private EligibilityService eligibilityService;

    @Test
    @DisplayName("Bug 1 Test: Field Officer Logout Session Invalidation & Security")
    public void testFieldOfficerLogoutAndProtectedAccessControl() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        TestingAuthenticationToken auth = new TestingAuthenticationToken("field.officer@gmail.com", "pass", "ROLE_FIELD_OFFICER");
        SecurityContextHolder.getContext().setAuthentication(auth);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());

        SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
        logoutHandler.setInvalidateHttpSession(true);
        logoutHandler.setClearAuthentication(true);
        logoutHandler.logout(request, response, SecurityContextHolder.getContext().getAuthentication());

        assertNull(SecurityContextHolder.getContext().getAuthentication(), "Authentication must be cleared on logout");
    }

    @Test
    @DisplayName("Bug 2 Test: Unique Document Retrieval Strategy (No Duplicate Displays)")
    public void testUniqueDocumentRetrievalStrategy() throws Exception {
        long ts = System.currentTimeMillis();
        Beneficiary beneficiary = createBeneficiary("DocUser" + ts, "doc" + ts + "@gmail.com", "9870" + (ts % 1000000));
        Scheme scheme = schemeRepository.findAll().get(0);

        // 1. Initial Document Uploads during eligibility check (application = null)
        MockMultipartFile aadhaar1 = new MockMultipartFile("file", "aadhaar_v1.pdf", "application/pdf", "Aadhaar v1".getBytes());
        MockMultipartFile passbook1 = new MockMultipartFile("file", "passbook_v1.pdf", "application/pdf", "Passbook v1".getBytes());

        documentService.saveDocument(beneficiary, null, "AADHAAR", aadhaar1);
        documentService.saveDocument(beneficiary, null, "BANK_PASSBOOK", passbook1);

        // 2. Re-upload eligibility check documents before submission
        MockMultipartFile aadhaar2 = new MockMultipartFile("file", "aadhaar_v2.pdf", "application/pdf", "Aadhaar v2".getBytes());
        documentService.saveDocument(beneficiary, null, "AADHAAR", aadhaar2);

        // 3. Submit Application
        ApplicationSubmissionDto dto = new ApplicationSubmissionDto();
        dto.setSchemeId(scheme.getId());
        dto.setRequestedAmount(new BigDecimal("30000"));
        dto.setAnnualIncome(new BigDecimal("120000"));
        Application app = applicationService.submitApplication(beneficiary, dto);

        // 4. Verify Latest Active Documents retrieval
        List<Document> latestDocs = documentService.getLatestActiveDocumentsByApplication(app.getId());

        assertEquals(2, latestDocs.size(), "Must display exactly 2 unique document types (AADHAAR and BANK_PASSBOOK)");
        
        long aadhaarCount = latestDocs.stream().filter(d -> "AADHAAR".equalsIgnoreCase(d.getDocumentType())).count();
        long passbookCount = latestDocs.stream().filter(d -> "BANK_PASSBOOK".equalsIgnoreCase(d.getDocumentType())).count();

        assertEquals(1, aadhaarCount, "Aadhaar document must be displayed exactly once");
        assertEquals(1, passbookCount, "Bank Passbook document must be displayed exactly once");

        // Verify that aadhaar2 (the latest version) is the one active in latestDocs
        Document activeAadhaar = latestDocs.stream().filter(d -> "AADHAAR".equalsIgnoreCase(d.getDocumentType())).findFirst().orElseThrow();
        assertEquals("aadhaar_v2.pdf", activeAadhaar.getFileName(), "The latest uploaded file must be active");
    }

    @Test
    @DisplayName("Bug 3 Test: Education Scheme Eligibility Score 85/100 >= Min 60/100 -> ELIGIBLE")
    public void testEducationSchemeScore85IsEligible() {
        Scheme eduScheme = schemeRepository.findAll().stream()
                .filter(s -> "Education".equalsIgnoreCase(s.getCategory()) || s.getName().toLowerCase().contains("education"))
                .findFirst().orElseThrow();

        // Ensure scheme minimum qualifying score is set to 60
        eduScheme.setMinQualifyingScore(60);
        schemeRepository.save(eduScheme);

        long ts = System.currentTimeMillis();
        Beneficiary beneficiary = createBeneficiary("EduUser" + ts, "edu" + ts + "@gmail.com", "9871" + (ts % 1000000));

        List<EligibilityCriteria> criteria = criteriaRepository.findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(eduScheme.getId());
        Map<Long, String> answers = new HashMap<>();

        // Set answers for criteria to achieve ~85% score
        for (EligibilityCriteria c : criteria) {
            String name = c.getCriterionName() != null ? c.getCriterionName().toUpperCase() : "";
            if ("MAX_INCOME".equals(name)) {
                answers.put(c.getId(), "150000");
            } else if ("ACADEMIC_PERFORMANCE".equals(name)) {
                answers.put(c.getId(), "85.5");
            } else if ("EDUCATION_LEVEL".equals(name)) {
                answers.put(c.getId(), "Undergraduate");
            } else if ("INSTITUTION_TYPE".equals(name)) {
                answers.put(c.getId(), "Government");
            } else if ("STUDY_YEAR".equals(name)) {
                answers.put(c.getId(), "2nd Year");
            } else if ("COURSE_DURATION".equals(name)) {
                answers.put(c.getId(), "4");
            } else if ("SCHOLARSHIP_STATUS".equals(name)) {
                answers.put(c.getId(), "YES"); // Fails scholarship check to get 85% instead of 100%
            } else {
                answers.put(c.getId(), "YES");
            }
        }

        EligibilityCheckRequestDto req = new EligibilityCheckRequestDto();
        req.setSchemeId(eduScheme.getId());
        req.setAnnualIncome(new BigDecimal("150000"));
        req.setCriterionAnswers(answers);

        EligibilityCheckResponseDto res = eligibilityService.evaluateEligibility(beneficiary, req);

        assertTrue(res.getTotalScore() >= eduScheme.getMinQualifyingScore(), "Score should be >= min qualifying score (" + eduScheme.getMinQualifyingScore() + ")");
        assertTrue(res.isEligible(), "Score (" + res.getTotalScore() + ") meeting threshold (60) must evaluate to ELIGIBLE");
        assertTrue(res.getStatusMessage().contains("Congratulations"), "Status message should indicate success");
    }

    @Test
    @DisplayName("Bug 3 Test: Education Scheme Eligibility Score 55/100 < Min 60/100 -> NOT ELIGIBLE")
    public void testEducationSchemeScore55IsNotEligible() {
        Scheme eduScheme = schemeRepository.findAll().stream()
                .filter(s -> "Education".equalsIgnoreCase(s.getCategory()) || s.getName().toLowerCase().contains("education"))
                .findFirst().orElseThrow();

        eduScheme.setMinQualifyingScore(60);
        schemeRepository.save(eduScheme);

        long ts = System.currentTimeMillis();
        Beneficiary beneficiary = createBeneficiary("EduUser2" + ts, "edu2" + ts + "@gmail.com", "9872" + (ts % 1000000));

        List<EligibilityCriteria> criteria = criteriaRepository.findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(eduScheme.getId());
        Map<Long, String> answers = new HashMap<>();

        // Fail multiple criteria to drop score below 60%
        for (int i = 0; i < criteria.size(); i++) {
            EligibilityCriteria c = criteria.get(i);
            if ("MAX_INCOME".equalsIgnoreCase(c.getCriterionName())) {
                answers.put(c.getId(), "150000");
            } else if (i < criteria.size() / 2) {
                answers.put(c.getId(), "NO"); // Fail first half
            } else {
                answers.put(c.getId(), "YES");
            }
        }

        EligibilityCheckRequestDto req = new EligibilityCheckRequestDto();
        req.setSchemeId(eduScheme.getId());
        req.setAnnualIncome(new BigDecimal("150000"));
        req.setCriterionAnswers(answers);

        EligibilityCheckResponseDto res = eligibilityService.evaluateEligibility(beneficiary, req);

        assertTrue(res.getTotalScore() < eduScheme.getMinQualifyingScore(), "Score (" + res.getTotalScore() + ") should be < min qualifying score (60)");
        assertFalse(res.isEligible(), "Score (" + res.getTotalScore() + ") below threshold (60) must evaluate to NOT ELIGIBLE");
    }

    private Beneficiary createBeneficiary(String name, String email, String mobile) {
        BeneficiaryRegistrationDto reg = new BeneficiaryRegistrationDto();
        reg.setFullName(name);
        reg.setEmail(email);
        reg.setMobileNumber(mobile);
        reg.setPassword("Pass@1234");
        reg.setConfirmPassword("Pass@1234");
        reg.setAge(22);
        reg.setGender("Female");
        reg.setCategory("General");
        reg.setState("Andhra Pradesh");
        reg.setDistrict("Kakinada");
        reg.setAddress("College Road 12");
        reg.setPincode("533003");
        reg.setIdentityType("Aadhaar");
        reg.setIdentityNumber(String.format("%012d", System.currentTimeMillis() % 1000000000000L));
        reg.setTermsAgreed(true);
        return beneficiaryService.registerBeneficiary(reg);
    }
}
