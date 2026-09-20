package com.example.digital_subsidy;

import com.example.digital_subsidy.dto.ApplicationSubmissionDto;
import com.example.digital_subsidy.dto.BeneficiaryRegistrationDto;
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
public class RoleDifferenceWorkflowTest {

    @Autowired
    private BeneficiaryService beneficiaryService;

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private EligibilityCriteriaRepository criteriaRepository;

    @Autowired
    private RequiredDocumentRepository requiredDocumentRepository;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private VerificationService verificationService;

    @Autowired
    private ApplicationEscalationService escalationService;

    @Autowired
    private AnomalyDetectionService anomalyDetectionService;

    @Autowired
    private DistrictBudgetService districtBudgetService;

    @Autowired
    private GrievanceService grievanceService;

    @Autowired
    private OfficerService officerService;

    @Autowired
    private OfficerRepository officerRepository;

    @Test
    @DisplayName("Test FO vs DO Stage Transition Authority Limits")
    public void testFieldOfficerVsDistrictOfficerStageTransitions() throws Exception {
        long ts = System.currentTimeMillis();
        Beneficiary beneficiary = createTestBeneficiary("FoDoUser" + ts, "fodo" + ts + "@example.com", "987654" + (ts % 10000), "Kakinada");

        Scheme scheme = schemeRepository.findAll().get(0);
        Application app = submitTestApplication(beneficiary, scheme, new BigDecimal("45000"));

        // Upload required documents for the scheme
        List<RequiredDocument> reqDocs = requiredDocumentRepository.findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(scheme.getId());
        Map<Long, String> docStatuses = new HashMap<>();
        Map<Long, String> docRemarks = new HashMap<>();

        for (RequiredDocument rd : reqDocs) {
            MockMultipartFile file = new MockMultipartFile("file", rd.getDocumentType() + ".pdf", "application/pdf", "dummy content".getBytes());
            Document doc = documentService.saveDocument(beneficiary, app, rd.getDocumentType(), file);
            docStatuses.put(doc.getId(), "VERIFIED");
            docRemarks.put(doc.getId(), "Verified OK");
        }

        assertEquals("FIELD_VERIFICATION", app.getCurrentStage(), "New app stage must be FIELD_VERIFICATION");

        // 1. Field Officer approval -> MUST transition to DISTRICT_REVIEW (cannot jump to FINANCE_APPROVAL)
        VerificationActionDto foAction = new VerificationActionDto();
        foAction.setApplicationId(app.getId());
        foAction.setAction("APPROVE");
        foAction.setRemarks("Field ground check satisfied.");
        foAction.setDocumentVerificationStatuses(docStatuses);
        foAction.setDocumentVerificationRemarks(docRemarks);

        verificationService.processVerification("field.officer@gmail.com", "ROLE_FIELD_OFFICER", foAction);

        Application afterFoApp = applicationService.getApplicationById(app.getId());
        assertEquals("DISTRICT_REVIEW", afterFoApp.getCurrentStage(), "FO approval must advance stage to DISTRICT_REVIEW");

        // 2. District Officer approval -> MUST transition to FINANCE_APPROVAL
        VerificationActionDto doAction = new VerificationActionDto();
        doAction.setApplicationId(app.getId());
        doAction.setAction("APPROVE");
        doAction.setRemarks("District sanction granted.");

        verificationService.processVerification("district.officer@gmail.com", "ROLE_DISTRICT_OFFICER", doAction);

        Application afterDoApp = applicationService.getApplicationById(app.getId());
        assertEquals("FINANCE_APPROVAL", afterDoApp.getCurrentStage(), "DO approval must advance stage to FINANCE_APPROVAL");
    }

