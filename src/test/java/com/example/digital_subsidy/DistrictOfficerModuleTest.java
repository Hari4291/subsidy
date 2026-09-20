package com.example.digital_subsidy;

import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
public class DistrictOfficerModuleTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OfficerRepository officerRepository;

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private BeneficiaryRepository beneficiaryRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ApplicationEscalationRepository escalationRepository;

    @Autowired
    private AnomalyFlagRepository anomalyFlagRepository;

    @Autowired
    private GrievanceRepository grievanceRepository;

    @Autowired
    private RoleRepository roleRepository;

    private User districtUser;
    private Officer districtOfficer;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        districtUser = userRepository.findByEmail("district.officer@gmail.com")
                .or(() -> userRepository.findByEmail("district@gov.in"))
                .orElseGet(() -> {
                    long ts = System.currentTimeMillis();
                    User u = new User();
                    u.setFullName("District Collector");
                    u.setEmail("district.officer@gmail.com");
                    u.setPassword("password");
                    u.setMobileNumber("987" + (ts % 10000000L));
                    return userRepository.save(u);
                });

        Role doRole = roleRepository.findByName("ROLE_DISTRICT_OFFICER")
                .or(() -> roleRepository.findByName("DISTRICT_OFFICER"))
                .orElseGet(() -> roleRepository.save(new Role("ROLE_DISTRICT_OFFICER")));

        if (!districtUser.getRoles().contains(doRole)) {
            districtUser.getRoles().add(doRole);
            districtUser = userRepository.save(districtUser);
        }

        districtOfficer = officerRepository.findByUserEmail(districtUser.getEmail()).orElseGet(() -> {
            Officer o = new Officer(districtUser, "District Collector", districtUser.getEmail(), districtUser.getMobileNumber(), "DISTRICT_OFFICER", "Bihar", "Patna");
            return officerRepository.save(o);
        });
    }

    @Test
    @DisplayName("District Officer Dashboard renders with district metrics and no nulls")
    @WithMockUser(username = "district.officer@gmail.com", roles = {"DISTRICT_OFFICER"})
    void testDistrictOfficerDashboard() throws Exception {
        mockMvc.perform(get("/district-officer/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("district-officer/dashboard"))
                .andExpect(model().attributeExists("districtName", "metrics", "pendingApplications"));
    }

    @Test
    @DisplayName("District Officer Escalations page renders successfully")
    @WithMockUser(username = "district.officer@gmail.com", roles = {"DISTRICT_OFFICER"})
    void testEscalationsPage() throws Exception {
        mockMvc.perform(get("/district-officer/escalations"))
                .andExpect(status().isOk())
                .andExpect(view().name("district-officer/escalations"))
                .andExpect(model().attributeExists("escalations", "districtName"));
    }

    @Test
    @DisplayName("District Officer Anomalies page renders successfully")
    @WithMockUser(username = "district.officer@gmail.com", roles = {"DISTRICT_OFFICER"})
    void testAnomaliesPage() throws Exception {
        mockMvc.perform(get("/district-officer/anomalies"))
                .andExpect(status().isOk())
                .andExpect(view().name("district-officer/anomalies"))
                .andExpect(model().attributeExists("anomalies", "districtName"));
    }

    @Test
    @DisplayName("District Officer Budget page renders successfully")
    @WithMockUser(username = "district.officer@gmail.com", roles = {"DISTRICT_OFFICER"})
    void testBudgetPage() throws Exception {
        mockMvc.perform(get("/district-officer/budget"))
                .andExpect(status().isOk())
                .andExpect(view().name("district-officer/budget"))
                .andExpect(model().attributeExists("budget", "districtName"));
    }

    @Test
    @DisplayName("District Officer Grievances page renders successfully")
    @WithMockUser(username = "district.officer@gmail.com", roles = {"DISTRICT_OFFICER"})
    void testGrievancesPage() throws Exception {
        mockMvc.perform(get("/district-officer/grievances"))
                .andExpect(status().isOk())
                .andExpect(view().name("district-officer/grievances"))
                .andExpect(model().attributeExists("grievances", "districtName"));
    }

    @Test
    @DisplayName("District Officer Field Officers page renders successfully")
    @WithMockUser(username = "district.officer@gmail.com", roles = {"DISTRICT_OFFICER"})
    void testOfficersPage() throws Exception {
        mockMvc.perform(get("/district-officer/officers"))
                .andExpect(status().isOk())
                .andExpect(view().name("district-officer/officers"))
                .andExpect(model().attributeExists("officers", "pendingApplications", "districtName"));
    }

    @Test
    @DisplayName("District Officer Reports & Analytics page renders successfully")
    @WithMockUser(username = "district.officer@gmail.com", roles = {"DISTRICT_OFFICER"})
    void testReportsPage() throws Exception {
        mockMvc.perform(get("/district-officer/reports"))
                .andExpect(status().isOk())
                .andExpect(view().name("district-officer/reports"))
                .andExpect(model().attributeExists("metrics", "districtName"));
    }

    @Test
    @DisplayName("District Officer Audit page renders successfully")
    @WithMockUser(username = "district.officer@gmail.com", roles = {"DISTRICT_OFFICER"})
    void testAuditPage() throws Exception {
        mockMvc.perform(get("/district-officer/audit"))
                .andExpect(status().isOk())
                .andExpect(view().name("district-officer/audit"))
                .andExpect(model().attributeExists("audits", "districtName"));
    }

    @Test
    @DisplayName("Resolve Escalation action redirects to escalations with success message")
    @WithMockUser(username = "district.officer@gmail.com", roles = {"DISTRICT_OFFICER"})
    void testResolveEscalationAction() throws Exception {
        Application app = createTestApplication();
        ApplicationEscalation escalation = new ApplicationEscalation(app, 5, 7, "ESCALATED", "Ground signature discrepancy");
        escalation = escalationRepository.save(escalation);

        mockMvc.perform(post("/district-officer/escalation/resolve")
                        .with(csrf())
                        .param("escalationId", escalation.getId().toString())
                        .param("status", "RESOLVED")
                        .param("remarks", "Issue resolved after ground review"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/district-officer/escalations"));
    }

    @Test
    @DisplayName("Resolve Anomaly action redirects to anomalies with success message")
    @WithMockUser(username = "district.officer@gmail.com", roles = {"DISTRICT_OFFICER"})
    void testResolveAnomalyAction() throws Exception {
        Application app = createTestApplication();
        AnomalyFlag flag = new AnomalyFlag(app, "INCOME_GRANT_MISMATCH", "High requested amount flagged", "HIGH");
        flag = anomalyFlagRepository.save(flag);

        mockMvc.perform(post("/district-officer/anomaly/resolve")
                        .with(csrf())
                        .param("anomalyId", flag.getId().toString())
                        .param("status", "RESOLVED")
                        .param("remarks", "Verified document proof submitted"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/district-officer/anomalies"));
    }

    @Test
    @DisplayName("Resolve Grievance action redirects to grievances with success message")
    @WithMockUser(username = "district.officer@gmail.com", roles = {"DISTRICT_OFFICER"})
    void testResolveGrievanceAction() throws Exception {
        Application app = createTestApplication();
        Grievance grievance = new Grievance();
        grievance.setGrievanceNumber("GRV-TEST-" + System.currentTimeMillis());
        grievance.setBeneficiary(app.getBeneficiary());
        grievance.setApplication(app);
        grievance.setCategory("VERIFICATION_DELAY");
        grievance.setDescription("Verification pending over 15 days");
        grievance = grievanceRepository.save(grievance);

        mockMvc.perform(post("/district-officer/grievance/resolve")
                        .with(csrf())
                        .param("grievanceId", grievance.getId().toString())
                        .param("status", "RESOLVED")
                        .param("remarks", "Expedited and verified by field team"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/district-officer/grievances"));
    }

    private Application createTestApplication() {
        long ts = System.currentTimeMillis();
        User u = new User();
        u.setEmail("beneficiary" + ts + "@example.com");
        u.setFullName("Test Beneficiary " + ts);
        u.setPassword("password");
        u.setMobileNumber("98" + (ts % 100000000L));
        u = userRepository.save(u);

        Beneficiary b = new Beneficiary();
        b.setUser(u);
        b.setAge(30);
        b.setGender("Male");
        b.setCategory("GENERAL");
        b.setPincode("800001");
        b.setIdentityType("AADHAAR");
        b.setIdentityNumber("1234567" + (ts % 100000L));
        b.setDistrict("Patna");
        b.setState("Bihar");
        b.setAddress("Test Address");
        b = beneficiaryRepository.save(b);

        Scheme s = schemeRepository.findAll().get(0);

        Application app = new Application();
        app.setApplicationNumber("APP-TEST-" + ts);
        app.setBeneficiary(b);
        app.setScheme(s);
        app.setRequestedAmount(new BigDecimal("50000"));
        app.setCurrentStage("DISTRICT_REVIEW");
        app.setStatus("PENDING");
        return applicationRepository.save(app);
    }
}
