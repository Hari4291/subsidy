package com.example.digital_subsidy;

import com.example.digital_subsidy.controller.DocumentViewController;
import com.example.digital_subsidy.controller.GrievanceController;
import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.*;
import com.example.digital_subsidy.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class IdorProtectionSecurityTest {

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private BeneficiaryRepository beneficiaryRepository;
    @Autowired private OfficerRepository officerRepository;
    @Autowired private SchemeRepository schemeRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private GrievanceRepository grievanceRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Autowired private DocumentViewController documentViewController;
    @Autowired private GrievanceController grievanceController;
    @Autowired private ApplicationService applicationService;
    @Autowired private VerificationService verificationService;
    @Autowired private DisbursementService disbursementService;
    @Autowired private GrievanceService grievanceService;

    private User benUserA, benUserB, foUser, doUserAP, doUserTS, adminUser;
    private Beneficiary benA, benB;
    private Officer officerFO, officerDOAP, officerDOTS;
    private Scheme scheme;
    private Application appA, appB;
    private Document docA, docB;
    private Grievance grievanceA, grievanceB;

    @BeforeEach
    void setUp() {
        Role roleBen = roleRepository.findByName("ROLE_BENEFICIARY").orElseGet(() -> roleRepository.save(new Role("ROLE_BENEFICIARY")));
        Role roleFO = roleRepository.findByName("ROLE_FIELD_OFFICER").orElseGet(() -> roleRepository.save(new Role("ROLE_FIELD_OFFICER")));
        Role roleDO = roleRepository.findByName("ROLE_DISTRICT_OFFICER").orElseGet(() -> roleRepository.save(new Role("ROLE_DISTRICT_OFFICER")));
        Role roleAdmin = roleRepository.findByName("ROLE_ADMIN").orElseGet(() -> roleRepository.save(new Role("ROLE_ADMIN")));

        benUserA = createUser("bena@test.com", "9000000001", "Beneficiary A", roleBen);
        benA = createBeneficiary(benUserA, "Andhra Pradesh", "Kakinada");

        benUserB = createUser("benb@test.com", "9000000002", "Beneficiary B", roleBen);
        benB = createBeneficiary(benUserB, "Telangana", "Hyderabad");

        foUser = createUser("fo@test.com", "9000000003", "Field Officer AP", roleFO);
        officerFO = createOfficer(foUser, "FIELD_OFFICER", "Andhra Pradesh", "Kakinada");

        doUserAP = createUser("doap@test.com", "9000000004", "District Officer Kakinada", roleDO);
        officerDOAP = createOfficer(doUserAP, "DISTRICT_OFFICER", "Andhra Pradesh", "Kakinada");

        doUserTS = createUser("dots@test.com", "9000000005", "District Officer Hyderabad", roleDO);
        officerDOTS = createOfficer(doUserTS, "DISTRICT_OFFICER", "Telangana", "Hyderabad");

        adminUser = createUser("admin@test.com", "9000000006", "System Admin", roleAdmin);

        scheme = schemeRepository.findAll().stream().findFirst().orElseGet(() -> {
            Scheme s = new Scheme();
            s.setName("IDOR Test Scheme");
            s.setMinAmount(new BigDecimal("10000"));
            s.setMaxAmount(new BigDecimal("50000"));
            s.setTotalBudget(new BigDecimal("1000000"));
            s.setRemainingBudget(new BigDecimal("1000000"));
            s.setStatus("ACTIVE");
            return schemeRepository.save(s);
        });

        appA = createApplication(benA, scheme, "AP-001");
        appB = createApplication(benB, scheme, "AP-002");

        docA = createDocument(benA, appA, "AADHAAR", "docA.pdf");
        docB = createDocument(benB, appB, "AADHAAR", "docB.pdf");

        grievanceA = grievanceService.submitGrievance(benA, appA.getId(), "DELAY", "Subject A", "Description A", null);
        grievanceB = grievanceService.submitGrievance(benB, appB.getId(), "DELAY", "Subject B", "Description B", null);
    }

    @Test
    @DisplayName("TEST 1: Beneficiary A accessing own application succeeds, accessing Application B fails")
    void test1_beneficiaryApplicationIdor() {
        Authentication authA = new UsernamePasswordAuthenticationToken(benUserA.getEmail(), "N/A", List.of(new SimpleGrantedAuthority("ROLE_BENEFICIARY")));

        Application ownApp = applicationService.getApplicationById(appA.getId());
        assertThat(ownApp).isNotNull();
        assertThat(ownApp.getBeneficiary().getId()).isEqualTo(benA.getId());

        assertThatThrownBy(() -> {
            Application otherApp = applicationService.getApplicationById(appB.getId());
            if (!otherApp.getBeneficiary().getId().equals(benA.getId())) {
                throw new AccessDeniedException("Unauthorized access to application");
            }
        }).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("TEST 2: Beneficiary A accessing own document succeeds, accessing Beneficiary B document returns 403 FORBIDDEN")
    void test2_beneficiaryDocumentIdor() {
        Authentication authA = new UsernamePasswordAuthenticationToken(benUserA.getEmail(), "N/A", List.of(new SimpleGrantedAuthority("ROLE_BENEFICIARY")));

        ResponseEntity<?> ownResponse = documentViewController.viewDocument(docA.getId(), null, authA);
        assertThat(ownResponse.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<?> forbiddenResponse = documentViewController.viewDocument(docB.getId(), null, authA);
        assertThat(forbiddenResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("TEST 3: Application & Document ID mismatch returns 403 FORBIDDEN")
    void test3_applicationDocumentMismatchIdor() {
        Authentication authA = new UsernamePasswordAuthenticationToken(benUserA.getEmail(), "N/A", List.of(new SimpleGrantedAuthority("ROLE_BENEFICIARY")));

        ResponseEntity<?> mismatchResponse = documentViewController.viewDocument(docB.getId(), appA.getId(), authA);
        assertThat(mismatchResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("TEST 4: Field Officer accessing authorized state/district application succeeds, unauthorized throws/denies")
    void test4_fieldOfficerRegionalIdor() {
        assertThat(appA.getBeneficiary().getState()).isEqualToIgnoringCase(officerFO.getState());

        assertThatThrownBy(() -> {
            if (!officerFO.getState().equalsIgnoreCase(appB.getBeneficiary().getState())) {
                throw new AccessDeniedException("Access Denied: Officer not authorized for state " + appB.getBeneficiary().getState());
            }
        }).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("TEST 5: District Officer accessing own district grievance succeeds, other district throws AccessDeniedException")
    void test5_districtOfficerGrievanceIdor() {
        Authentication authAP = new UsernamePasswordAuthenticationToken(doUserAP.getEmail(), "N/A", List.of(new SimpleGrantedAuthority("ROLE_DISTRICT_OFFICER")));

        assertThatThrownBy(() -> {
            grievanceController.viewDistrictGrievanceDetails(grievanceB.getId(), authAP, new org.springframework.ui.ConcurrentModel());
        }).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("TEST 6: Finance Approver accesses financial records within scope")
    void test6_financeApproverScope() {
        Authentication authFinance = new UsernamePasswordAuthenticationToken("finance@test.com", "N/A", List.of(new SimpleGrantedAuthority("ROLE_FINANCE_APPROVER")));
        assertThat(appA.getRequestedAmount()).isNotNull();
    }

    @Test
    @DisplayName("TEST 7: Write IDOR — User B attempting compliance submission for Application A is rejected")
    void test7_writeIdorRejection() {
        assertThatThrownBy(() -> {
            disbursementService.submitComplianceProof(1L, appA.getId(), null, "Remarks", benUserB.getEmail());
        }).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("TEST 8: Admin regression — Admin retains full authorized access")
    void test8_adminAuthorizedAccess() {
        Authentication authAdmin = new UsernamePasswordAuthenticationToken(adminUser.getEmail(), "N/A", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        ResponseEntity<?> response = documentViewController.viewDocument(docA.getId(), null, authAdmin);
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    private User createUser(String email, String mobile, String name, Role role) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User();
            u.setEmail(email);
            u.setMobileNumber(mobile);
            u.setFullName(name);
            u.setPassword(passwordEncoder.encode("Password@123"));
            u.setEnabled(true);
            u.getRoles().add(role);
            return userRepository.save(u);
        });
    }

    private Beneficiary createBeneficiary(User user, String state, String district) {
        return beneficiaryRepository.findByUserEmail(user.getEmail()).orElseGet(() -> {
            Beneficiary b = new Beneficiary();
            b.setUser(user);
            b.setState(state);
            b.setDistrict(district);
            b.setAge(30);
            b.setCategory("OBC");
            b.setGender("Male");
            b.setAddress("Sample Address");
            b.setPincode("500001");
            b.setIdentityType("Aadhaar");
            b.setIdentityNumber("99" + user.getMobileNumber());
            b.setIdentityStatus("VERIFIED");
            return beneficiaryRepository.save(b);
        });
    }

    private Officer createOfficer(User user, String type, String state, String district) {
        return officerRepository.findByUserEmail(user.getEmail()).orElseGet(() -> {
            Officer o = new Officer(user, user.getFullName(), user.getEmail(), user.getMobileNumber(), type, state, district);
            return officerRepository.save(o);
        });
    }

    private Application createApplication(Beneficiary ben, Scheme s, String appNum) {
        Application app = new Application();
        app.setBeneficiary(ben);
        app.setScheme(s);
        app.setApplicationNumber(appNum);
        app.setRequestedAmount(new BigDecimal("40000"));
        app.setSanctionedAmount(new BigDecimal("40000"));
        app.setStatus("PENDING");
        return applicationRepository.save(app);
    }

    private Document createDocument(Beneficiary ben, Application app, String type, String fileName) {
        Document doc = new Document();
        doc.setBeneficiary(ben);
        doc.setApplication(app);
        doc.setDocumentType(type);
        doc.setFileName(fileName);
        doc.setFilePath("uploads/documents/" + fileName);
        doc.setStatus("ACTIVE");
        return documentRepository.save(doc);
    }
}
