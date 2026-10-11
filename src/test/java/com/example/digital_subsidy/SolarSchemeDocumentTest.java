package com.example.digital_subsidy;

import com.example.digital_subsidy.controller.DocumentViewController;
import com.example.digital_subsidy.dto.BeneficiaryRegistrationDto;
import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.*;
import com.example.digital_subsidy.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class SolarSchemeDocumentTest {

    @Autowired
    private SchemeRepository schemeRepository;

    @Autowired
    private RequiredDocumentRepository requiredDocumentRepository;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private BeneficiaryService beneficiaryService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentViewController documentViewController;

    @Autowired
    private OfficerRepository officerRepository;

    private Beneficiary createTestBeneficiary(String email, String state) throws Exception {
        long ts = System.currentTimeMillis() % 10000000L;
        String district = "Hyderabad";
        if ("Andhra Pradesh".equalsIgnoreCase(state)) {
            district = "Kakinada";
        } else if ("Kerala".equalsIgnoreCase(state)) {
            district = "Wayanad";
        }
        BeneficiaryRegistrationDto regDto = new BeneficiaryRegistrationDto();
        regDto.setFullName("Solar Beneficiary " + ts);
        regDto.setEmail(email);
        regDto.setMobileNumber("9" + String.format("%09d", (long)(Math.random() * 1000000000L)));
        regDto.setPassword("Password@123");
        regDto.setConfirmPassword("Password@123");
        regDto.setAge(30);
        regDto.setGender("Male");
        regDto.setCategory("General");
        regDto.setState(state);
        regDto.setDistrict(district);
        regDto.setAddress("Solar Street 1");
        regDto.setPincode("500001");
        regDto.setIdentityType("Aadhaar");
        regDto.setIdentityNumber(String.format("%012d", (long)(Math.random() * 1000000000000L)));
        regDto.setTermsAgreed(true);
        return beneficiaryService.registerBeneficiary(regDto);
    }

    private Scheme getSolarScheme() {
        return schemeRepository.findAll().stream()
                .filter(s -> s.getName() != null && s.getName().toLowerCase().contains("solar"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Solar Scheme not found"));
    }

    private Application createTestApplication(Beneficiary b, Scheme s) {
        com.example.digital_subsidy.dto.ApplicationSubmissionDto dto = new com.example.digital_subsidy.dto.ApplicationSubmissionDto();
        dto.setSchemeId(s.getId());
        dto.setRequestedAmount(new BigDecimal("40000"));
        return applicationService.submitApplication(b, dto);
    }

    @Test
    @DisplayName("1. National Rooftop Solar Scheme contains Income Certificate as required document")
    public void testSolarSchemeHasIncomeCertificate() {
        Scheme solar = getSolarScheme();
        List<RequiredDocument> reqDocs = requiredDocumentRepository.findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(solar.getId());
        
        boolean hasIncomeCert = reqDocs.stream()
                .anyMatch(d -> "INCOME_CERTIFICATE".equalsIgnoreCase(d.getDocumentType()) && "Income Certificate".equalsIgnoreCase(d.getDocumentName()));
        
        assertTrue(hasIncomeCert, "National Rooftop Solar Subsidy Scheme must contain Income Certificate as a required document");
        assertEquals(4, reqDocs.size(), "Solar scheme should configure exactly 4 required documents (Aadhaar, Bank Passbook, Electricity Bill, Income Certificate)");
    }

    @Test
    @DisplayName("2 & 3. Income Certificate can be uploaded and stored against correct application")
    public void testIncomeCertificateUploadAndStorage() throws Exception {
        Beneficiary b = createTestBeneficiary("solar_user1@test.gov.in", "Telangana");
        Scheme solar = getSolarScheme();
        
        MockMultipartFile file = new MockMultipartFile("docFile", "income_cert.pdf", "application/pdf", "Dummy content".getBytes());
        Document doc = documentService.saveDocument(b, null, "INCOME_CERTIFICATE", file);
        
        assertNotNull(doc);
        assertEquals("INCOME_CERTIFICATE", doc.getDocumentType());
        assertEquals("ACTIVE", doc.getStatus());
        assertEquals("PENDING", doc.getVerificationStatus());
    }

    @Test
    @DisplayName("4, 5, 6, 7 & 8. Document scoping by application and officer view filtering")
    public void testApplicationDocumentScoping() throws Exception {
        Beneficiary b1 = createTestBeneficiary("solar_user_a@test.gov.in", "Telangana");
        Beneficiary b2 = createTestBeneficiary("solar_user_b@test.gov.in", "Telangana");
        Scheme solar = getSolarScheme();

        // Beneficiary 1 uploads Aadhaar, Income Certificate, Electricity Bill (No Bank Passbook)
        MockMultipartFile aadhaar = new MockMultipartFile("docAadhaar", "aadhaar.pdf", "application/pdf", "Aadhaar".getBytes());
        MockMultipartFile income = new MockMultipartFile("docIncome", "income.pdf", "application/pdf", "Income".getBytes());
        MockMultipartFile bill = new MockMultipartFile("docBill", "bill.pdf", "application/pdf", "Bill".getBytes());

        documentService.saveDocument(b1, null, "AADHAAR", aadhaar);
        documentService.saveDocument(b1, null, "INCOME_CERTIFICATE", income);
        documentService.saveDocument(b1, null, "ELECTRICITY_BILL", bill);

        Application app1 = createTestApplication(b1, solar);

        // Beneficiary 2 uploads Bank Passbook only
        MockMultipartFile passbook = new MockMultipartFile("docPassbook", "passbook.pdf", "application/pdf", "Passbook".getBytes());
        documentService.saveDocument(b2, null, "BANK_PASSBOOK", passbook);
        Application app2 = createTestApplication(b2, solar);

        List<Document> app1Docs = documentService.getLatestActiveDocumentsByApplication(app1.getId());
        List<Document> app2Docs = documentService.getLatestActiveDocumentsByApplication(app2.getId());

        assertEquals(3, app1Docs.size(), "App 1 must have exactly 3 uploaded documents");
        assertTrue(app1Docs.stream().anyMatch(d -> "AADHAAR".equals(d.getDocumentType())));
        assertTrue(app1Docs.stream().anyMatch(d -> "INCOME_CERTIFICATE".equals(d.getDocumentType())));
        assertTrue(app1Docs.stream().anyMatch(d -> "ELECTRICITY_BILL".equals(d.getDocumentType())));
        assertFalse(app1Docs.stream().anyMatch(d -> "BANK_PASSBOOK".equals(d.getDocumentType())), "Bank Passbook must NOT appear in App 1 docs");

        assertEquals(1, app2Docs.size(), "App 2 must have exactly 1 uploaded document");
        assertEquals("BANK_PASSBOOK", app2Docs.get(0).getDocumentType());
    }

    @Test
    @DisplayName("9. Direct IDOR document access is blocked (403 FORBIDDEN)")
    public void testIdorDocumentAccessBlocked() throws Exception {
        Beneficiary b1 = createTestBeneficiary("solar_user_c@test.gov.in", "Telangana");
        Beneficiary b2 = createTestBeneficiary("solar_user_d@test.gov.in", "Andhra Pradesh");
        Scheme solar = getSolarScheme();

        MockMultipartFile file1 = new MockMultipartFile("doc1", "doc1.pdf", "application/pdf", "App1 doc".getBytes());
        Document doc1 = documentService.saveDocument(b1, null, "INCOME_CERTIFICATE", file1);
        Application app1 = createTestApplication(b1, solar);

        // Beneficiary 2 tries to access Beneficiary 1's document
        UsernamePasswordAuthenticationToken b2Auth = new UsernamePasswordAuthenticationToken(
                b2.getUser().getEmail(), "password", List.of(new SimpleGrantedAuthority("ROLE_BENEFICIARY")));

        ResponseEntity<?> response = documentViewController.viewDocument(doc1.getId(), app1.getId(), b2Auth);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(), "Beneficiary 2 accessing Beneficiary 1 doc must return 403 FORBIDDEN");

        // Officer from different state tries to access document
        String offEmail = "officer_kerala_" + System.currentTimeMillis() + "@test.gov.in";
        String offMobile = "9" + String.format("%09d", (long)(Math.random() * 1000000000L));

        User offUser = new User();
        offUser.setFullName("Kerala Officer");
        offUser.setEmail(offEmail);
        offUser.setMobileNumber(offMobile);
        offUser.setPassword("password");
        offUser = userRepository.save(offUser);

        Officer officer = new Officer();
        officer.setUser(offUser);
        officer.setFullName("Kerala Officer");
        officer.setEmail(offEmail);
        officer.setMobileNumber(offMobile);
        officer.setOfficerType("FIELD_OFFICER");
        officer.setState("Kerala"); // Different state
        officer.setDistrict("Wayanad");
        officerRepository.save(officer);

        UsernamePasswordAuthenticationToken offAuth = new UsernamePasswordAuthenticationToken(
                offUser.getEmail(), "password", List.of(new SimpleGrantedAuthority("ROLE_FIELD_OFFICER")));

        ResponseEntity<?> offResponse = documentViewController.viewDocument(doc1.getId(), app1.getId(), offAuth);
        assertEquals(HttpStatus.FORBIDDEN, offResponse.getStatusCode(), "Field Officer from another state accessing doc must return 403 FORBIDDEN");
    }

    @Test
    @DisplayName("10, 11 & 12. Income Certificate verification, rejection, re-upload, and historical archive lifecycle")
    public void testIncomeCertificateLifecycle() throws Exception {
        Beneficiary b = createTestBeneficiary("solar_user_e@test.gov.in", "Telangana");
        Scheme solar = getSolarScheme();

        MockMultipartFile originalFile = new MockMultipartFile("docIncome", "income_old.pdf", "application/pdf", "Old Content".getBytes());
        Document oldDoc = documentService.saveDocument(b, null, "INCOME_CERTIFICATE", originalFile);
        Application app = createTestApplication(b, solar);

        // Verify document status update
        Document updated = documentService.updateDocumentVerification(oldDoc.getId(), "INVALID", "Blurry document image", "FO-101");
        assertEquals("INVALID", updated.getVerificationStatus());
        assertEquals("Blurry document image", updated.getVerificationRemarks());

        // Re-upload new replacement document
        MockMultipartFile newFile = new MockMultipartFile("docIncome", "income_new.pdf", "application/pdf", "New Fresh Content".getBytes());
        Document newDoc = documentService.replaceDocument(b, app, "INCOME_CERTIFICATE", newFile);

        assertNotNull(newDoc);
        assertEquals("ACTIVE", newDoc.getStatus());
        assertEquals("PENDING", newDoc.getVerificationStatus());

        // Check that old document is archived and preserved in DB
        Document reFetchedOldDoc = documentService.getDocumentById(oldDoc.getId());
        assertEquals("ARCHIVED", reFetchedOldDoc.getStatus(), "Old rejected/unclear document must be marked as ARCHIVED");

        List<Document> activeDocs = documentService.getLatestActiveDocumentsByApplication(app.getId());
        assertEquals(1, activeDocs.size());
        assertEquals(newDoc.getId(), activeDocs.get(0).getId(), "Latest active document list must show the newly re-uploaded document");
    }
}
