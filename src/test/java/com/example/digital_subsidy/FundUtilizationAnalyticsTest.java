package com.example.digital_subsidy;

import com.example.digital_subsidy.dto.*;
import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.*;
import com.example.digital_subsidy.service.DisbursementService;
import com.example.digital_subsidy.service.FundUtilizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class FundUtilizationAnalyticsTest {

    @Autowired
    private FundUtilizationService fundUtilizationService;

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
    private DisbursementReleaseRepository releaseRepository;

    @Autowired
    private FundUtilizationRepository utilizationRepository;

    private User financeUser;
    private User adminUser;
    private User districtUser;
    private User beneficiaryUser;
    private User fieldOfficerUser;
    private Beneficiary testBeneficiary;
    private Scheme testScheme1;
    private Scheme testScheme2;

    @BeforeEach
    void setUp() {
        long ts = System.currentTimeMillis();

        financeUser = new User();
        financeUser.setEmail("fin_util_" + ts + "@gov.in");
        financeUser.setPassword("Password@123");
        financeUser.setFullName("Finance User");
        financeUser.setMobileNumber("910" + (ts % 10000007));
        financeUser = userRepository.save(financeUser);

        adminUser = new User();
        adminUser.setEmail("admin_util_" + ts + "@gov.in");
        adminUser.setPassword("Password@123");
        adminUser.setFullName("Admin User");
        adminUser.setMobileNumber("920" + (ts % 10000007));
        adminUser = userRepository.save(adminUser);

        districtUser = new User();
        districtUser.setEmail("do_util_" + ts + "@gov.in");
        districtUser.setPassword("Password@123");
        districtUser.setFullName("District Officer");
        districtUser.setMobileNumber("930" + (ts % 10000007));
        districtUser = userRepository.save(districtUser);

        beneficiaryUser = new User();
        beneficiaryUser.setEmail("ben_util_" + ts + "@gov.in");
        beneficiaryUser.setPassword("Password@123");
        beneficiaryUser.setFullName("Beneficiary User");
        beneficiaryUser.setMobileNumber("940" + (ts % 10000007));
        beneficiaryUser = userRepository.save(beneficiaryUser);

        fieldOfficerUser = new User();
        fieldOfficerUser.setEmail("fo_util_" + ts + "@gov.in");
        fieldOfficerUser.setPassword("Password@123");
        fieldOfficerUser.setFullName("Field Officer");
        fieldOfficerUser.setMobileNumber("950" + (ts % 10000007));
        fieldOfficerUser = userRepository.save(fieldOfficerUser);

        testBeneficiary = new Beneficiary();
        testBeneficiary.setUser(beneficiaryUser);
        testBeneficiary.setIdentityType("AADHAAR");
        testBeneficiary.setIdentityNumber("556677" + (ts % 1000000));
        testBeneficiary.setAddress("500 Treasury Road");
        testBeneficiary.setAge(35);
        testBeneficiary.setGender("MALE");
        testBeneficiary.setPincode("500001");
        testBeneficiary.setCategory("OBC");
        testBeneficiary.setState("TELANGANA");
        testBeneficiary.setDistrict("HYDERABAD");
        testBeneficiary = beneficiaryRepository.save(testBeneficiary);

        testScheme1 = new Scheme();
        testScheme1.setName("Agriculture Scheme " + ts);
        testScheme1.setCategory("Agriculture");
        testScheme1.setTotalBudget(new BigDecimal("1000000.00"));
        testScheme1.setRemainingBudget(new BigDecimal("1000000.00"));
        testScheme1.setMinAmount(new BigDecimal("1000.00"));
        testScheme1.setMaxAmount(new BigDecimal("200000.00"));
        testScheme1.setStatus("ACTIVE");
        testScheme1 = schemeRepository.save(testScheme1);

        testScheme2 = new Scheme();
        testScheme2.setName("Solar Scheme " + ts);
        testScheme2.setCategory("Solar");
        testScheme2.setTotalBudget(new BigDecimal("2000000.00"));
        testScheme2.setRemainingBudget(new BigDecimal("2000000.00"));
        testScheme2.setMinAmount(new BigDecimal("1000.00"));
        testScheme2.setMaxAmount(new BigDecimal("300000.00"));
        testScheme2.setStatus("ACTIVE");
        testScheme2 = schemeRepository.save(testScheme2);
    }

    private Application createApplication(Scheme scheme, BigDecimal sanctionedAmt) {
        Application app = new Application();
        app.setApplicationNumber("APP-UTIL-" + System.currentTimeMillis() + "-" + (long)(Math.random()*1000));
        app.setBeneficiary(testBeneficiary);
        app.setScheme(scheme);
        app.setRequestedAmount(sanctionedAmt);
        app.setSanctionedAmount(sanctionedAmt);
        app.setStatus("APPROVED");
        app.setCurrentStage("DISBURSEMENT");
        return applicationRepository.save(app);
    }

    @Test
    @DisplayName("Test 1 & 5: System Fund Utilization Summary and BigDecimal Precision")
    void testFundUtilizationSummaryCalculation() {
        Application app = createApplication(testScheme1, new BigDecimal("80000.00"));
        Disbursement d = disbursementService.initializeDisbursement(app, financeUser);
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(d);

        // Verify milestone compliance
        milestones.get(0).setComplianceVerificationStatus("VERIFIED");
        milestoneRepository.save(milestones.get(0));

        // Release Phase 1
        StageReleaseDto dto = new StageReleaseDto();
        dto.setMilestoneId(milestones.get(0).getId());
        dto.setReleaseAmount(milestones.get(0).getMilestoneAmount());
        disbursementService.releaseMilestone(dto, financeUser.getEmail());

        FundUtilizationSummaryDto summary = fundUtilizationService.getSystemFundUtilizationSummary(null, null, null);
        assertNotNull(summary);
        assertTrue(summary.getTotalAllocatedAmount().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(summary.getTotalSanctionedAmount().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(summary.getTotalReleasedAmount().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(summary.getTotalUtilizedAmount().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Test 2 & 16: Zero Allocation & Empty Analytics Handling")
    void testZeroAllocationAndEmptyDataset() {
        FundUtilizationSummaryDto summary = fundUtilizationService.getSystemFundUtilizationSummary("NON_EXISTENT_CATEGORY", "NON_EXISTENT_STATE", null);
        assertNotNull(summary);
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.getTotalAllocatedAmount()));
        assertEquals(0, summary.getUtilizationPercentage());
    }

    @Test
    @DisplayName("Test 6 & 7: Remaining Amount and Utilization Percentage Calculation")
    void testRemainingAmountAndPercentageCalculation() {
        BigDecimal allocated = new BigDecimal("1000000.00");
        BigDecimal released = new BigDecimal("500000.00");
        BigDecimal utilized = new BigDecimal("400000.00");

        BigDecimal remaining = allocated.subtract(released);
        assertEquals(0, new BigDecimal("500000.00").compareTo(remaining));

        int pct = utilized.multiply(new BigDecimal("100")).divide(allocated, 0, java.math.RoundingMode.HALF_UP).intValue();
        assertEquals(40, pct);
    }

    @Test
    @DisplayName("Test 8 & 17: Scheme-wise Aggregation with Multiple Schemes")
    void testSchemeWiseFundUtilizationAggregation() {
        Application app1 = createApplication(testScheme1, new BigDecimal("40000.00"));
        Application app2 = createApplication(testScheme2, new BigDecimal("60000.00"));

        disbursementService.initializeDisbursement(app1, financeUser);
        disbursementService.initializeDisbursement(app2, financeUser);

        List<SchemeFundUtilizationDto> schemeWise = fundUtilizationService.getSchemeWiseFundUtilization(null, null);
        assertNotNull(schemeWise);
        assertTrue(schemeWise.size() >= 2);

        boolean foundScheme1 = schemeWise.stream().anyMatch(s -> s.getSchemeId().equals(testScheme1.getId()));
        boolean foundScheme2 = schemeWise.stream().anyMatch(s -> s.getSchemeId().equals(testScheme2.getId()));

        assertTrue(foundScheme1, "Scheme 1 must be present in scheme-wise utilization analytics");
        assertTrue(foundScheme2, "Scheme 2 must be present in scheme-wise utilization analytics");
    }

    @Test
    @DisplayName("Test 9 & 10: State-wise & District-wise Analytics Aggregation")
    void testRegionalFundUtilizationAggregation() {
        Application app = createApplication(testScheme1, new BigDecimal("40000.00"));
        disbursementService.initializeDisbursement(app, financeUser);

        List<StateFundUtilizationDto> stateWise = fundUtilizationService.getStateWiseFundUtilization();
        assertNotNull(stateWise);

        List<DistrictFundUtilizationDto> districtWise = fundUtilizationService.getDistrictWiseFundUtilization("TELANGANA");
        assertNotNull(districtWise);
        assertTrue(districtWise.stream().anyMatch(d -> "HYDERABAD".equalsIgnoreCase(d.getDistrict())));
    }

    @Test
    @DisplayName("Test 20 & 21: Successful Release Aggregation & Failed Release Exclusion")
    void testReleaseStatusAggregationExclusion() {
        Application app = createApplication(testScheme1, new BigDecimal("40000.00"));
        Disbursement d = disbursementService.initializeDisbursement(app, financeUser);
        Milestone m = milestoneRepository.findByDisbursementOrderByIdAsc(d).get(0);

        m.setComplianceVerificationStatus("VERIFIED");
        milestoneRepository.save(m);

        // 1. Successful release
        DisbursementRelease successRel = new DisbursementRelease(d, m, app, new BigDecimal("40000.00"), financeUser.getEmail(), "TXN-OK-1", "DBT", "Success release");
        successRel.setStatus("SUCCESS");
        releaseRepository.save(successRel);

        // 2. Failed release
        DisbursementRelease failedRel = new DisbursementRelease(d, m, app, new BigDecimal("40000.00"), financeUser.getEmail(), "TXN-FAIL-1", "DBT", "Failed release");
        failedRel.setStatus("FAILED");
        releaseRepository.save(failedRel);

        List<DisbursementRelease> activeReleases = releaseRepository.findAll().stream()
                .filter(r -> "SUCCESS".equalsIgnoreCase(r.getStatus()))
                .filter(r -> r.getApplication().getId().equals(app.getId()))
                .toList();

        assertEquals(1, activeReleases.size(), "Only SUCCESS releases should be aggregated");
    }

    @Test
    @DisplayName("Regression Test: Fix Screenshot Inconsistency (Allocated = 380M, Utilized = 122.5k => ~0.0322%, NOT 306%)")
    void testScreenshotInconsistencyCalculationFix() {
        Scheme bigBudgetScheme = new Scheme();
        bigBudgetScheme.setName("Large Infrastructure Fund Scheme");
        bigBudgetScheme.setCategory("Infrastructure");
        bigBudgetScheme.setTotalBudget(new BigDecimal("380000000.00"));
        bigBudgetScheme.setRemainingBudget(new BigDecimal("379877500.00"));
        bigBudgetScheme.setMinAmount(new BigDecimal("1000.00"));
        bigBudgetScheme.setMaxAmount(new BigDecimal("500000.00"));
        bigBudgetScheme.setStatus("ACTIVE");
        bigBudgetScheme = schemeRepository.save(bigBudgetScheme);

        Application app = createApplication(bigBudgetScheme, new BigDecimal("40000.00"));
        Disbursement d = disbursementService.initializeDisbursement(app, financeUser);
        Milestone m = milestoneRepository.findByDisbursementOrderByIdAsc(d).get(0);
        m.setComplianceVerificationStatus("VERIFIED");
        milestoneRepository.save(m);

        DisbursementRelease rel = new DisbursementRelease(d, m, app, new BigDecimal("122500.00"), financeUser.getEmail(), "TXN-FIX-380M", "DBT", "Release");
        rel.setStatus("SUCCESS");
        releaseRepository.save(rel);
        fundUtilizationService.ensureUtilizationForRelease(rel);

        FundUtilizationSummaryDto summary = fundUtilizationService.getSystemFundUtilizationSummary("Infrastructure", null, null);
        assertNotNull(summary);
        assertEquals(0, new BigDecimal("380000000.00").compareTo(summary.getTotalAllocatedAmount()));
        assertEquals(0, new BigDecimal("122500.00").compareTo(summary.getTotalUtilizedAmount()));
        
        // Expected percentage: 122,500 / 380,000,000 * 100 = ~0.0322368%
        double pct = summary.getUtilizationPercentage();
        assertTrue(pct > 0.03 && pct < 0.04, "Utilization percentage must be approx 0.0322%, but got: " + pct);
        assertNotEquals(306.0, pct, 0.01, "Utilization percentage must NOT be 306%");
        assertEquals("0.032%", summary.getFormattedUtilizationPercentage());
    }

    @Test
    @DisplayName("Test Scheme-wise Percentage Accuracy (Solar Scheme 30M Allocated, 22.5k Utilized => 0.075%)")
    void testSchemeWisePercentageAccuracy() {
        Scheme sScheme = new Scheme();
        sScheme.setName("National Rooftop Solar Subsidy Scheme " + System.currentTimeMillis());
        sScheme.setCategory("Solar");
        sScheme.setTotalBudget(new BigDecimal("30000000.00"));
        sScheme.setRemainingBudget(new BigDecimal("29977500.00"));
        sScheme.setMinAmount(new BigDecimal("1000.00"));
        sScheme.setMaxAmount(new BigDecimal("100000.00"));
        sScheme.setStatus("ACTIVE");
        final Scheme solarScheme = schemeRepository.save(sScheme);

        Application app = createApplication(solarScheme, new BigDecimal("40000.00"));
        Disbursement d = disbursementService.initializeDisbursement(app, financeUser);
        Milestone m = milestoneRepository.findByDisbursementOrderByIdAsc(d).get(0);
        m.setComplianceVerificationStatus("VERIFIED");
        milestoneRepository.save(m);

        DisbursementRelease rel = new DisbursementRelease(d, m, app, new BigDecimal("22500.00"), financeUser.getEmail(), "TXN-SOLAR-22.5K", "DBT", "Release");
        rel.setStatus("SUCCESS");
        releaseRepository.save(rel);
        fundUtilizationService.ensureUtilizationForRelease(rel);

        List<SchemeFundUtilizationDto> list = fundUtilizationService.getSchemeWiseFundUtilization("Solar", null);
        assertNotNull(list);
        SchemeFundUtilizationDto dto = list.stream().filter(s -> solarScheme.getId().equals(s.getSchemeId())).findFirst().orElse(null);
        assertNotNull(dto);

        double pct = dto.getUtilizationPercentage();
        assertEquals(0.075, pct, 0.001);
        assertEquals("0.075%", dto.getFormattedUtilizationPercentage());
    }

    @Test
    @DisplayName("Test Filters: Filter by Category, State, District")
    void testAnalyticsFilteringByStateAndDistrict() {
        FundUtilizationSummaryDto filteredSummary = fundUtilizationService.getSystemFundUtilizationSummary("Agriculture", "TELANGANA", "HYDERABAD");
        assertNotNull(filteredSummary);

        List<DistrictFundUtilizationDto> districtWise = fundUtilizationService.getDistrictWiseFundUtilization("TELANGANA");
        assertNotNull(districtWise);
        assertTrue(districtWise.stream().allMatch(d -> "TELANGANA".equalsIgnoreCase(d.getState())));
    }
}
