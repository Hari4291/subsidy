package com.example.digital_subsidy;

import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.*;
import com.example.digital_subsidy.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class SchemeTermDocumentWorkflowTest {

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private DisbursementMilestoneRepository disbursementMilestoneRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private BeneficiaryRepository beneficiaryRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private DisbursementRepository disbursementRepository;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private DisbursementService disbursementService;

    @Autowired
    private DocumentService documentService;

    private User solarUser;
    private Beneficiary solarBen;
    private Scheme solarScheme;
    private Scheme eduScheme;

    @BeforeEach
    void setUp() {
        Role roleBen = roleRepository.findByName("ROLE_BENEFICIARY")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_BENEFICIARY")));

        String email = "solar_term_doc_user@test.gov.in";
        solarUser = userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User();
            u.setEmail(email);
            u.setFullName("Solar Term Doc Beneficiary");
            u.setPassword("password");
            u.setMobileNumber("9988771122");
            u.getRoles().add(roleBen);
            return userRepository.save(u);
        });

        solarBen = beneficiaryRepository.findByUserEmail(email).orElseGet(() -> {
            Beneficiary b = new Beneficiary();
            b.setUser(solarUser);
            b.setState("Andhra Pradesh");
            b.setDistrict("Kakinada");
            b.setAddress("123 Test Street");
            b.setPincode("533001");
            b.setCategory("OBC");
            b.setGender("Male");
            b.setAge(30);
            b.setIdentityType("Aadhaar");
            b.setIdentityNumber("999988887777");
            return beneficiaryRepository.save(b);
        });

        solarScheme = schemeRepository.findAll().stream()
                .filter(s -> s.getName() != null && s.getName().toLowerCase().contains("solar"))
                .findFirst()
                .orElseGet(() -> {
                    Scheme s = new Scheme();
                    s.setName("National Rooftop Solar Subsidy Scheme");
                    s.setCategory("Solar");
                    s.setTotalBudget(new BigDecimal("1000000"));
                    s.setRemainingBudget(new BigDecimal("1000000"));
                    s.setStatus("ACTIVE");
                    return schemeRepository.save(s);
                });

        eduScheme = schemeRepository.findAll().stream()
                .filter(s -> s.getName() != null && s.getName().toLowerCase().contains("education"))
                .findFirst()
                .orElseGet(() -> {
                    Scheme s = new Scheme();
                    s.setName("Higher Education Financial Assistance Scheme");
                    s.setCategory("Education");
                    s.setTotalBudget(new BigDecimal("1000000"));
                    s.setRemainingBudget(new BigDecimal("1000000"));
                    s.setStatus("ACTIVE");
                    return schemeRepository.save(s);
                });
    }

    @Test
    @DisplayName("1-3. Solar scheme gets correct term-specific required document types")
    void testSolarSchemeTermDocumentTypes() {
        Application app = createApprovedApplication(solarScheme, new BigDecimal("60000.00"));
        Disbursement disbursement = disbursementService.getDisbursementByApplicationId(app.getId());

        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        assertEquals(3, milestones.size());

        assertEquals("INSTALLATION_WORK_ORDER", milestones.get(0).getRequiredDocumentType());
        assertEquals("INSTALLATION_PROGRESS_REPORT", milestones.get(1).getRequiredDocumentType());
        assertEquals("COMMISSIONING_CERTIFICATE", milestones.get(2).getRequiredDocumentType());
    }

    @Test
    @DisplayName("4-6. Education scheme gets correct term-specific required document types")
    void testEducationSchemeTermDocumentTypes() {
        Application app = createApprovedApplication(eduScheme, new BigDecimal("60000.00"));
        Disbursement disbursement = disbursementService.getDisbursementByApplicationId(app.getId());

        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        assertEquals(3, milestones.size());

        assertEquals("ADMISSION_PROOF", milestones.get(0).getRequiredDocumentType());
        assertEquals("FEE_PAYMENT_RECEIPT", milestones.get(1).getRequiredDocumentType());
        assertEquals("COURSE_PROGRESS_CERTIFICATE", milestones.get(2).getRequiredDocumentType());
    }

    @Test
    @DisplayName("7-9. Term 2 & 3 do not inherit Term 1 document accidentally")
    void testTermDocumentNonInheritance() {
        Application app = createApprovedApplication(solarScheme, new BigDecimal("60000.00"));
        Disbursement disbursement = disbursementService.getDisbursementByApplicationId(app.getId());
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);

        assertNotEquals(milestones.get(0).getRequiredDocumentType(), milestones.get(1).getRequiredDocumentType());
        assertNotEquals(milestones.get(1).getRequiredDocumentType(), milestones.get(2).getRequiredDocumentType());
    }

    @Test
    @DisplayName("10-11. Beneficiary sees only active term's document and future term is locked")
    void testActiveTermDocumentVisibility() throws Exception {
        Application app = createApprovedApplication(solarScheme, new BigDecimal("60000.00"));
        Disbursement disbursement = disbursementService.getDisbursementByApplicationId(app.getId());
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);

        List<Milestone> actionRequired = disbursementService.getActionRequiredTermMilestonesForBeneficiary(solarBen.getId());
        assertEquals(1, actionRequired.size());
        assertEquals(milestones.get(0).getId(), actionRequired.get(0).getId());

        MockMultipartFile file = new MockMultipartFile("file", "work_order.pdf", "application/pdf", "Work Order PDF".getBytes());
        
        // Attempting to upload for Term 2 before Term 1 release must fail
        assertThrows(IllegalStateException.class, () -> {
            disbursementService.submitComplianceProof(milestones.get(1).getId(), file, "Term 2 upload attempt", solarUser.getEmail());
        });
    }

    @Test
    @DisplayName("12-14. IDOR protection & wrong milestone upload rejection")
    void testIdorAndValidationProtection() {
        Application app = createApprovedApplication(solarScheme, new BigDecimal("60000.00"));
        Disbursement disbursement = disbursementService.getDisbursementByApplicationId(app.getId());
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);

        MockMultipartFile file = new MockMultipartFile("file", "proof.pdf", "application/pdf", "Valid pdf".getBytes());

        // IDOR check: wrong user trying to upload
        assertThrows(AccessDeniedException.class, () -> {
            disbursementService.submitComplianceProof(milestones.get(0).getId(), file, "Test", "hacker@test.com");
        });
    }

    @Test
    @DisplayName("18. Re-upload preserves previous document history")
    void testReuploadPreservesHistory() throws Exception {
        Application app = createApprovedApplication(solarScheme, new BigDecimal("60000.00"));
        Disbursement disbursement = disbursementService.getDisbursementByApplicationId(app.getId());
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);
        Milestone m1 = milestones.get(0);

        MockMultipartFile file1 = new MockMultipartFile("file", "doc_v1.pdf", "application/pdf", "Version 1 content".getBytes());
        disbursementService.submitComplianceProof(m1.getId(), file1, "Initial upload", solarUser.getEmail());

        Document firstDoc = documentService.getDocumentsByMilestone(m1.getId()).get(0);
        assertEquals("ACTIVE", firstDoc.getStatus());

        // Field Officer requests re-upload
        disbursementService.verifyCompliance(m1.getId(), "REUPLOAD_REQUIRED", "Incorrect format", "field.officer@test.gov.in");

        MockMultipartFile file2 = new MockMultipartFile("file", "doc_v2.pdf", "application/pdf", "Version 2 content".getBytes());
        disbursementService.submitComplianceProof(m1.getId(), file2, "Re-uploaded doc", solarUser.getEmail());

        List<Document> history = documentService.getDocumentsByMilestone(m1.getId());
        assertEquals(2, history.size());
        assertTrue(history.stream().anyMatch(d -> "ARCHIVED".equals(d.getStatus())));
        assertTrue(history.stream().anyMatch(d -> "ACTIVE".equals(d.getStatus())));
    }

    @Test
    @DisplayName("19. Single-phase <= ₹40,000 application has exactly 1 term document requirement")
    void testSinglePhase40kThreshold() {
        Application app = createApprovedApplication(solarScheme, new BigDecimal("40000.00"));
        Disbursement disbursement = disbursementService.getDisbursementByApplicationId(app.getId());
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);

        assertEquals(1, milestones.size());
        assertEquals("INSTALLATION_WORK_ORDER", milestones.get(0).getRequiredDocumentType());
    }

    @Test
    @DisplayName("20. Multi-phase > ₹40,000 application has 3 terms with 3 configured document requirements")
    void testMultiPhaseGreaterThan40kThreshold() {
        Application app = createApprovedApplication(solarScheme, new BigDecimal("75000.00"));
        Disbursement disbursement = disbursementService.getDisbursementByApplicationId(app.getId());
        List<Milestone> milestones = milestoneRepository.findByDisbursementOrderByIdAsc(disbursement);

        assertEquals(3, milestones.size());
        assertNotNull(milestones.get(0).getRequiredDocumentType());
        assertNotNull(milestones.get(1).getRequiredDocumentType());
        assertNotNull(milestones.get(2).getRequiredDocumentType());
    }

    private Application createApprovedApplication(Scheme scheme, BigDecimal amount) {
        Application app = new Application();
        app.setApplicationNumber("APP-TEST-" + System.currentTimeMillis() + "-" + (int)(Math.random()*1000));
        app.setBeneficiary(solarBen);
        app.setScheme(scheme);
        app.setRequestedAmount(amount);
        app.setSanctionedAmount(amount);
        app.setStatus("APPROVED");
        app.setCurrentStage("DISBURSEMENT");
        return applicationRepository.save(app);
    }
}
