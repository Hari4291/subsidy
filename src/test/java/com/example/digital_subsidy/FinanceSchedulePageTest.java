package com.example.digital_subsidy;

import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.*;
import com.example.digital_subsidy.service.DisbursementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
public class FinanceSchedulePageTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private BeneficiaryRepository beneficiaryRepository;

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DisbursementService disbursementService;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private DocumentRepository documentRepository;

    private Application testApplication;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        long timestamp = System.currentTimeMillis();

        User user = new User();
        user.setEmail("ben_sched_" + timestamp + "@gov.in");
        user.setPassword("Password@123");
        user.setFullName("Schedule Beneficiary");
        user.setMobileNumber("98765" + (timestamp % 100000));
        user = userRepository.save(user);

        Beneficiary beneficiary = new Beneficiary();
        beneficiary.setUser(user);
        beneficiary.setIdentityType("AADHAAR");
        beneficiary.setIdentityNumber("12345678" + (timestamp % 10000));
        beneficiary.setAddress("123 Main St");
        beneficiary.setAge(30);
        beneficiary.setGender("MALE");
        beneficiary.setPincode("400001");
        beneficiary.setCategory("GENERAL");
        beneficiary.setState("MAHARASHTRA");
        beneficiary.setDistrict("MUMBAI");
        beneficiary = beneficiaryRepository.save(beneficiary);

        Scheme scheme = new Scheme();
        scheme.setName("Schedule Scheme " + timestamp);
        scheme.setCategory("AGRICULTURE");
        scheme.setTotalBudget(new BigDecimal("1000000.00"));
        scheme.setRemainingBudget(new BigDecimal("1000000.00"));
        scheme.setMinAmount(new BigDecimal("1000.00"));
        scheme.setMaxAmount(new BigDecimal("100000.00"));
        scheme.setStatus("ACTIVE");
        scheme = schemeRepository.save(scheme);

        testApplication = new Application();
        testApplication.setApplicationNumber("APP-SCHED-" + timestamp);
        testApplication.setBeneficiary(beneficiary);
        testApplication.setScheme(scheme);
        testApplication.setRequestedAmount(new BigDecimal("50000.00"));
        testApplication.setSanctionedAmount(new BigDecimal("50000.00"));
        testApplication.setStatus("APPROVED");
        testApplication.setCurrentStage("DISBURSEMENT");
        testApplication = applicationRepository.save(testApplication);

        Disbursement disbursement = disbursementService.initializeDisbursement(testApplication, user);

        Document doc = new Document();
        doc.setBeneficiary(beneficiary);
        doc.setApplication(testApplication);
        doc.setDocumentType("UTILIZATION_CERTIFICATE");
        doc.setFileName("proof.pdf");
        doc.setFilePath("/uploads/proof.pdf");
        doc.setFileSize(1024L);
        doc.setContentType("application/pdf");
        doc.setStatus("ACTIVE");
        doc = documentRepository.save(doc);

        Milestone m = disbursement.getMilestones().get(0);
        m.setSubmittedDocument(doc);
        m.setComplianceVerificationStatus("SUBMITTED");
        milestoneRepository.save(m);
    }

    @Test
    @WithMockUser(username = "finance@gov.in", roles = {"FINANCE_APPROVER"})
    void testRenderSchedulePageSingleTermFortyThousand() throws Exception {
        testApplication.setSanctionedAmount(new BigDecimal("40000.00"));
        testApplication = applicationRepository.save(testApplication);

        MvcResult result = mockMvc.perform(get("/disbursements/schedule/" + testApplication.getId()))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("Single-Phase Grant Release (100%)"), 
                "Schedule page MUST render Single-Phase Grant Release for sanctionedAmount <= 40k");
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("Single-Phase Release Plan (100%)"));
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("Three-Phase Release Plan"));
    }

    @Test
    @WithMockUser(username = "finance@gov.in", roles = {"FINANCE_APPROVER"})
    void testRenderSchedulePageThreeTermsFiftyThousand() throws Exception {
        MvcResult result = mockMvc.perform(get("/disbursements/schedule/" + testApplication.getId()))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(content.contains("Three-Phase Release Plan"));
        org.junit.jupiter.api.Assertions.assertFalse(content.contains("Single-Phase Release Plan (100%)"));
    }
}
