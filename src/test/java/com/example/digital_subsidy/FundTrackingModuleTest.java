package com.example.digital_subsidy;

import com.example.digital_subsidy.controller.FundTrackingController;
import com.example.digital_subsidy.dto.*;
import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.*;
import com.example.digital_subsidy.service.DisbursementService;
import com.example.digital_subsidy.service.FundTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
public class FundTrackingModuleTest {

    @Autowired
    private FundTrackingService fundTrackingService;

    @Autowired
    private FundTrackingController fundTrackingController;

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

    @Autowired
    private DisbursementRepository disbursementRepository;

    private MockMvc mockMvc;

    private Application testApp;
    private Beneficiary testBeneficiary;
    private Scheme testScheme;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(fundTrackingController).build();

        long ts = System.currentTimeMillis();

        User user = new User();
        user.setEmail("fund_beneficiary_" + ts + "@gov.in");
        user.setPassword("Password@123");
        user.setFullName("Vamsi Bhai Test");
        user.setMobileNumber("91234" + (ts % 100000));
        user = userRepository.save(user);

        testBeneficiary = new Beneficiary();
        testBeneficiary.setUser(user);
        testBeneficiary.setIdentityType("AADHAAR");
        testBeneficiary.setIdentityNumber("12345678" + (ts % 10000));
        testBeneficiary.setDistrict("Visakhapatnam");
        testBeneficiary.setState("Andhra Pradesh");
        testBeneficiary.setAddress("123 Treasury Colony");
        testBeneficiary.setPincode("530001");
        testBeneficiary.setCategory("GENERAL");
        testBeneficiary.setGender("MALE");
        testBeneficiary.setAge(30);
        testBeneficiary = beneficiaryRepository.save(testBeneficiary);

        testScheme = new Scheme();
        testScheme.setName("Higher Education Financial Assistance Scheme " + ts);
        testScheme.setCategory("EDUCATION");
        testScheme.setMinAmount(new BigDecimal("10000.00"));
        testScheme.setMaxAmount(new BigDecimal("100000.00"));
        testScheme.setTotalBudget(new BigDecimal("1000000.00"));
        testScheme.setRemainingBudget(new BigDecimal("1000000.00"));
        testScheme.setStatus("ACTIVE");
        testScheme = schemeRepository.save(testScheme);

        testApp = new Application();
        testApp.setApplicationNumber("APP-" + ts);
        testApp.setBeneficiary(testBeneficiary);
        testApp.setScheme(testScheme);
        testApp.setRequestedAmount(new BigDecimal("100000.00"));
        testApp.setSanctionedAmount(new BigDecimal("100000.00"));
        testApp.setStatus("APPROVED");
        testApp = applicationRepository.save(testApp);

        // Initialize disbursement plan
        Disbursement disbursement = new Disbursement();
        disbursement.setApplication(testApp);
        disbursement.setTotalSanctionedAmount(new BigDecimal("100000.00"));
        disbursement.setDisbursedAmount(BigDecimal.ZERO);
        disbursement.setStatus("SCHEDULED");
        disbursement = disbursementRepository.save(disbursement);

