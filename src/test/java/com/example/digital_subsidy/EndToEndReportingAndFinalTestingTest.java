package com.example.digital_subsidy;

import com.example.digital_subsidy.controller.ReportController;
import com.example.digital_subsidy.dto.*;
import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.*;
import com.example.digital_subsidy.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class EndToEndReportingAndFinalTestingTest {

    @Autowired
    private ReportService reportService;

    @Autowired
    private ReportController reportController;

    @Autowired
    private FundUtilizationService fundUtilizationService;

    @Autowired
    private DisbursementService disbursementService;

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BeneficiaryRepository beneficiaryRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private OfficerRepository officerRepository;

    private Scheme testScheme;
    private User beneficiaryUser;
    private Beneficiary beneficiary;
    private User adminUser;
    private User financeUser;
    private User telanganaOfficerUser;
    private Officer telanganaOfficer;
    private User fieldOfficerUser;

    @BeforeEach
    void setUp() {
        long timestamp = System.currentTimeMillis();

        adminUser = createUserIfNotExist("admin_phase4_" + timestamp + "@gov.in", "ROLE_ADMIN");
        financeUser = createUserIfNotExist("finance_phase4_" + timestamp + "@gov.in", "ROLE_FINANCE_APPROVER");
        fieldOfficerUser = createUserIfNotExist("field_phase4_" + timestamp + "@gov.in", "ROLE_FIELD_OFFICER");

        telanganaOfficerUser = createUserIfNotExist("do_telangana_" + timestamp + "@gov.in", "ROLE_DISTRICT_OFFICER");
        telanganaOfficer = officerRepository.findByUserEmail(telanganaOfficerUser.getEmail())
                .orElseGet(() -> officerRepository.save(new Officer(telanganaOfficerUser, "DO Telangana", telanganaOfficerUser.getEmail(), "98765" + (timestamp % 100000), "DISTRICT_OFFICER", "TELANGANA", "MEDAK")));

        beneficiaryUser = createUserIfNotExist("ben_phase4_" + timestamp + "@gov.in", "ROLE_BENEFICIARY");
        beneficiary = beneficiaryRepository.findByUser(beneficiaryUser)
                .orElseGet(() -> {
                    Beneficiary b = new Beneficiary();
                    b.setUser(beneficiaryUser);
                    b.setState("TELANGANA");
                    b.setDistrict("MEDAK");
                    b.setIdentityType("AADHAAR");
                    b.setIdentityNumber("123456" + (timestamp % 1000000));
                    b.setCategory("GENERAL");
                    b.setGender("FEMALE");
                    b.setAge(30);
                    b.setAddress("H.No 1-2-3");
                    b.setPincode("502110");
                    return beneficiaryRepository.save(b);
                });

        testScheme = new Scheme();
        testScheme.setName("Phase 4 Test Agriculture Grant " + timestamp);
        testScheme.setCategory("AGRICULTURE");
        testScheme.setTotalBudget(new BigDecimal("1000000.00"));
        testScheme.setRemainingBudget(new BigDecimal("1000000.00"));
        testScheme.setMinAmount(new BigDecimal("1000.00"));
        testScheme.setMaxAmount(new BigDecimal("100000.00"));
        testScheme.setStatus("ACTIVE");
        testScheme = schemeRepository.save(testScheme);
    }

    private static final java.util.concurrent.atomic.AtomicLong mobileCounter = new java.util.concurrent.atomic.AtomicLong(9999100000L);

    private User createUserIfNotExist(String email, String role) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User();
            u.setEmail(email);
            u.setPassword("password");
            u.setFullName("Test User " + role);
            u.setMobileNumber(String.valueOf(mobileCounter.incrementAndGet()));
            u.setEnabled(true);
            return userRepository.save(u);
        });
    }

    private void authenticate(User user, String role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user.getEmail(), null, List.of(new SimpleGrantedAuthority(role))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Report 1: Fund Utilization Report Generation")
    void testFundUtilizationReport() {
        authenticate(adminUser, "ROLE_ADMIN");
        List<SchemeFundUtilizationDto> report = reportService.generateFundUtilizationReport(testScheme.getId(), "AGRICULTURE", "TELANGANA", "MEDAK");
        assertNotNull(report);
        assertFalse(report.isEmpty());
        SchemeFundUtilizationDto dto = report.get(0);
        assertEquals(testScheme.getName(), dto.getSchemeName());
        assertEquals("AGRICULTURE", dto.getCategory());
        assertNotNull(dto.getAllocatedAmount());
        assertNotNull(dto.getUtilizationPercentage());
    }

    @Test
    @DisplayName("Report 2: Regional Fund Hierarchy Report Generation")
    void testRegionalFundReport() {
        authenticate(adminUser, "ROLE_ADMIN");

        Application app = new Application();
        app.setApplicationNumber("APP-REG-01-" + System.currentTimeMillis());
        app.setBeneficiary(beneficiary);
        app.setScheme(testScheme);
        app.setRequestedAmount(new BigDecimal("30000.00"));
        app.setSanctionedAmount(new BigDecimal("30000.00"));
        app.setStatus("APPROVED");
        app.setCurrentStage("DISBURSEMENT");
        applicationRepository.save(app);

        List<RegionalFundReportDto> report = reportService.generateRegionalFundReport("TELANGANA", "MEDAK", testScheme.getId());
        assertNotNull(report);
        assertFalse(report.isEmpty());
        RegionalFundReportDto dto = report.get(0);
        assertEquals("TELANGANA", dto.getState());
        assertEquals("MEDAK", dto.getDistrict());
        assertEquals(testScheme.getName(), dto.getSchemeName());
        assertEquals(1, dto.getApplicationCount());
        assertEquals(new BigDecimal("30000.00"), dto.getSanctionedAmount());
    }

    @Test
    @DisplayName("Report 3 & 4: Disbursement & Compliance Reports Generation")
    void testDisbursementAndComplianceReports() {
        authenticate(adminUser, "ROLE_ADMIN");

        Application app = new Application();
        app.setApplicationNumber("APP-DISB-01-" + System.currentTimeMillis());
        app.setBeneficiary(beneficiary);
        app.setScheme(testScheme);
        app.setRequestedAmount(new BigDecimal("35000.00"));
        app.setSanctionedAmount(new BigDecimal("35000.00"));
        app.setStatus("APPROVED");
        app.setCurrentStage("DISBURSEMENT");
        app = applicationRepository.save(app);

        Disbursement d = disbursementService.initializeDisbursement(app, adminUser);

        List<DisbursementReportDto> disbReport = reportService.generateDisbursementReport(testScheme.getId(), null, null, null);
        assertNotNull(disbReport);
        assertFalse(disbReport.isEmpty());
        DisbursementReportDto dDto = disbReport.get(0);
        assertEquals(app.getApplicationNumber(), dDto.getApplicationNumber());
        assertEquals(1, dDto.getTotalTerms());
        assertEquals(0, dDto.getReleasedTerms());

        List<ComplianceReportDto> compReport = reportService.generateComplianceReport(testScheme.getId(), null, null, null);
        assertNotNull(compReport);
        assertFalse(compReport.isEmpty());
        ComplianceReportDto cDto = compReport.get(0);
        assertEquals(app.getApplicationNumber(), cDto.getApplicationNumber());
        assertEquals("PENDING", cDto.getComplianceStatus());
    }

    @Test
    @DisplayName("Report 5: Application Lifecycle Report Breakdown")
    void testApplicationLifecycleReport() {
        authenticate(adminUser, "ROLE_ADMIN");

        Application app = new Application();
        app.setApplicationNumber("APP-LIFE-01-" + System.currentTimeMillis());
        app.setBeneficiary(beneficiary);
        app.setScheme(testScheme);
        app.setRequestedAmount(new BigDecimal("50000.00"));
        app.setStatus("SUBMITTED");
        app.setCurrentStage("SUBMITTED");
        applicationRepository.save(app);

        ApplicationLifecycleReportDto dto = reportService.generateApplicationLifecycleReport("TELANGANA", "MEDAK", testScheme.getId());
        assertNotNull(dto);
        assertTrue(dto.getTotalApplications() >= 1);
        assertTrue(dto.getSubmittedCount() >= 1);
    }

    @Test
    @DisplayName("PDF and CSV Export Verification")
    void testPdfAndCsvExports() throws Exception {
        authenticate(adminUser, "ROLE_ADMIN");
        List<SchemeFundUtilizationDto> report = reportService.generateFundUtilizationReport(testScheme.getId(), null, null, null);

        byte[] csv = reportService.exportReportToCsv("FUND_UTILIZATION", report);
        assertNotNull(csv);
        assertTrue(csv.length > 0);
        String csvString = new String(csv);
        assertTrue(csvString.contains("Scheme Name"));

        byte[] pdf = reportService.exportReportToPdf("FUND_UTILIZATION", report);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @DisplayName("Security & IDOR Isolation Test: District Officer Boundary Violation")
    void testIdorAndSecurityRestrictions() {
        // 1. Beneficiary Attempt -> Expect AccessDeniedException
        authenticate(beneficiaryUser, "ROLE_BENEFICIARY");
        assertThrows(AccessDeniedException.class, () -> {
            reportController.viewReports("FUND_UTILIZATION", null, null, null, null, null, null, SecurityContextHolder.getContext().getAuthentication(), new ConcurrentModel());
        });

        // 2. Field Officer Attempt -> Expect AccessDeniedException
        authenticate(fieldOfficerUser, "ROLE_FIELD_OFFICER");
        assertThrows(AccessDeniedException.class, () -> {
            reportController.viewReports("FUND_UTILIZATION", null, null, null, null, null, null, SecurityContextHolder.getContext().getAuthentication(), new ConcurrentModel());
        });

        // 3. District Officer Telangana accessing Andhra Pradesh -> Expect AccessDeniedException (IDOR Protection)
        authenticate(telanganaOfficerUser, "ROLE_DISTRICT_OFFICER");
        assertThrows(AccessDeniedException.class, () -> {
            reportController.viewReports("FUND_UTILIZATION", null, null, "Andhra Pradesh", "Guntur", null, null, SecurityContextHolder.getContext().getAuthentication(), new ConcurrentModel());
        });

        // 4. District Officer Telangana accessing TELANGANA (MEDAK) -> Allowed
        Model model = new ConcurrentModel();
        String view = reportController.viewReports("FUND_UTILIZATION", null, null, "TELANGANA", "MEDAK", null, null, SecurityContextHolder.getContext().getAuthentication(), model);
        assertEquals("reports/index", view);
    }

    @Test
    @DisplayName("Phase 1 Regression: Automatic Disbursement Phase Rule Boundary (<=40,000 vs >40,000)")
    void testDisbursementPhaseRuleBoundaryRegression() {
        authenticate(adminUser, "ROLE_ADMIN");

        // Case A: Sanctioned = 40,000 -> 1 Phase
        Application app1 = new Application();
        app1.setApplicationNumber("APP-B-40K-" + System.currentTimeMillis());
        app1.setBeneficiary(beneficiary);
        app1.setScheme(testScheme);
        app1.setRequestedAmount(new BigDecimal("50000.00"));
        app1.setSanctionedAmount(new BigDecimal("40000.00"));
        app1.setStatus("APPROVED");
        app1 = applicationRepository.save(app1);

        Disbursement d1 = disbursementService.initializeDisbursement(app1, adminUser);
        List<Milestone> m1 = milestoneRepository.findByDisbursementOrderByIdAsc(d1);
        assertEquals(1, m1.size());
        assertEquals(0, new BigDecimal("40000.00").compareTo(m1.get(0).getMilestoneAmount()));

        // Case B: Sanctioned = 40,001 -> 3 Phases
        Application app2 = new Application();
        app2.setApplicationNumber("APP-B-40K1-" + System.currentTimeMillis());
        app2.setBeneficiary(beneficiary);
        app2.setScheme(testScheme);
        app2.setRequestedAmount(new BigDecimal("40001.00"));
        app2.setSanctionedAmount(new BigDecimal("40001.00"));
        app2.setStatus("APPROVED");
        app2 = applicationRepository.save(app2);

        Disbursement d2 = disbursementService.initializeDisbursement(app2, adminUser);
        List<Milestone> m2 = milestoneRepository.findByDisbursementOrderByIdAsc(d2);
        assertEquals(3, m2.size());

        // Case C: Sanctioned null -> Expect Exception & no disbursement
        Application app3 = new Application();
        app3.setApplicationNumber("APP-B-NULL-" + System.currentTimeMillis());
        app3.setBeneficiary(beneficiary);
        app3.setScheme(testScheme);
        app3.setRequestedAmount(new BigDecimal("50000.00"));
        app3.setSanctionedAmount(null);
        app3.setStatus("APPROVED");
        app3 = applicationRepository.save(app3);

        final Application targetApp3 = app3;
        assertThrows(IllegalArgumentException.class, () -> disbursementService.initializeDisbursement(targetApp3, adminUser));
    }

    @Test
    @DisplayName("Phase 2 & 3 Regression: Compliance Verification Blocking & Fund Aggregation")
    void testComplianceBlockingAndFundAggregationRegression() {
        authenticate(adminUser, "ROLE_ADMIN");

        Application app = new Application();
        app.setApplicationNumber("APP-COMP-REG-" + System.currentTimeMillis());
        app.setBeneficiary(beneficiary);
        app.setScheme(testScheme);
        app.setRequestedAmount(new BigDecimal("30000.00"));
        app.setSanctionedAmount(new BigDecimal("30000.00"));
        app.setStatus("APPROVED");
        app.setCurrentStage("DISBURSEMENT");
        app = applicationRepository.save(app);

        Disbursement d = disbursementService.initializeDisbursement(app, adminUser);
        Milestone m = milestoneRepository.findByDisbursementOrderByIdAsc(d).get(0);

        // Unverified compliance attempt to release -> expect failure
        StageReleaseDto dto = new StageReleaseDto();
        dto.setMilestoneId(m.getId());
        dto.setApplicationId(app.getId());

        assertThrows(IllegalStateException.class, () -> disbursementService.releaseMilestone(dto, adminUser.getEmail()));

        // Verify compliance
        m.setComplianceVerificationStatus("VERIFIED");
        milestoneRepository.save(m);

        // Now release -> should succeed
        Milestone rel = disbursementService.releaseMilestone(dto, adminUser.getEmail());
        assertNotNull(rel);
        assertTrue("RELEASED".equalsIgnoreCase(rel.getCompletionStatus()) || "COMPLETED".equalsIgnoreCase(rel.getCompletionStatus()));
    }

    @Test
    @DisplayName("End-to-End Workflow Integration Test")
    void testFullEndToEndWorkflow() throws Exception {
        authenticate(adminUser, "ROLE_ADMIN");

        // 1. Application Creation
        Application app = new Application();
        app.setApplicationNumber("APP-E2E-P4-" + System.currentTimeMillis());
        app.setBeneficiary(beneficiary);
        app.setScheme(testScheme);
        app.setRequestedAmount(new BigDecimal("35000.00"));
        app.setStatus("SUBMITTED");
        app.setCurrentStage("SUBMITTED");
        app = applicationRepository.save(app);

        // 2. Verification Steps to APPROVED
        app.setSanctionedAmount(new BigDecimal("35000.00"));
        app.setStatus("APPROVED");
        app.setCurrentStage("DISBURSEMENT");
        app = applicationRepository.save(app);

        // 3. Disbursement Plan Creation
        Disbursement d = disbursementService.initializeDisbursement(app, adminUser);
        assertNotNull(d);
        assertTrue("PENDING".equalsIgnoreCase(d.getStatus()) || "IN_PROGRESS".equalsIgnoreCase(d.getStatus()));

        // 4. Compliance & Release
        Milestone m = milestoneRepository.findByDisbursementOrderByIdAsc(d).get(0);
        m.setComplianceVerificationStatus("VERIFIED");
        milestoneRepository.save(m);

        StageReleaseDto dto = new StageReleaseDto();
        dto.setMilestoneId(m.getId());
        dto.setApplicationId(app.getId());

        Milestone rel = disbursementService.releaseMilestone(dto, adminUser.getEmail());
        assertNotNull(rel);

        // 5. Report verification
        List<DisbursementReportDto> dReports = reportService.generateDisbursementReport(testScheme.getId(), null, null, null);
        assertNotNull(dReports);
        assertFalse(dReports.isEmpty());

        List<ComplianceReportDto> cReports = reportService.generateComplianceReport(testScheme.getId(), null, null, null);
        assertNotNull(cReports);
        assertFalse(cReports.isEmpty());

        ResponseEntity<byte[]> exportResp = reportController.exportReport("DISBURSEMENT", "csv", testScheme.getId(), null, null, null, null, null, SecurityContextHolder.getContext().getAuthentication());
        assertEquals(200, exportResp.getStatusCode().value());
        assertNotNull(exportResp.getBody());
    }
}
