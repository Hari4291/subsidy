package com.example.digital_subsidy;

import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.*;
import com.example.digital_subsidy.service.GrievanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
public class BeneficiaryGrievanceWorkflowTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

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
    private GrievanceRepository grievanceRepository;

    @Autowired
    private GrievanceService grievanceService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private User beneficiaryUser1;
    private Beneficiary beneficiary1;

    private User beneficiaryUser2;
    private Beneficiary beneficiary2;

    private User districtOfficerUser;
    private Officer districtOfficer;

    private Application application1;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        long ts = System.currentTimeMillis();

        Role benRole = roleRepository.findByName("ROLE_BENEFICIARY")
                .or(() -> roleRepository.findByName("BENEFICIARY"))
                .orElseGet(() -> roleRepository.save(new Role("ROLE_BENEFICIARY")));

        Role doRole = roleRepository.findByName("ROLE_DISTRICT_OFFICER")
                .or(() -> roleRepository.findByName("DISTRICT_OFFICER"))
                .orElseGet(() -> roleRepository.save(new Role("ROLE_DISTRICT_OFFICER")));

        // Beneficiary 1
        beneficiaryUser1 = new User();
        beneficiaryUser1.setEmail("grievance.ben1." + ts + "@example.com");
        beneficiaryUser1.setFullName("Grievance Applicant One");
        beneficiaryUser1.setPassword("password");
        beneficiaryUser1.setMobileNumber("91" + (ts % 100000000L));
        beneficiaryUser1.getRoles().add(benRole);
        beneficiaryUser1 = userRepository.save(beneficiaryUser1);

        beneficiary1 = new Beneficiary();
        beneficiary1.setUser(beneficiaryUser1);
        beneficiary1.setAge(32);
        beneficiary1.setGender("Male");
        beneficiary1.setCategory("GENERAL");
        beneficiary1.setPincode("500001");
        beneficiary1.setIdentityType("AADHAAR");
        beneficiary1.setIdentityNumber("11112222" + (ts % 10000L));
        beneficiary1.setDistrict("Hyderabad");
        beneficiary1.setState("Telangana");
        beneficiary1.setAddress("Street 1, Hyd");
        beneficiary1 = beneficiaryRepository.save(beneficiary1);

        // Beneficiary 2
        beneficiaryUser2 = new User();
        beneficiaryUser2.setEmail("grievance.ben2." + ts + "@example.com");
        beneficiaryUser2.setFullName("Grievance Applicant Two");
        beneficiaryUser2.setPassword("password");
        beneficiaryUser2.setMobileNumber("92" + (ts % 100000000L));
        beneficiaryUser2.getRoles().add(benRole);
        beneficiaryUser2 = userRepository.save(beneficiaryUser2);

        beneficiary2 = new Beneficiary();
        beneficiary2.setUser(beneficiaryUser2);
        beneficiary2.setAge(28);
        beneficiary2.setGender("Female");
        beneficiary2.setCategory("GENERAL");
        beneficiary2.setPincode("500002");
        beneficiary2.setIdentityType("AADHAAR");
        beneficiary2.setIdentityNumber("33334444" + (ts % 10000L));
        beneficiary2.setDistrict("Hyderabad");
        beneficiary2.setState("Telangana");
        beneficiary2.setAddress("Street 2, Hyd");
        beneficiary2 = beneficiaryRepository.save(beneficiary2);

        // District Officer
        districtOfficerUser = new User();
        districtOfficerUser.setEmail("grievance.officer." + ts + "@example.com");
        districtOfficerUser.setFullName("Hyderabad District Collector");
        districtOfficerUser.setPassword("password");
        districtOfficerUser.setMobileNumber("93" + (ts % 100000000L));
        districtOfficerUser.getRoles().add(doRole);
        districtOfficerUser = userRepository.save(districtOfficerUser);

        districtOfficer = new Officer(districtOfficerUser, "Hyderabad Collectorate", districtOfficerUser.getEmail(), districtOfficerUser.getMobileNumber(), "DISTRICT_OFFICER", "Telangana", "Hyderabad");
        districtOfficer = officerRepository.save(districtOfficer);

        // Scheme & Application
        Scheme scheme = schemeRepository.findAll().stream().findFirst().orElseGet(() -> {
            Scheme s = new Scheme();
            s.setName("Test Subsidy Scheme");
            s.setCategory("AGRICULTURE");
            s.setMinAmount(new BigDecimal("1000"));
            s.setMaxAmount(new BigDecimal("100000"));
            s.setTotalBudget(new BigDecimal("1000000"));
            s.setRemainingBudget(new BigDecimal("1000000"));
            s.setTargetCategories("GENERAL");
            s.setEligibleStates("ALL");
            return schemeRepository.save(s);
        });

        application1 = new Application();
        application1.setApplicationNumber("APP-GRV-" + ts);
        application1.setBeneficiary(beneficiary1);
        application1.setScheme(scheme);
        application1.setRequestedAmount(new BigDecimal("50000"));
        application1.setCurrentStage("FIELD_VERIFICATION");
        application1.setStatus("PENDING");
        application1 = applicationRepository.save(application1);
    }

    @Test
    @DisplayName("Beneficiary Grievances List Page Renders Successfully")
    void testShowBeneficiaryGrievancesPage() throws Exception {
        mockMvc.perform(get("/beneficiary/grievances")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(beneficiaryUser1.getEmail()).roles("BENEFICIARY")))
                .andExpect(status().isOk())
                .andExpect(view().name("beneficiary/grievances"))
                .andExpect(model().attributeExists("beneficiary", "grievances", "applications"));
    }

    @Test
    @DisplayName("Beneficiary Submit Grievance - Success Flow Redirects to Dashboard")
    void testBeneficiarySubmitGrievanceSuccess() throws Exception {
        mockMvc.perform(post("/beneficiary/grievances/submit")
                        .with(csrf())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(beneficiaryUser1.getEmail()).roles("BENEFICIARY"))
                        .param("applicationId", application1.getId().toString())
                        .param("category", "APPLICATION_DELAY")
                        .param("subject", "Delayed Field Verification")
                        .param("description", "Field officer has not visited for 20 days."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/beneficiary/dashboard"))
                .andExpect(flash().attribute("successMessage", "Your grievance has been submitted successfully."));

        List<Grievance> list = grievanceRepository.findByBeneficiaryIdOrderByIdDesc(beneficiary1.getId());
        assertEquals(1, list.size());
        Grievance g = list.get(0);
        assertEquals("APPLICATION_DELAY", g.getCategory());
        assertEquals("SUBMITTED", g.getStatus());
        assertEquals("HIGH", g.getPriority());
        assertNotNull(g.getGrievanceNumber());
        assertTrue(g.getGrievanceNumber().startsWith("GRV-"));
    }

    @Test
    @DisplayName("IDOR Security Protection: Beneficiary cannot submit grievance for another beneficiary's application")
    void testIdorProtectionOnGrievanceSubmission() throws Exception {
        // Beneficiary 2 attempts to submit grievance referencing Beneficiary 1's application
        mockMvc.perform(post("/beneficiary/grievances/submit")
                        .with(csrf())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(beneficiaryUser2.getEmail()).roles("BENEFICIARY"))
                        .param("applicationId", application1.getId().toString())
                        .param("category", "REJECTION_COMPLAINT")
                        .param("subject", "Unauthorized Attempt")
                        .param("description", "Malicious IDOR attempt"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/beneficiary/grievances/new"))
                .andExpect(flash().attribute("errorMessage", "Access Denied: Invalid application selected."));
    }

    @Test
    @DisplayName("5MB File Limit Validation: Document exceeding 5MB is rejected")
    void testMaxFileSizeValidation() throws Exception {
        byte[] largeContent = new byte[6 * 1024 * 1024]; // 6MB file
        org.springframework.mock.web.MockMultipartFile largeFile =
                new org.springframework.mock.web.MockMultipartFile("supportingDocument", "large_doc.pdf", "application/pdf", largeContent);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/beneficiary/grievances/submit")
                        .file(largeFile)
                        .with(csrf())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(beneficiaryUser1.getEmail()).roles("BENEFICIARY"))
                        .param("category", "OTHER")
                        .param("subject", "Large File Upload")
                        .param("description", "Testing 5MB restriction."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/beneficiary/grievances/new"))
                .andExpect(flash().attribute("errorMessage", "Supporting document size exceeds maximum allowed limit of 5MB."));
    }

    @Test
    @DisplayName("Beneficiary Reopen Resolved Grievance Flow")
    void testBeneficiaryReopenGrievanceFlow() throws Exception {
        Grievance g = grievanceService.submitGrievance(beneficiary1, application1.getId(), "DISBURSEMENT_ISSUE", "Payment Pending", "Fund not credited", null);
        grievanceService.updateGrievanceStatus(g.getId(), "RESOLVED", "Payment dispatched", null, districtOfficerUser.getEmail());

        mockMvc.perform(post("/beneficiary/grievances/" + g.getId() + "/reopen")
                        .with(csrf())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(beneficiaryUser1.getEmail()).roles("BENEFICIARY"))
                        .param("reopenReason", "Bank statement shows money has not arrived after 5 days."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/beneficiary/grievances/" + g.getId()))
                .andExpect(flash().attribute("successMessage", "Grievance reopened successfully."));

        Grievance reopened = grievanceService.getGrievanceById(g.getId());
        assertEquals("REOPENED", reopened.getStatus());
        assertTrue(reopened.getResolutionRemarks().contains("Bank statement shows money has not arrived"));
    }

    @Test
    @DisplayName("IDOR Security Protection: Service layer throws AccessDeniedException on illegal application ID")
    void testServiceLayerIdorProtection() {
        assertThrows(AccessDeniedException.class, () -> {
            grievanceService.submitGrievance(beneficiary2, application1.getId(), "OTHER", "Fraudulent test", "IDOR attempt", null);
        });
    }

    @Test
    @DisplayName("District Officer Grievances Page Renders Metrics and Data")
    void testDistrictOfficerGrievancesDashboard() throws Exception {
        // Submit a grievance for Hyderabad district
        grievanceService.submitGrievance(beneficiary1, application1.getId(), "DISBURSEMENT_ISSUE", "Disbursement delayed", "Disbursement was expected last week", null);

        mockMvc.perform(get("/district-officer/grievances")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(districtOfficerUser.getEmail()).roles("DISTRICT_OFFICER")))
                .andExpect(status().isOk())
                .andExpect(view().name("district-officer/grievances"))
                .andExpect(model().attributeExists("grievances", "metrics", "districtName"));

        Map<String, Long> metrics = grievanceService.getGrievanceMetrics("Hyderabad", "Telangana");
        assertTrue(metrics.get("total") >= 1);
        assertTrue(metrics.get("newCount") >= 1);
    }

    @Test
    @DisplayName("End-to-End Test: Beneficiary submits grievance -> DB persistence verified -> District Officer sees ticket & counts increase -> Other district officer cannot see ticket")
    void testEndToEndGrievanceSubmissionAndDistrictIsolation() throws Exception {
        // 1. Submit new grievance via endpoint for Beneficiary 1 (Hyderabad district)
        mockMvc.perform(post("/beneficiary/grievances/submit")
                        .with(csrf())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(beneficiaryUser1.getEmail()).roles("BENEFICIARY"))
                        .param("applicationId", application1.getId().toString())
                        .param("category", "APPLICATION_DELAY")
                        .param("subject", "Delay in Field Verification Visit")
                        .param("description", "Field officer has not visited for over 15 days."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/beneficiary/dashboard"));

        // 2. Database persistence verification
        List<Grievance> ben1Grievances = grievanceRepository.findByBeneficiaryIdOrderByIdDesc(beneficiary1.getId());
        assertEquals(1, ben1Grievances.size());
        Grievance dbGrievance = ben1Grievances.get(0);
        assertNotNull(dbGrievance.getId());
        assertEquals("Hyderabad", dbGrievance.getBeneficiary().getDistrict());
        assertEquals("APPLICATION_DELAY", dbGrievance.getCategory());
        assertEquals("SUBMITTED", dbGrievance.getStatus());

        // 3. Create another beneficiary and district officer for Patna district
        long ts = System.currentTimeMillis();
        User patnaBenUser = new User();
        patnaBenUser.setEmail("patna.ben." + ts + "@example.com");
        patnaBenUser.setFullName("Patna Applicant");
        patnaBenUser.setPassword("password");
        patnaBenUser.setMobileNumber("94" + (ts % 100000000L));
        userRepository.save(patnaBenUser);

        Beneficiary patnaBeneficiary = new Beneficiary();
        patnaBeneficiary.setUser(patnaBenUser);
        patnaBeneficiary.setAge(30);
        patnaBeneficiary.setGender("Male");
        patnaBeneficiary.setCategory("GENERAL");
        patnaBeneficiary.setPincode("800001");
        patnaBeneficiary.setIdentityType("AADHAAR");
        patnaBeneficiary.setIdentityNumber("99990000" + (ts % 10000L));
        patnaBeneficiary.setDistrict("Patna");
        patnaBeneficiary.setState("Bihar");
        patnaBeneficiary.setAddress("Main Road, Patna");
        patnaBeneficiary = beneficiaryRepository.save(patnaBeneficiary);

        Grievance patnaGrievance = grievanceService.submitGrievance(patnaBeneficiary, null, "TECHNICAL_ISSUE", "Patna Complaint", "Portal login error", null);

        User patnaOfficerUser = new User();
        patnaOfficerUser.setEmail("patna.officer." + ts + "@example.com");
        patnaOfficerUser.setFullName("Patna District Collector");
        patnaOfficerUser.setPassword("password");
        patnaOfficerUser.setMobileNumber("95" + (ts % 100000000L));
        userRepository.save(patnaOfficerUser);

        Officer patnaOfficer = new Officer(patnaOfficerUser, "Patna Collectorate", patnaOfficerUser.getEmail(), patnaOfficerUser.getMobileNumber(), "DISTRICT_OFFICER", "Bihar", "Patna");
        officerRepository.save(patnaOfficer);

        // 4. Login as Hyderabad District Officer -> Verify Hyderabad grievance is visible & counts increased
        mockMvc.perform(get("/district-officer/grievances")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(districtOfficerUser.getEmail()).roles("DISTRICT_OFFICER")))
                .andExpect(status().isOk())
                .andExpect(view().name("district-officer/grievances"))
                .andExpect(model().attributeExists("grievances", "metrics"))
                .andExpect(result -> {
                    List<Grievance> list = (List<Grievance>) result.getModelAndView().getModel().get("grievances");
                    Map<String, Long> metrics = (Map<String, Long>) result.getModelAndView().getModel().get("metrics");
                    assertTrue(list.stream().anyMatch(g -> g.getId().equals(dbGrievance.getId())));
                    assertFalse(list.stream().anyMatch(g -> g.getId().equals(patnaGrievance.getId())));
                    assertTrue(metrics.get("total") >= 1);
                    assertTrue(metrics.get("newCount") >= 1);
                });

        // 5. Login as Patna District Officer -> Verify Patna officer sees ONLY Patna grievance
        mockMvc.perform(get("/district-officer/grievances")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(patnaOfficerUser.getEmail()).roles("DISTRICT_OFFICER")))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    List<Grievance> list = (List<Grievance>) result.getModelAndView().getModel().get("grievances");
                    assertTrue(list.stream().anyMatch(g -> g.getId().equals(patnaGrievance.getId())));
                    assertFalse(list.stream().anyMatch(g -> g.getId().equals(dbGrievance.getId())));
                });
    }

    @Test
    @DisplayName("Full Grievance Lifecycle: Submit -> DO Clarification -> Beneficiary Response -> DO Resolve")
    void testFullGrievanceLifecycle() throws Exception {
        // 1. Submit Grievance
        Grievance g = grievanceService.submitGrievance(beneficiary1, application1.getId(), "DOCUMENT_VERIFICATION", "Income certificate issue", "Original document rejected.", null);
        assertEquals("SUBMITTED", g.getStatus());

        // 2. District Officer views details page
        mockMvc.perform(get("/district-officer/grievances/" + g.getId())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(districtOfficerUser.getEmail()).roles("DISTRICT_OFFICER")))
                .andExpect(status().isOk())
                .andExpect(view().name("district-officer/grievance-details"))
                .andExpect(model().attributeExists("grievance", "officer"));

        // 3. District Officer requests clarification
        mockMvc.perform(post("/district-officer/grievances/" + g.getId() + "/action")
                        .with(csrf())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(districtOfficerUser.getEmail()).roles("DISTRICT_OFFICER"))
                        .param("status", "CLARIFICATION_REQUIRED")
                        .param("remarks", "Please upload digital signed copy of MeeSeva Income Certificate."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/district-officer/grievances"));

        Grievance afterClarificationReq = grievanceService.getGrievanceById(g.getId());
        assertEquals("CLARIFICATION_REQUIRED", afterClarificationReq.getStatus());
        assertEquals("Please upload digital signed copy of MeeSeva Income Certificate.", afterClarificationReq.getResolutionRemarks());

        // 4. Beneficiary views detail page and submits clarification response
        mockMvc.perform(get("/beneficiary/grievances/" + g.getId())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(beneficiaryUser1.getEmail()).roles("BENEFICIARY")))
                .andExpect(status().isOk())
                .andExpect(view().name("beneficiary/grievance-details"));

        mockMvc.perform(post("/beneficiary/grievances/" + g.getId() + "/clarification")
                        .with(csrf())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(beneficiaryUser1.getEmail()).roles("BENEFICIARY"))
                        .param("clarificationResponse", "Uploaded MeeSeva Certificate IC-2026-999888."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/beneficiary/grievances/" + g.getId()));

        Grievance afterBenResponse = grievanceService.getGrievanceById(g.getId());
        assertEquals("UNDER_REVIEW", afterBenResponse.getStatus());
        assertEquals("Uploaded MeeSeva Certificate IC-2026-999888.", afterBenResponse.getBeneficiaryClarification());

        // 5. District Officer resolves grievance
        mockMvc.perform(post("/district-officer/grievances/" + g.getId() + "/action")
                        .with(csrf())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(districtOfficerUser.getEmail()).roles("DISTRICT_OFFICER"))
                        .param("status", "RESOLVED")
                        .param("remarks", "Income certificate verified successfully. Application resumed."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/district-officer/grievances"));

        Grievance finalGrievance = grievanceService.getGrievanceById(g.getId());
        assertEquals("RESOLVED", finalGrievance.getStatus());
        assertEquals("Income certificate verified successfully. Application resumed.", finalGrievance.getResolutionRemarks());
        assertEquals(districtOfficerUser.getEmail(), finalGrievance.getResolvedBy());
        assertNotNull(finalGrievance.getResolvedAt());
    }
}
