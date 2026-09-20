package com.example.digital_subsidy;

import com.example.digital_subsidy.dto.*;
import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.*;
import com.example.digital_subsidy.service.*;
import com.example.digital_subsidy.controller.TrackingController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.ConcurrentModel;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class ApplicationTrackingAndNotificationTest {

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
    private TimelineService timelineService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ReminderSchedulerService reminderSchedulerService;

    @Autowired
    private VerificationService verificationService;

    @Autowired
    private TrackingController trackingController;

    @Autowired
    private com.example.digital_subsidy.controller.NotificationController notificationController;

    private Scheme scheme;
    private Beneficiary beneficiary1;
    private Beneficiary beneficiary2;
    private User user1;
    private User user2;
    private User fieldOfficerUser;
    private User districtOfficerUser;
    private Officer fieldOfficer;
    private Officer districtOfficer;

    @BeforeEach
    void setUp() {
        Role beneficiaryRole = roleRepository.findByName("ROLE_BENEFICIARY")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_BENEFICIARY")));
        Role foRole = roleRepository.findByName("ROLE_FIELD_OFFICER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_FIELD_OFFICER")));
        Role doRole = roleRepository.findByName("ROLE_DISTRICT_OFFICER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_DISTRICT_OFFICER")));

        scheme = new Scheme();
        scheme.setName("Solar Pump Grant " + UUID.randomUUID().toString().substring(0, 5));
        scheme.setDescription("Clean energy subsidy");
        scheme.setMinAmount(new BigDecimal("1000.00"));
        scheme.setMaxAmount(new BigDecimal("100000.00"));
        scheme.setTotalBudget(new BigDecimal("1000000.00"));
        scheme.setRemainingBudget(new BigDecimal("1000000.00"));
        scheme.setStatus("ACTIVE");
        scheme.setRejectionCooldownDays(30);
        scheme = schemeRepository.save(scheme);

        // Beneficiary 1
        user1 = new User();
        user1.setEmail("ben1_" + UUID.randomUUID().toString().substring(0, 5) + "@test.com");
        user1.setPassword("password");
        user1.setFullName("Beneficiary One");
        user1.setMobileNumber(UUID.randomUUID().toString().substring(0, 10));
        user1.setRoles(Collections.singleton(beneficiaryRole));
        user1 = userRepository.save(user1);

        beneficiary1 = new Beneficiary();
        beneficiary1.setUser(user1);
        beneficiary1.setAge(30);
        beneficiary1.setGender("MALE");
        beneficiary1.setCategory("General");
        beneficiary1.setAddress("Address 1");
        beneficiary1.setDistrict("Jaipur");
        beneficiary1.setState("Rajasthan");
        beneficiary1.setPincode("302001");
        beneficiary1.setIdentityType("AADHAAR");
        beneficiary1.setIdentityNumber(UUID.randomUUID().toString().substring(0, 12));
        beneficiary1 = beneficiaryRepository.save(beneficiary1);

        // Beneficiary 2
        user2 = new User();
        user2.setEmail("ben2_" + UUID.randomUUID().toString().substring(0, 5) + "@test.com");
        user2.setPassword("password");
        user2.setFullName("Beneficiary Two");
        user2.setMobileNumber(UUID.randomUUID().toString().substring(0, 10));
        user2.setRoles(Collections.singleton(beneficiaryRole));
        user2 = userRepository.save(user2);

        beneficiary2 = new Beneficiary();
        beneficiary2.setUser(user2);
        beneficiary2.setAge(35);
        beneficiary2.setGender("FEMALE");
        beneficiary2.setCategory("General");
        beneficiary2.setAddress("Address 2");
        beneficiary2.setDistrict("Jaipur");
        beneficiary2.setState("Rajasthan");
        beneficiary2.setPincode("302001");
        beneficiary2.setIdentityType("AADHAAR");
        beneficiary2.setIdentityNumber(UUID.randomUUID().toString().substring(0, 12));
        beneficiary2 = beneficiaryRepository.save(beneficiary2);

        // Officers
        fieldOfficerUser = new User();
        fieldOfficerUser.setEmail("fo_" + UUID.randomUUID().toString().substring(0, 5) + "@test.com");
        fieldOfficerUser.setPassword("password");
        fieldOfficerUser.setFullName("Field Officer");
        fieldOfficerUser.setMobileNumber(UUID.randomUUID().toString().substring(0, 10));
        fieldOfficerUser.setRoles(Collections.singleton(foRole));
        fieldOfficerUser = userRepository.save(fieldOfficerUser);

        fieldOfficer = new Officer(fieldOfficerUser, "Field Officer 1", fieldOfficerUser.getEmail(), fieldOfficerUser.getMobileNumber(), "FIELD_OFFICER", "Rajasthan", "Jaipur");
        fieldOfficer = officerRepository.save(fieldOfficer);

        districtOfficerUser = new User();
        districtOfficerUser.setEmail("do_" + UUID.randomUUID().toString().substring(0, 5) + "@test.com");
        districtOfficerUser.setPassword("password");
        districtOfficerUser.setFullName("District Officer");
        districtOfficerUser.setMobileNumber(UUID.randomUUID().toString().substring(0, 10));
        districtOfficerUser.setRoles(Collections.singleton(doRole));
        districtOfficerUser = userRepository.save(districtOfficerUser);

        districtOfficer = new Officer(districtOfficerUser, "District Officer 1", districtOfficerUser.getEmail(), districtOfficerUser.getMobileNumber(), "DISTRICT_OFFICER", "Rajasthan", "Jaipur");
        districtOfficer = officerRepository.save(districtOfficer);
    }

    @Test
    void testTimelineGenerationAndUpdates() {
        // Create initial application
        Application app = new Application();
        app.setApplicationNumber("APP-TRACK-" + UUID.randomUUID().toString().substring(0, 6));
        app.setScheme(scheme);
        app.setBeneficiary(beneficiary1);
        app.setRequestedAmount(new BigDecimal("50000.00"));
        app.setStatus("PENDING");
        app.setCurrentStage("FIELD_VERIFICATION");
        app.setEligibilityScore(85);
        app.setCreatedAt(LocalDateTime.now());
        app = applicationRepository.save(app);

        // Build timeline
        List<TimelineEventDto> initialTimeline = timelineService.buildApplicationTimeline(app.getId());
        assertNotNull(initialTimeline);
        assertFalse(initialTimeline.isEmpty());
        assertEquals("1. Application Submitted", initialTimeline.get(0).getStageTitle());
        assertEquals("COMPLETED", initialTimeline.get(0).getState());

        // Field Officer Verification
        VerificationActionDto foAction = new VerificationActionDto();
        foAction.setApplicationId(app.getId());
        foAction.setAction("APPROVE");
        foAction.setRemarks("Ground physical verification completed cleanly");

        verificationService.processVerification(fieldOfficerUser.getEmail(), "FIELD_OFFICER", foAction);

        Application appAfterFO = applicationRepository.findById(app.getId()).orElseThrow();
        assertEquals("PENDING", appAfterFO.getStatus());
        assertEquals("DISTRICT_REVIEW", appAfterFO.getCurrentStage());

        List<TimelineEventDto> timelineAfterFO = timelineService.buildApplicationTimeline(app.getId());
        boolean hasFoCompleted = timelineAfterFO.stream()
                .anyMatch(e -> "FIELD_VERIFICATION".equals(e.getStageCode()) && "COMPLETED".equals(e.getState()));
        assertTrue(hasFoCompleted);

        // DO Re-verification Request
        VerificationActionDto doReverify = new VerificationActionDto();
        doReverify.setApplicationId(app.getId());
        doReverify.setAction("REQUEST_RE_VERIFICATION");
        doReverify.setRemarks("Land parcel documents require secondary check");

        verificationService.processVerification(districtOfficerUser.getEmail(), "DISTRICT_OFFICER", doReverify);

        Application appReverify = applicationRepository.findById(app.getId()).orElseThrow();
        assertEquals("RE_VERIFICATION_REQUIRED", appReverify.getStatus());

        List<TimelineEventDto> timelineReverify = timelineService.buildApplicationTimeline(app.getId());
        boolean hasReverifyEvent = timelineReverify.stream()
                .anyMatch(e -> "RE_VERIFICATION_REQUIRED".equals(e.getStageCode()) || (e.getRemarks() != null && e.getRemarks().contains("secondary check")));
        assertTrue(hasReverifyEvent);
    }

    @Test
    void testNotificationServiceAndDeduplication() {
        // Create notification
        Notification notif1 = notificationService.createNotification(
                user1,
                null,
                "SUBMISSION",
                "Application Submission Success",
                "Your application APP-1001 has been received.",
                "HIGH",
                "/beneficiary/applications",
                "EVENT_TEST_KEY_1"
        );

        assertNotNull(notif1);
        assertEquals(1, notificationService.getUnreadCount(user1));

        // Duplicate event key notification attempt -> should return null
        Notification notif2 = notificationService.createNotification(
                user1,
                null,
                "SUBMISSION",
                "Application Submission Success",
                "Duplicate payload.",
                "HIGH",
                "/beneficiary/applications",
                "EVENT_TEST_KEY_1"
        );

        assertNull(notif2);
        assertEquals(1, notificationService.getUnreadCount(user1));

        // Mark read
        notificationService.markAsRead(notif1.getId(), user1);
        assertEquals(0, notificationService.getUnreadCount(user1));
    }

    @Test
    void testUnreadNotificationRestApiEndpoint() {
        notificationService.createNotification(
                user1,
                null,
                "TEST_TYPE",
                "Unread REST Test",
                "Message for REST endpoint test",
                "HIGH",
                "/beneficiary/dashboard",
                "EVENT_REST_TEST_" + UUID.randomUUID()
        );

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user1.getEmail(), "password", Collections.singletonList(new SimpleGrantedAuthority("ROLE_BENEFICIARY"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        org.springframework.http.ResponseEntity<List<java.util.Map<String, Object>>> response = notificationController.getUnreadNotifications(auth);
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isEmpty());

        java.util.Map<String, Object> firstNotif = response.getBody().get(0);
        assertEquals("Unread REST Test", firstNotif.get("title"));
        assertEquals("Message for REST endpoint test", firstNotif.get("message"));
        assertEquals("HIGH", firstNotif.get("priority"));

        // Unauthenticated access returns empty list, no exception
        org.springframework.http.ResponseEntity<List<java.util.Map<String, Object>>> unauthResponse = notificationController.getUnreadNotifications(null);
        assertNotNull(unauthResponse);
        assertEquals(200, unauthResponse.getStatusCode().value());
        assertTrue(unauthResponse.getBody().isEmpty());
    }

    @Test
    void testReminderSchedulerService() {
        // Create an application pending field verification for 5 days
        Application pendingApp = new Application();
        pendingApp.setApplicationNumber("APP-REMIND-" + UUID.randomUUID().toString().substring(0, 6));
        pendingApp.setScheme(scheme);
        pendingApp.setBeneficiary(beneficiary1);
        pendingApp.setRequestedAmount(new BigDecimal("40000.00"));
        pendingApp.setStatus("PENDING");
        pendingApp.setCurrentStage("FIELD_VERIFICATION");
        pendingApp.setCreatedAt(LocalDateTime.now().minusDays(5));
        pendingApp.setUpdatedAt(LocalDateTime.now().minusDays(5));
        final Application savedPendingApp = applicationRepository.save(pendingApp);

        // Execute reminder scheduler
        reminderSchedulerService.processScheduledReminders();

        // Check if field officer user received notification
        List<Notification> foNotifs = notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(fieldOfficerUser.getId());
        assertFalse(foNotifs.isEmpty());
        assertTrue(foNotifs.stream().anyMatch(n -> n.getEventKey().contains("REMINDER_FO_APP_" + savedPendingApp.getId())));
    }

    @Test
    void testIDORAuthorizationOnTrackingController() {
        Application app = new Application();
        app.setApplicationNumber("APP-IDOR-" + UUID.randomUUID().toString().substring(0, 6));
        app.setScheme(scheme);
        app.setBeneficiary(beneficiary1);
        app.setRequestedAmount(new BigDecimal("30000.00"));
        app.setStatus("PENDING");
        app.setCreatedAt(LocalDateTime.now());
        final Application savedApp = applicationRepository.save(app);

        // Test 1: Beneficiary 1 accesses own tracking -> allowed
        UsernamePasswordAuthenticationToken authBen1 = new UsernamePasswordAuthenticationToken(
                user1.getEmail(), "password", Collections.singletonList(new SimpleGrantedAuthority("ROLE_BENEFICIARY"))
        );
        SecurityContextHolder.getContext().setAuthentication(authBen1);
        ConcurrentModel model = new ConcurrentModel();
        String viewName1 = trackingController.viewTracking(savedApp.getId(), authBen1, model);
        assertEquals("beneficiary/tracking", viewName1);

        // Test 2: Beneficiary 2 attempts to access Beneficiary 1's tracking -> AccessDeniedException (IDOR check)
        UsernamePasswordAuthenticationToken authBen2 = new UsernamePasswordAuthenticationToken(
                user2.getEmail(), "password", Collections.singletonList(new SimpleGrantedAuthority("ROLE_BENEFICIARY"))
        );
        SecurityContextHolder.getContext().setAuthentication(authBen2);

        assertThrows(AccessDeniedException.class, () -> {
            trackingController.viewTracking(savedApp.getId(), authBen2, new ConcurrentModel());
        });

        // Test 3: Officer accesses Beneficiary 1's tracking -> allowed
        UsernamePasswordAuthenticationToken authFO = new UsernamePasswordAuthenticationToken(
                fieldOfficerUser.getEmail(), "password", Collections.singletonList(new SimpleGrantedAuthority("ROLE_FIELD_OFFICER"))
        );
        SecurityContextHolder.getContext().setAuthentication(authFO);
        String viewNameFO = trackingController.viewTracking(savedApp.getId(), authFO, new ConcurrentModel());
        assertEquals("beneficiary/tracking", viewNameFO);
    }
}
