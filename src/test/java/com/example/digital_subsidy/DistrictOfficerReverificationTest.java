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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
public class DistrictOfficerReverificationTest {

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
    private ApplicationRestrictionRepository restrictionRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private RoleRepository roleRepository;

    private User doUser;
    private User foUser;
    private Officer fieldOfficer;
    private Scheme testScheme;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        long ts = System.currentTimeMillis();

        // 1. District Officer User & Role
        final String doEmail = "do_test@gov.in";
        doUser = userRepository.findByEmail(doEmail).orElseGet(() -> {
            User u = new User();
            u.setFullName("District Collector Test");
            u.setEmail(doEmail);
            u.setPassword("password");
            u.setMobileNumber("981" + (ts % 10000000L));
            u.setEnabled(true);
            return userRepository.save(u);
        });

        Role doRole = roleRepository.findByName("ROLE_DISTRICT_OFFICER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_DISTRICT_OFFICER")));
        if (!doUser.getRoles().contains(doRole)) {
            doUser.getRoles().add(doRole);
            doUser = userRepository.save(doUser);
        }

        final User finalDoUser = doUser;
        Officer doOfficer = officerRepository.findByUserEmail(doEmail).orElseGet(() -> {
            Officer o = new Officer(finalDoUser, finalDoUser.getFullName(), finalDoUser.getEmail(), finalDoUser.getMobileNumber(), "DISTRICT_OFFICER", "Telangana", "Hyderabad");
            return officerRepository.save(o);
        });

        // 2. Field Officer User & Role
        final String foEmail = "fo_test@gov.in";
        foUser = userRepository.findByEmail(foEmail).orElseGet(() -> {
            User u = new User();
            u.setFullName("Field Inspector Test");
            u.setEmail(foEmail);
            u.setPassword("password");
            u.setMobileNumber("982" + (ts % 10000000L));
            u.setEnabled(true);
            return userRepository.save(u);
        });

        Role foRole = roleRepository.findByName("ROLE_FIELD_OFFICER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_FIELD_OFFICER")));
        if (!foUser.getRoles().contains(foRole)) {
            foUser.getRoles().add(foRole);
            foUser = userRepository.save(foUser);
        }

        final User finalFoUser = foUser;
        fieldOfficer = officerRepository.findByUserEmail(foEmail).orElseGet(() -> {
            Officer o = new Officer(finalFoUser, finalFoUser.getFullName(), finalFoUser.getEmail(), finalFoUser.getMobileNumber(), "FIELD_OFFICER", "Telangana", "Hyderabad");
            return officerRepository.save(o);
        });

        // 3. Scheme
        testScheme = new Scheme();
        testScheme.setName("Reverification Test Scheme " + ts);
        testScheme.setCategory("Agriculture");
        testScheme.setMinAmount(new BigDecimal("10000.00"));
        testScheme.setMaxAmount(new BigDecimal("100000.00"));
        testScheme.setTotalBudget(new BigDecimal("1000000.00"));
        testScheme.setRemainingBudget(new BigDecimal("1000000.00"));
        testScheme.setRejectionCooldownDays(30);
        testScheme.setStatus("ACTIVE");
        testScheme = schemeRepository.save(testScheme);
    }

    private Application createTestApplication(String suffix) {
        long ts = System.currentTimeMillis();
        User benUser = new User();
        benUser.setFullName("Beneficiary " + suffix);
        benUser.setEmail("ben_" + suffix + "_" + ts + "@gmail.com");
        benUser.setPassword("password");
        benUser.setMobileNumber("983" + (ts % 10000000L));
        benUser.setEnabled(true);
        benUser = userRepository.save(benUser);

        Role benRole = roleRepository.findByName("ROLE_BENEFICIARY")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_BENEFICIARY")));
        benUser.getRoles().add(benRole);
        benUser = userRepository.save(benUser);

        Beneficiary ben = new Beneficiary();
        ben.setUser(benUser);
        ben.setAge(30);
        ben.setGender("Male");
        ben.setCategory("GENERAL");
        ben.setState("Telangana");
        ben.setDistrict("Hyderabad");
        ben.setAddress("House No " + suffix + ", Hyderabad");
        ben.setPincode("500001");
        ben.setIdentityType("Aadhaar");
        ben.setIdentityNumber("99998888" + (ts % 10000L));
        ben = beneficiaryRepository.save(ben);

        Application app = new Application();
        app.setApplicationNumber("APP-REVERIFY-" + suffix + "-" + ts);
        app.setBeneficiary(ben);
        app.setScheme(testScheme);
        app.setRequestedAmount(new BigDecimal("50000.00"));
        app.setSanctionedAmount(new BigDecimal("45000.00"));
        app.setCurrentStage("DISTRICT_REVIEW");
        app.setStatus("PENDING");
        app.setAssignedOfficer(fieldOfficer);
        return applicationRepository.save(app);
    }

    @Test
    @DisplayName("Send for Re-Verification via /district-officer/review/{id} succeeds with correct redirect, status & message")
    @WithMockUser(username = "do_test@gov.in", roles = {"DISTRICT_OFFICER"})
    void testSendForReverification_ViaReviewEndpoint() throws Exception {
        Application app = createTestApplication("01");
        Long appId = app.getId();

        mockMvc.perform(post("/district-officer/review/" + appId)
                        .with(csrf())
                        .param("applicationId", appId.toString())
                        .param("action", "REQUEST_RE_VERIFICATION")
                        .param("remarks", "Land property document blurry, please re-inspect in field"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/district-officer/dashboard?verified=true"))
                .andExpect(flash().attribute("successMessage", "Application sent to Field Officer for re-verification successfully."));

        // Verify Database State
        Application updatedApp = applicationRepository.findById(appId).orElseThrow();
        assertEquals("RE_VERIFICATION_REQUIRED", updatedApp.getStatus());
        assertEquals("FIELD_VERIFICATION", updatedApp.getCurrentStage());
        assertEquals("Land property document blurry, please re-inspect in field", updatedApp.getRejectionReason());
        assertEquals(new BigDecimal("45000.00"), updatedApp.getSanctionedAmount(), "Sanctioned amount must NOT be altered during re-verification.");
        assertEquals(app.getId(), updatedApp.getId(), "Application ID must remain unchanged.");

        // Verify NO Restriction Cooldown Created
        List<ApplicationRestriction> restrictions = restrictionRepository.findByApplicationId(appId);
        assertTrue(restrictions.isEmpty(), "Re-verification MUST NOT create any rejection cooldown restriction.");

        // Verify Audit Log
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        boolean auditExists = auditLogs.stream().anyMatch(l -> l.getEntityId() != null && l.getEntityId().equals(appId) && "RE_VERIFICATION_REQUESTED".equalsIgnoreCase(l.getAction()));
        assertTrue(auditExists, "Audit log for RE_VERIFICATION_REQUESTED must be logged.");

        // Verify Notification for Field Officer
        List<Notification> notifications = notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(foUser.getId());
        assertFalse(notifications.isEmpty(), "Notification for assigned Field Officer must be created.");
        assertTrue(notifications.get(0).getMessage().contains("returned for re-verification"), "Notification text must indicate re-verification assignment.");
    }

    @Test
    @DisplayName("Send for Re-Verification via /officer/verify endpoint succeeds")
    @WithMockUser(username = "do_test@gov.in", roles = {"DISTRICT_OFFICER"})
    void testSendForReverification_ViaOfficerVerifyEndpoint() throws Exception {
        Application app = createTestApplication("02");
        Long appId = app.getId();

        mockMvc.perform(post("/officer/verify")
                        .with(csrf())
                        .param("applicationId", appId.toString())
                        .param("role", "DISTRICT_OFFICER")
                        .param("action", "REQUEST_RE_VERIFICATION")
                        .param("remarks", "Bank account branch code mismatch"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/district-officer/dashboard?verified=true"))
                .andExpect(flash().attribute("successMessage", "Application sent to Field Officer for re-verification successfully."));

        Application updatedApp = applicationRepository.findById(appId).orElseThrow();
        assertEquals("RE_VERIFICATION_REQUIRED", updatedApp.getStatus());
        assertEquals("FIELD_VERIFICATION", updatedApp.getCurrentStage());
    }

    @Test
    @DisplayName("Field Officer dashboard displays re-verification application under dedicated section")
    @WithMockUser(username = "fo_test@gov.in", roles = {"FIELD_OFFICER"})
    void testFieldOfficerDashboardVisibility() throws Exception {
        Application app = createTestApplication("03");
        app.setStatus("RE_VERIFICATION_REQUIRED");
        app.setCurrentStage("FIELD_VERIFICATION");
        app.setRejectionReason("Verify crop damage proof again");
        applicationRepository.save(app);

        mockMvc.perform(get("/field-officer/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("field-officer/dashboard"))
                .andExpect(model().attributeExists("reverificationApplications", "foReverificationDetailsMap"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Applications Sent for Re-Verification by District Officer")));
    }

    @Test
    @DisplayName("Send for Re-Verification requires mandatory remarks")
    @WithMockUser(username = "do_test@gov.in", roles = {"DISTRICT_OFFICER"})
    void testSendForReverification_MissingRemarksFails() throws Exception {
        Application app = createTestApplication("04");
        Long appId = app.getId();

        mockMvc.perform(post("/district-officer/review/" + appId)
                        .with(csrf())
                        .param("applicationId", appId.toString())
                        .param("action", "REQUEST_RE_VERIFICATION")
                        .param("remarks", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/district-officer/review/" + appId))
                .andExpect(flash().attributeExists("errorMessage"));

        Application unchangedApp = applicationRepository.findById(appId).orElseThrow();
        assertEquals("PENDING", unchangedApp.getStatus(), "Status must remain unchanged when mandatory remarks are missing.");
    }

    @Test
    @DisplayName("Role authorization: Non-District Officer role gets access denied on re-verification")
    @WithMockUser(username = "fo_test@gov.in", roles = {"FIELD_OFFICER"})
    void testRoleAuthorizationEnforced() throws Exception {
        Application app = createTestApplication("05");
        Long appId = app.getId();

        mockMvc.perform(post("/officer/verify")
                        .with(csrf())
                        .param("applicationId", appId.toString())
                        .param("role", "DISTRICT_OFFICER")
                        .param("action", "REQUEST_RE_VERIFICATION")
                        .param("remarks", "Unauthorized attempt"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", "Access Denied: Only District Officers are authorized to perform this review action."));
    }

    @Test
    @DisplayName("Multiple applications can be sent for re-verification independently")
    @WithMockUser(username = "do_test@gov.in", roles = {"DISTRICT_OFFICER"})
    void testMultipleApplicationsReverification() throws Exception {
        Application app1 = createTestApplication("Multi1");
        Application app2 = createTestApplication("Multi2");
        Application app3 = createTestApplication("Multi3");

        long totalAppsBefore = applicationRepository.count();

        mockMvc.perform(post("/district-officer/review/" + app1.getId())
                        .with(csrf())
                        .param("applicationId", app1.getId().toString())
                        .param("action", "REQUEST_RE_VERIFICATION")
                        .param("remarks", "Reason for App 1"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/district-officer/review/" + app2.getId())
                        .with(csrf())
                        .param("applicationId", app2.getId().toString())
                        .param("action", "REQUEST_RE_VERIFICATION")
                        .param("remarks", "Reason for App 2"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/district-officer/review/" + app3.getId())
                        .with(csrf())
                        .param("applicationId", app3.getId().toString())
                        .param("action", "REQUEST_RE_VERIFICATION")
                        .param("remarks", "Reason for App 3"))
                .andExpect(status().is3xxRedirection());

        assertEquals(totalAppsBefore, applicationRepository.count(), "No duplicate application rows must be created.");

        Application updated1 = applicationRepository.findById(app1.getId()).orElseThrow();
        Application updated2 = applicationRepository.findById(app2.getId()).orElseThrow();
        Application updated3 = applicationRepository.findById(app3.getId()).orElseThrow();

        assertEquals("RE_VERIFICATION_REQUIRED", updated1.getStatus());
        assertEquals("RE_VERIFICATION_REQUIRED", updated2.getStatus());
        assertEquals("RE_VERIFICATION_REQUIRED", updated3.getStatus());

        assertEquals("FIELD_VERIFICATION", updated1.getCurrentStage());
        assertEquals("FIELD_VERIFICATION", updated2.getCurrentStage());
        assertEquals("FIELD_VERIFICATION", updated3.getCurrentStage());

        assertTrue(restrictionRepository.findByApplicationId(app1.getId()).isEmpty());
        assertTrue(restrictionRepository.findByApplicationId(app2.getId()).isEmpty());
        assertTrue(restrictionRepository.findByApplicationId(app3.getId()).isEmpty());
    }
}
