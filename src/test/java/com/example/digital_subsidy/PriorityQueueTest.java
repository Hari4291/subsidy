package com.example.digital_subsidy;

import com.example.digital_subsidy.dto.PriorityBreakdownDto;
import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.*;
import com.example.digital_subsidy.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class PriorityQueueTest {

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private BeneficiaryRepository beneficiaryRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private OfficerRepository officerRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PriorityService priorityService;

    @Autowired
    private SchemeService schemeService;

    private Scheme scheme;
    private Beneficiary beneficiary1;
    private Beneficiary beneficiary2;
    private User user1;
    private User user2;
    private User districtOfficerUser;
    private User fieldOfficerUser;

    @BeforeEach
    void setUp() {
        Role beneficiaryRole = roleRepository.findByName("ROLE_BENEFICIARY")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_BENEFICIARY")));
        Role doRole = roleRepository.findByName("ROLE_DISTRICT_OFFICER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_DISTRICT_OFFICER")));
        Role foRole = roleRepository.findByName("ROLE_FIELD_OFFICER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_FIELD_OFFICER")));

        scheme = new Scheme();
        scheme.setName("Priority Test Scheme " + UUID.randomUUID().toString().substring(0, 5));
        scheme.setDescription("Scheme priority queue test");
        scheme.setMinAmount(new BigDecimal("1000.00"));
        scheme.setMaxAmount(new BigDecimal("100000.00"));
        scheme.setTotalBudget(new BigDecimal("1000000.00"));
        scheme.setRemainingBudget(new BigDecimal("1000000.00"));
        scheme.setStatus("ACTIVE");
        scheme.setPriorityQueueEnabled(true);
        scheme.setEligibilityWeight(60);
        scheme.setWaitingWeight(25);
        scheme.setVulnerabilityWeight(15);
        scheme.setHighPriorityThreshold(75);
        scheme.setMediumPriorityThreshold(50);
        scheme.setMaxAgingDays(30);
        scheme.setEscalationWaitingDays(7);
        scheme = schemeRepository.save(scheme);

        // Beneficiary 1: Senior Female ST Beneficiary (Vulnerability = 100)
        user1 = new User();
        user1.setEmail("ben_pri_1_" + UUID.randomUUID().toString().substring(0, 5) + "@test.com");
        user1.setPassword("password");
        user1.setFullName("Priority Beneficiary One");
        user1.setMobileNumber(UUID.randomUUID().toString().substring(0, 10));
        user1.setRoles(Collections.singleton(beneficiaryRole));
        user1 = userRepository.save(user1);

        beneficiary1 = new Beneficiary();
        beneficiary1.setUser(user1);
        beneficiary1.setAge(65);
        beneficiary1.setGender("FEMALE");
        beneficiary1.setCategory("ST");
        beneficiary1.setAddress("Street 1");
        beneficiary1.setDistrict("Jaipur");
        beneficiary1.setState("Rajasthan");
        beneficiary1.setPincode("302001");
        beneficiary1.setIdentityType("AADHAAR");
        beneficiary1.setIdentityNumber(UUID.randomUUID().toString().substring(0, 12));
        beneficiary1 = beneficiaryRepository.save(beneficiary1);

        // Beneficiary 2: Male General Beneficiary Age 30 (Vulnerability = 0)
        user2 = new User();
        user2.setEmail("ben_pri_2_" + UUID.randomUUID().toString().substring(0, 5) + "@test.com");
        user2.setPassword("password");
        user2.setFullName("Priority Beneficiary Two");
        user2.setMobileNumber(UUID.randomUUID().toString().substring(0, 10));
        user2.setRoles(Collections.singleton(beneficiaryRole));
        user2 = userRepository.save(user2);

        beneficiary2 = new Beneficiary();
        beneficiary2.setUser(user2);
        beneficiary2.setAge(30);
        beneficiary2.setGender("MALE");
        beneficiary2.setCategory("General");
        beneficiary2.setAddress("Street 2");
        beneficiary2.setDistrict("Jaipur");
        beneficiary2.setState("Rajasthan");
        beneficiary2.setPincode("302001");
        beneficiary2.setIdentityType("AADHAAR");
        beneficiary2.setIdentityNumber(UUID.randomUUID().toString().substring(0, 12));
        beneficiary2 = beneficiaryRepository.save(beneficiary2);

        // Officers
        districtOfficerUser = new User();
        districtOfficerUser.setEmail("do_pri_" + UUID.randomUUID().toString().substring(0, 5) + "@test.com");
        districtOfficerUser.setPassword("password");
        districtOfficerUser.setFullName("District Officer");
        districtOfficerUser.setMobileNumber(UUID.randomUUID().toString().substring(0, 10));
        districtOfficerUser.setRoles(Collections.singleton(doRole));
        districtOfficerUser = userRepository.save(districtOfficerUser);

        fieldOfficerUser = new User();
        fieldOfficerUser.setEmail("fo_pri_" + UUID.randomUUID().toString().substring(0, 5) + "@test.com");
        fieldOfficerUser.setPassword("password");
        fieldOfficerUser.setFullName("Field Officer");
        fieldOfficerUser.setMobileNumber(UUID.randomUUID().toString().substring(0, 10));
        fieldOfficerUser.setRoles(Collections.singleton(foRole));
        fieldOfficerUser = userRepository.save(fieldOfficerUser);
    }

    @Test
    void testPriorityScoreCalculation_DefaultWeights() {
        Application app = new Application();
        app.setApplicationNumber("APP-PRI-" + UUID.randomUUID().toString().substring(0, 6));
        app.setScheme(scheme);
        app.setBeneficiary(beneficiary1);
        app.setRequestedAmount(new BigDecimal("50000.00"));
        app.setEligibilityScore(80);
        app.setStatus("PENDING");
        app.setCreatedAt(LocalDateTime.now());
        app = applicationRepository.save(app);

        // Calculate priority
        Application updated = priorityService.calculatePriority(app);
        assertNotNull(updated);
        assertNotNull(updated.getPriorityScore());
        assertEquals("MEDIUM", updated.getPriorityLevel()); // Score = 80*0.6 + 0*0.25 + 100*0.15 = 48 + 15 = 63.0

        PriorityBreakdownDto breakdown = priorityService.getPriorityBreakdown(updated);
        assertNotNull(breakdown);
        assertEquals(80, breakdown.getEligibilityScore());
        assertEquals(new BigDecimal("63.0"), breakdown.getFinalPriorityScore());
        assertEquals("MEDIUM", breakdown.getPriorityLevel());
        assertFalse(breakdown.getVulnerabilityDetails().isEmpty());
    }

    @Test
    void testWeightValidation() {
        // Test invalid sum (90 != 100)
        assertThrows(IllegalArgumentException.class, () -> {
            schemeService.updatePriorityConfig(scheme.getId(), true, 60, 20, 10, 75, 50, 30, 7, "admin@test.com");
        });

        // Test negative weight (-10)
        assertThrows(IllegalArgumentException.class, () -> {
            schemeService.updatePriorityConfig(scheme.getId(), true, 80, 30, -10, 75, 50, 30, 7, "admin@test.com");
        });

        // Test valid sum (50 + 30 + 20 = 100)
        Scheme updated = schemeService.updatePriorityConfig(scheme.getId(), true, 50, 30, 20, 80, 50, 30, 7, "admin@test.com");
        assertEquals(50, updated.getEligibilityWeight());
        assertEquals(30, updated.getWaitingWeight());
        assertEquals(20, updated.getVulnerabilityWeight());
    }

    @Test
    void testWaitingDaysAging() {
        // App 1: Submitted 15 days ago
        Application app1 = new Application();
        app1.setApplicationNumber("APP-AGE-1-" + UUID.randomUUID().toString().substring(0, 6));
        app1.setScheme(scheme);
        app1.setBeneficiary(beneficiary2); // Vulnerability = 0
        app1.setRequestedAmount(new BigDecimal("40000.00"));
        app1.setEligibilityScore(70);
        app1.setStatus("PENDING");
        app1.setCreatedAt(LocalDateTime.now().minusDays(15)); // 15 days pending
        app1 = applicationRepository.save(app1);

        Application calculated = priorityService.calculatePriority(app1);
        // Waiting factor = (15/30)*100 = 50.0. Contribution = 50.0 * 0.25 = 12.5.
        // Eligibility contribution = 70 * 0.60 = 42.0. Total = 42.0 + 12.5 = 54.5.
        assertEquals(new BigDecimal("54.5"), calculated.getPriorityScore());
        assertEquals("MEDIUM", calculated.getPriorityLevel());
    }

    @Test
    void testEqualPriorityTieBreaker() {
        // App A: Submitted 2 days ago
        Application appA = new Application();
        appA.setApplicationNumber("APP-TIE-A-" + UUID.randomUUID().toString().substring(0, 6));
        appA.setScheme(scheme);
        appA.setBeneficiary(beneficiary2);
        appA.setRequestedAmount(new BigDecimal("30000.00"));
        appA.setPriorityScore(new BigDecimal("70.0"));
        appA.setPriorityLevel("MEDIUM");
        appA.setCreatedAt(LocalDateTime.now().minusDays(2));
        appA = applicationRepository.save(appA);

        // App B: Submitted 10 days ago (Earlier submission)
        Application appB = new Application();
        appB.setApplicationNumber("APP-TIE-B-" + UUID.randomUUID().toString().substring(0, 6));
        appB.setScheme(scheme);
        appB.setBeneficiary(beneficiary2);
        appB.setRequestedAmount(new BigDecimal("30000.00"));
        appB.setPriorityScore(new BigDecimal("70.0"));
        appB.setPriorityLevel("MEDIUM");
        appB.setCreatedAt(LocalDateTime.now().minusDays(10));
        appB = applicationRepository.save(appB);

        List<Application> sorted = priorityService.sortApplicationsByPriority(List.of(appA, appB));
        assertEquals(appB.getId(), sorted.get(0).getId()); // Earlier submission (10 days ago) takes precedence
    }

    @Test
    void testManualPriorityOverride() {
        Application app = new Application();
        app.setApplicationNumber("APP-OVR-" + UUID.randomUUID().toString().substring(0, 6));
        app.setScheme(scheme);
        app.setBeneficiary(beneficiary2);
        app.setRequestedAmount(new BigDecimal("50000.00"));
        app.setEligibilityScore(60);
        app.setStatus("PENDING");
        app.setCreatedAt(LocalDateTime.now());
        Application savedAppForOverride = applicationRepository.save(app);
        final Long overrideAppId = savedAppForOverride.getId();

        // District Officer overrides to HIGH with score 88.0
        Application overridden = priorityService.manualOverridePriority(
                overrideAppId, "HIGH", new BigDecimal("88.0"),
                "Urgent flood relief case verified on field",
                districtOfficerUser.getEmail(), "ROLE_DISTRICT_OFFICER"
        );

        assertTrue(overridden.getIsPriorityOverridden());
        assertEquals("HIGH", overridden.getPriorityLevel());
        assertEquals(new BigDecimal("88.0"), overridden.getPriorityScore());
        assertEquals("Urgent flood relief case verified on field", overridden.getPriorityOverrideReason());
        assertEquals(districtOfficerUser.getEmail(), overridden.getPriorityOverriddenBy());

        // Recalculating priority on overridden app should preserve override
        Application recalc = priorityService.calculatePriority(overridden);
        assertEquals("HIGH", recalc.getPriorityLevel());
        assertEquals(new BigDecimal("88.0"), recalc.getPriorityScore());

        // Verify Audit Log entry
        boolean hasAudit = auditLogRepository.findAll().stream()
                .anyMatch(l -> "PRIORITY_MANUAL_OVERRIDE".equals(l.getAction()) && l.getEntityId().equals(overrideAppId));
        assertTrue(hasAudit);
    }

    @Test
    void testIDORAndRoleAuthorization() {
        Application app = new Application();
        app.setApplicationNumber("APP-AUTH-" + UUID.randomUUID().toString().substring(0, 6));
        app.setScheme(scheme);
        app.setBeneficiary(beneficiary2);
        app.setRequestedAmount(new BigDecimal("50000.00"));
        app.setStatus("PENDING");
        Application savedApp = applicationRepository.save(app);
        final Long appId = savedApp.getId();

        // Field Officer trying to override priority -> AccessDeniedException
        assertThrows(AccessDeniedException.class, () -> {
            priorityService.manualOverridePriority(
                    appId, "HIGH", new BigDecimal("90.0"),
                    "FO attempting override",
                    fieldOfficerUser.getEmail(), "ROLE_FIELD_OFFICER"
            );
        });
    }

    @Test
    void testEscalationNotification() {
        // App pending for 10 days (escalation threshold is 7 days)
        Application app = new Application();
        app.setApplicationNumber("APP-ESC-" + UUID.randomUUID().toString().substring(0, 6));
        app.setScheme(scheme);
        app.setBeneficiary(beneficiary1);
        app.setRequestedAmount(new BigDecimal("40000.00"));
        app.setStatus("PENDING");
        app.setCurrentStage("FIELD_VERIFICATION");
        app.setCreatedAt(LocalDateTime.now().minusDays(10));
        Application savedEscApp = applicationRepository.save(app);
        final Long escAppId = savedEscApp.getId();

        // Trigger scheduler recalculation & escalation checks
        priorityService.recalculatePendingApplicationsPriority();

        // Verify notification sent to FO role
        List<Notification> notifs = notificationRepository.findAll().stream()
                .filter(n -> n.getEventKey() != null && n.getEventKey().contains("REMINDER_PRIORITY_ESCALATION_" + escAppId))
                .toList();

        assertFalse(notifs.isEmpty());
    }
}