    @Test
    @DisplayName("Test Application Escalation Creation and Resolution")
    public void testEscalationWorkflow() {
        long ts = System.currentTimeMillis();
        Beneficiary beneficiary = createTestBeneficiary("EscUser" + ts, "esc" + ts + "@example.com", "987653" + (ts % 10000), "Andhra Pradesh");
        Scheme scheme = schemeRepository.findAll().get(0);
        Application app = submitTestApplication(beneficiary, scheme, new BigDecimal("60000"));

        // Create Escalation
        ApplicationEscalation escalation = escalationService.createEscalation(app, "Income document signature mismatch", "field.officer@gmail.com");
        assertNotNull(escalation.getId());
        assertEquals("OPEN", escalation.getStatus());

        // Verify query by district/state
        List<ApplicationEscalation> escList = escalationService.getEscalationsForDistrict("Andhra Pradesh", "ALL");
        assertTrue(escList.stream().anyMatch(e -> e.getId().equals(escalation.getId())));

        // Resolve Escalation by District Officer
        ApplicationEscalation resolved = escalationService.resolveEscalation(escalation.getId(), "District verified document with Tehsildar office.", "district.officer@gmail.com");
        assertEquals("RESOLVED", resolved.getStatus());
        assertNotNull(resolved.getResolvedAt());
    }

    @Test
    @DisplayName("Test Anomaly Detection & DO Resolution")
    public void testAnomalyDetectionAndResolution() {
        long ts = System.currentTimeMillis();
        Beneficiary beneficiary = createTestBeneficiary("AnomUser" + ts, "anom" + ts + "@example.com", "987652" + (ts % 10000), "Andhra Pradesh");
        Scheme scheme = schemeRepository.findAll().get(0);

        // Submit app with large requested amount (>= ₹90,000 threshold for INCOME_GRANT_MISMATCH)
        Application app = submitTestApplication(beneficiary, scheme, new BigDecimal("95000"));

        anomalyDetectionService.scanAndFlagAnomalies(app);
        List<AnomalyFlag> flags = anomalyDetectionService.getFlagsForApplication(app.getId());
        assertFalse(flags.isEmpty(), "Anomaly flags should be created for high requested amount");

        boolean hasFlag = flags.stream().anyMatch(f -> "INCOME_GRANT_MISMATCH".equalsIgnoreCase(f.getFlagType()));
        assertTrue(hasFlag, "INCOME_GRANT_MISMATCH anomaly flag must be generated");

        AnomalyFlag flag = flags.get(0);
        assertEquals("OPEN", flag.getStatus());

        // District Officer clears flag as FALSE_POSITIVE
        AnomalyFlag updatedFlag = anomalyDetectionService.resolveFlag(flag.getId(), "FALSE_POSITIVE", "Verified applicant possesses genuine high capital requirements.", "district.officer@gmail.com");
        assertEquals("FALSE_POSITIVE", updatedFlag.getStatus());
    }

    @Test
    @DisplayName("Test Real District Budget Calculation")
    public void testDistrictBudgetMonitoring() {
        Map<String, Object> budgetSummary = districtBudgetService.getDistrictBudgetSummary("Andhra Pradesh");

        assertNotNull(budgetSummary.get("state"));
        assertEquals("Andhra Pradesh", budgetSummary.get("state"));

        BigDecimal allocated = (BigDecimal) budgetSummary.get("totalAllocatedBudget");
        BigDecimal remaining = (BigDecimal) budgetSummary.get("totalRemainingBudget");
        BigDecimal approved = (BigDecimal) budgetSummary.get("approvedGrantAmount");
        BigDecimal pending = (BigDecimal) budgetSummary.get("pendingGrantAmount");
        BigDecimal disbursed = (BigDecimal) budgetSummary.get("disbursedGrantAmount");

        assertNotNull(allocated);
        assertNotNull(remaining);
        assertNotNull(approved);
        assertNotNull(pending);
        assertNotNull(disbursed);
    }