        // Add 4 milestones (terms)
        for (int i = 1; i <= 4; i++) {
            Milestone m = new Milestone();
            m.setDisbursement(disbursement);
            m.setStageNumber(i);
            m.setMilestoneName("Term " + i);
            m.setMilestoneAmount(new BigDecimal("25000.00"));
            m.setPercentage(25);
            m.setReleasedAmount(BigDecimal.ZERO);
            m.setCompletionStatus(i == 1 ? "PENDING" : "UPCOMING");
            m.setComplianceVerificationStatus(i == 1 ? "VERIFIED" : "NONE");
            milestoneRepository.save(m);
        }
    }

    @Test
    @DisplayName("Verify Fund Summary Calculation Integrity")
    void testFundSummaryCalculation() {
        FundTrackingSummaryDto summary = fundTrackingService.getFundTrackingSummary(null, null, "All", null);

        assertNotNull(summary);
        assertTrue(summary.getTotalSanctioned().compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(summary.getTotalReleased().compareTo(BigDecimal.ZERO) >= 0);
        assertEquals(
                summary.getTotalSanctioned().subtract(summary.getTotalReleased()),
                summary.getTotalRemaining()
        );
        assertTrue(summary.getTotalBeneficiaries() >= 1);
        assertTrue(summary.getActiveApplications() >= 1);
    }

    @Test
    @DisplayName("Verify Beneficiary-wise Fund Tracking Data & Filters")
    void testBeneficiaryFundTrackingListAndFiltering() {
        List<BeneficiaryFundTrackingDto> list = fundTrackingService.getBeneficiaryFundTrackingList(null, null, "All", null);
        assertNotNull(list);
        assertFalse(list.isEmpty());

        BeneficiaryFundTrackingDto item = list.stream()
                .filter(b -> b.getApplicationId().equals(testApp.getId()))
                .findFirst()
                .orElse(null);

        assertNotNull(item);
        assertEquals("Vamsi Bhai Test", item.getBeneficiaryName());
        assertEquals(new BigDecimal("100000.00"), item.getTotalSanctionedAmount());
        assertEquals(4, item.getTotalTerms());
        assertEquals(0, item.getCompletedTerms());
        assertEquals(4, item.getRemainingTerms());

        // Test filtering by Search
        List<BeneficiaryFundTrackingDto> filteredSearch = fundTrackingService.getBeneficiaryFundTrackingList(null, null, "All", "Vamsi Bhai");
        assertFalse(filteredSearch.isEmpty());

        // Test filtering by Scheme
        List<BeneficiaryFundTrackingDto> filteredScheme = fundTrackingService.getBeneficiaryFundTrackingList(testScheme.getId(), null, "All", null);
        assertFalse(filteredScheme.isEmpty());
    }

    @Test
    @DisplayName("Verify Clickable Beneficiary Detail Modal Endpoint Data")
    void testBeneficiaryDetailModalData() {
        BeneficiaryFundTrackingDto detail = fundTrackingService.getBeneficiaryFundTrackingDetail(testApp.getId());

        assertNotNull(detail);
        assertEquals(testApp.getApplicationNumber(), detail.getApplicationNumber());
        assertEquals("Vamsi Bhai Test", detail.getBeneficiaryName());
        assertEquals(4, detail.getTermDetails().size());
        assertEquals(new BigDecimal("25000.00"), detail.getTermDetails().get(0).getMilestoneAmount());
    }

    @Test
    @DisplayName("Verify Term-Level Overall Summary")
    void testTermLevelSummary() {
        List<TermLevelSummaryDto> termSummaries = fundTrackingService.getTermLevelSummaries(null);
        assertNotNull(termSummaries);
        assertFalse(termSummaries.isEmpty());

        TermLevelSummaryDto term1 = termSummaries.stream()
                .filter(t -> t.getStageNumber() == 1)
                .findFirst()
                .orElse(null);

        assertNotNull(term1);
        assertTrue(term1.getApplicationCount() >= 1);
        assertTrue(term1.getTotalSanctioned().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Verify Term 1 Release Updates Financial Tracking Calculations dynamically")
    void testFinancialTrackingUpdateAfterRelease() {
        List<Milestone> milestones = milestoneRepository.findByApplicationIdOrderAsc(testApp.getId());
        Milestone term1 = milestones.get(0);

        // Release Term 1
        disbursementService.releaseMilestone(term1.getId());

        FundTrackingSummaryDto summary = fundTrackingService.getFundTrackingSummary(null, null, "All", null);
        assertTrue(summary.getTotalReleased().compareTo(new BigDecimal("25000.00")) >= 0);

        BeneficiaryFundTrackingDto beneficiaryDetail = fundTrackingService.getBeneficiaryFundTrackingDetail(testApp.getId());
        assertEquals(new BigDecimal("25000.00"), beneficiaryDetail.getTotalReleasedAmount());
        assertEquals(new BigDecimal("75000.00"), beneficiaryDetail.getRemainingAmount());
        assertEquals(1, beneficiaryDetail.getCompletedTerms());
        assertEquals(3, beneficiaryDetail.getRemainingTerms());
    }

    @Test
    @DisplayName("Verify Excel (.xlsx) Export Generation")
    void testExcelExportGeneration() throws Exception {
        byte[] excelBytes = fundTrackingService.exportToExcel(null, null, "All", null);
        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 500, "Excel output should be a non-empty valid XLSX workbook");
    }

    @Test
    @DisplayName("Verify PDF (.pdf) Report Generation")
    void testPdfExportGeneration() throws Exception {
        byte[] pdfBytes = fundTrackingService.exportToPdf(null, null, "All", null);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 500, "PDF output should be a non-empty valid PDF document");
    }

    @Test
    @WithMockUser(username = "finance_approver", roles = {"FINANCE_APPROVER"})
    @DisplayName("Verify Security Authorization & Controller REST endpoints")
    void testControllerEndpointsWithRole() throws Exception {
        mockMvc.perform(get("/finance/api/fund-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSanctioned").exists())
                .andExpect(jsonPath("$.totalReleased").exists());

        mockMvc.perform(get("/finance/api/fund-tracking/" + testApp.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationNumber").value(testApp.getApplicationNumber()));

        mockMvc.perform(get("/finance/fund-tracking/export/excel"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=Digital_Subsidy_Fund_Tracking_Report.xlsx"));

        mockMvc.perform(get("/finance/fund-tracking/export/pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "inline; filename=Digital_Subsidy_Fund_Tracking_Report.pdf"));
    }
}