    @Test
    @DisplayName("Test Beneficiary Grievance Submission and Redressal")
    public void testGrievanceWorkflow() {
        long ts = System.currentTimeMillis();
        Beneficiary beneficiary = createTestBeneficiary("GrvUser" + ts, "grv" + ts + "@example.com", "987651" + (ts % 10000), "Andhra Pradesh");
        Scheme scheme = schemeRepository.findAll().get(0);
        Application app = submitTestApplication(beneficiary, scheme, new BigDecimal("25000"));

        // Beneficiary submits grievance
        Grievance grievance = grievanceService.submitGrievance(beneficiary, app.getId(), "DELAY", "Ground inspection pending for over 15 days.");
        assertNotNull(grievance.getId());
        assertEquals("SUBMITTED", grievance.getStatus());

        // Query by district/state
        List<Grievance> districtGrievances = grievanceService.getGrievancesForDistrict("Andhra Pradesh", null);
        assertTrue(districtGrievances.stream().anyMatch(g -> g.getId().equals(grievance.getId())));

        // DO updates grievance status to IN_PROGRESS then RESOLVED
        Grievance inProg = grievanceService.updateGrievanceStatus(grievance.getId(), "IN_PROGRESS", "Assigned senior field officer for priority check.", null, "district.officer@gmail.com");
        assertEquals("IN_PROGRESS", inProg.getStatus());

        Grievance resolved = grievanceService.updateGrievanceStatus(grievance.getId(), "RESOLVED", "Ground inspection completed and application approved.", null, "district.officer@gmail.com");
        assertEquals("RESOLVED", resolved.getStatus());
    }

    @Test
    @DisplayName("Test Field Officer Management & Application Reassignment")
    public void testOfficerManagementAndReassignment() {
        long ts = System.currentTimeMillis();
        Beneficiary beneficiary = createTestBeneficiary("ReassignUser" + ts, "reassign" + ts + "@example.com", "987650" + (ts % 10000), "Andhra Pradesh");
        Scheme scheme = schemeRepository.findAll().get(0);
        Application app = submitTestApplication(beneficiary, scheme, new BigDecimal("30000"));

        // Fetch Field Officers
        List<Officer> fieldOfficers = officerService.getFieldOfficersByDistrict("Andhra Pradesh", null);
        assertFalse(fieldOfficers.isEmpty(), "At least one Field Officer should exist in Andhra Pradesh");

        Officer fo = fieldOfficers.get(0);

        // Reassign application to Officer
        officerService.assignApplicationToOfficer(app, fo, "district.officer@gmail.com");

        Application reassignedApp = applicationService.getApplicationById(app.getId());
        assertNotNull(reassignedApp.getAssignedOfficer());
        assertEquals(fo.getId(), reassignedApp.getAssignedOfficer().getId());
    }

    private Beneficiary createTestBeneficiary(String name, String email, String mobile, String state) {
        BeneficiaryRegistrationDto reg = new BeneficiaryRegistrationDto();
        reg.setFullName(name);
        reg.setEmail(email);
        reg.setMobileNumber(mobile);
        reg.setPassword("Pass@12345");
        reg.setConfirmPassword("Pass@12345");
        reg.setAge(25);
        reg.setGender("Male");
        reg.setCategory("General");
        reg.setState(state);
        reg.setDistrict("Kakinada");
        reg.setAddress("Test Street 10");
        reg.setPincode("533001");
        reg.setIdentityType("Aadhaar");
        reg.setIdentityNumber(String.format("%012d", System.currentTimeMillis() % 1000000000000L));
        reg.setTermsAgreed(true);
        return beneficiaryService.registerBeneficiary(reg);
    }

    private Application submitTestApplication(Beneficiary beneficiary, Scheme scheme, BigDecimal requestedAmount) {
        List<EligibilityCriteria> criteria = criteriaRepository.findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(scheme.getId());
        Map<Long, String> answers = new HashMap<>();
        Map<Long, String> questions = new HashMap<>();
        Map<Long, Integer> orders = new HashMap<>();

        for (EligibilityCriteria c : criteria) {
            answers.put(c.getId(), "YES");
            questions.put(c.getId(), c.getQuestionText());
            orders.put(c.getId(), c.getDisplayOrder());
        }

        ApplicationSubmissionDto dto = new ApplicationSubmissionDto();
        dto.setSchemeId(scheme.getId());
        dto.setRequestedAmount(requestedAmount);
        dto.setAnnualIncome(new BigDecimal("120000"));
        dto.setCriterionAnswers(answers);
        dto.setCriterionQuestionSnapshots(questions);
        dto.setCriterionDisplayOrders(orders);

        return applicationService.submitApplication(beneficiary, dto);
    }
}
