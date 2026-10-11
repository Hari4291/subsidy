package com.example.digital_subsidy;

import com.example.digital_subsidy.model.Role;
import com.example.digital_subsidy.model.User;
import com.example.digital_subsidy.repository.RoleRepository;
import com.example.digital_subsidy.repository.UserRepository;
import com.example.digital_subsidy.service.EmailService;
import com.example.digital_subsidy.service.OtpException;
import com.example.digital_subsidy.service.OtpService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class ExtendedOfficerOtpAuthTest {

    @Autowired private OtpService otpService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean private EmailService emailService;

    private static final String FIELD_EMAIL    = "pabbuletihari4291@gmail.com";
    private static final String DISTRICT_EMAIL = "kalyanramvase377@gmail.com";
    private static final String FINANCE_EMAIL  = "pabbuletihari0@gmail.com";

    private static final String ADMIN_EMAIL    = "johnmekala4277@gmail.com";

    private HttpServletRequest stubRequest() {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        Mockito.when(req.getRemoteAddr()).thenReturn("127.0.0.1");
        return req;
    }

    @BeforeEach
    void setUp() {
        Role roleField    = roleRepository.findByName("ROLE_FIELD_OFFICER").orElseGet(() -> roleRepository.save(new Role("ROLE_FIELD_OFFICER")));
        Role roleDistrict = roleRepository.findByName("ROLE_DISTRICT_OFFICER").orElseGet(() -> roleRepository.save(new Role("ROLE_DISTRICT_OFFICER")));
        Role roleFinance  = roleRepository.findByName("ROLE_FINANCE_APPROVER").orElseGet(() -> roleRepository.save(new Role("ROLE_FINANCE_APPROVER")));
        Role roleAdmin    = roleRepository.findByName("ROLE_ADMIN").orElseGet(() -> roleRepository.save(new Role("ROLE_ADMIN")));

        userRepository.findByEmail(FIELD_EMAIL).orElseGet(() -> {
            User u = new User();
            u.setEmail(FIELD_EMAIL);
            u.setFullName("Field Officer Test User");
            u.setMobileNumber("9111111111");
            u.setPassword(passwordEncoder.encode("FieldPwd123"));
            u.setEnabled(true);
            u.getRoles().add(roleField);
            return userRepository.save(u);
        });

        userRepository.findByEmail(DISTRICT_EMAIL).orElseGet(() -> {
            User u = new User();
            u.setEmail(DISTRICT_EMAIL);
            u.setFullName("District Officer Test User");
            u.setMobileNumber("9222222222");
            u.setPassword(passwordEncoder.encode("DistrictPwd123"));
            u.setEnabled(true);
            u.getRoles().add(roleDistrict);
            return userRepository.save(u);
        });

        userRepository.findByEmail(FINANCE_EMAIL).orElseGet(() -> {
            User u = new User();
            u.setEmail(FINANCE_EMAIL);
            u.setFullName("Finance Approver Test User");
            u.setMobileNumber("9333333333");
            u.setPassword(passwordEncoder.encode("FinancePwd123"));
            u.setEnabled(true);
            u.getRoles().add(roleFinance);
            return userRepository.save(u);
        });

        userRepository.findByEmail(ADMIN_EMAIL).orElseGet(() -> {
            User u = new User();
            u.setEmail(ADMIN_EMAIL);
            u.setFullName("System Administrator Test User");
            u.setMobileNumber("9444444444");
            u.setPassword(passwordEncoder.encode("AdminPwd123"));
            u.setEnabled(true);
            u.getRoles().add(roleAdmin);
            return userRepository.save(u);
        });
    }

    // ── SECTION A: Field Officer Tests ───────────────────────────────────────

    @Test
    @DisplayName("A1: Field Officer (pabbuletihari4291@gmail.com) can request OTP")
    void testFieldOfficerRequestOtp() throws Exception {
        otpService.generateAndSendOtp(FIELD_EMAIL, "ROLE_FIELD_OFFICER", stubRequest());
        verify(emailService, times(1)).sendOfficerOtpEmail(eq(FIELD_EMAIL), eq("Field Officer"), anyString(), anyInt());
    }

    @Test
    @DisplayName("A2: Field Officer OTP verification succeeds and grants ROLE_FIELD_OFFICER")
    void testFieldOfficerVerifyOtpSuccess() throws Exception {
        String[] capturedRaw = {null};
        doAnswer(inv -> { capturedRaw[0] = inv.getArgument(2); return null; })
                .when(emailService).sendOfficerOtpEmail(anyString(), anyString(), anyString(), anyInt());

        otpService.generateAndSendOtp(FIELD_EMAIL, "ROLE_FIELD_OFFICER", stubRequest());
        User authUser = otpService.verifyOtp(FIELD_EMAIL, capturedRaw[0], "ROLE_FIELD_OFFICER");

        assertThat(authUser).isNotNull();
        assertThat(authUser.getEmail()).isEqualTo(FIELD_EMAIL);
        boolean hasRole = authUser.getRoles().stream().anyMatch(r -> "ROLE_FIELD_OFFICER".equals(r.getName()));
        assertThat(hasRole).isTrue();
    }

    // ── SECTION B: District Officer Tests ────────────────────────────────────

    @Test
    @DisplayName("B1: District Officer (kalyanramvase377@gmail.com) can request OTP")
    void testDistrictOfficerRequestOtp() throws Exception {
        otpService.generateAndSendOtp(DISTRICT_EMAIL, "ROLE_DISTRICT_OFFICER", stubRequest());
        verify(emailService, times(1)).sendOfficerOtpEmail(eq(DISTRICT_EMAIL), eq("District Officer"), anyString(), anyInt());
    }

    @Test
    @DisplayName("B2: District Officer OTP verification succeeds and grants ROLE_DISTRICT_OFFICER")
    void testDistrictOfficerVerifyOtpSuccess() throws Exception {
        String[] capturedRaw = {null};
        doAnswer(inv -> { capturedRaw[0] = inv.getArgument(2); return null; })
                .when(emailService).sendOfficerOtpEmail(anyString(), anyString(), anyString(), anyInt());

        otpService.generateAndSendOtp(DISTRICT_EMAIL, "ROLE_DISTRICT_OFFICER", stubRequest());
        User authUser = otpService.verifyOtp(DISTRICT_EMAIL, capturedRaw[0], "ROLE_DISTRICT_OFFICER");

        assertThat(authUser).isNotNull();
        assertThat(authUser.getEmail()).isEqualTo(DISTRICT_EMAIL);
        boolean hasRole = authUser.getRoles().stream().anyMatch(r -> "ROLE_DISTRICT_OFFICER".equals(r.getName()));
        assertThat(hasRole).isTrue();
    }

    @Test
    @DisplayName("B3: Field Officer email cannot request or verify as District Officer")
    void testFieldOfficerCannotAuthenticateAsDistrictOfficer() {
        assertThatThrownBy(() -> otpService.generateAndSendOtp(FIELD_EMAIL, "ROLE_DISTRICT_OFFICER", stubRequest()))
                .isInstanceOf(OtpException.class)
                .extracting("reason")
                .isEqualTo(OtpException.Reason.ACCOUNT_NOT_FOUND);
    }

    @Test
    @DisplayName("B4: Finance Approver email cannot request or verify as District Officer")
    void testFinanceApproverCannotAuthenticateAsDistrictOfficer() {
        assertThatThrownBy(() -> otpService.generateAndSendOtp(FINANCE_EMAIL, "ROLE_DISTRICT_OFFICER", stubRequest()))
                .isInstanceOf(OtpException.class)
                .extracting("reason")
                .isEqualTo(OtpException.Reason.ACCOUNT_NOT_FOUND);
    }

    // ── SECTION C: Finance Approver Tests ────────────────────────────────────

    @Test
    @DisplayName("C1: Finance Approver (pabbuletihari0@gmail.com) can request OTP")
    void testFinanceApproverRequestOtp() throws Exception {
        otpService.generateAndSendOtp(FINANCE_EMAIL, "ROLE_FINANCE_APPROVER", stubRequest());
        verify(emailService, times(1)).sendOfficerOtpEmail(eq(FINANCE_EMAIL), eq("Finance Approver"), anyString(), anyInt());
    }

    @Test
    @DisplayName("C2: Finance Approver OTP verification succeeds and grants ROLE_FINANCE_APPROVER")
    void testFinanceApproverVerifyOtpSuccess() throws Exception {
        String[] capturedRaw = {null};
        doAnswer(inv -> { capturedRaw[0] = inv.getArgument(2); return null; })
                .when(emailService).sendOfficerOtpEmail(anyString(), anyString(), anyString(), anyInt());

        otpService.generateAndSendOtp(FINANCE_EMAIL, "ROLE_FINANCE_APPROVER", stubRequest());
        User authUser = otpService.verifyOtp(FINANCE_EMAIL, capturedRaw[0], "ROLE_FINANCE_APPROVER");

        assertThat(authUser).isNotNull();
        assertThat(authUser.getEmail()).isEqualTo(FINANCE_EMAIL);
        boolean hasRole = authUser.getRoles().stream().anyMatch(r -> "ROLE_FINANCE_APPROVER".equals(r.getName()));
        assertThat(hasRole).isTrue();
    }

    @Test
    @DisplayName("C3: Field Officer email cannot request or verify as Finance Approver")
    void testFieldOfficerCannotAuthenticateAsFinanceApprover() {
        assertThatThrownBy(() -> otpService.generateAndSendOtp(FIELD_EMAIL, "ROLE_FINANCE_APPROVER", stubRequest()))
                .isInstanceOf(OtpException.class)
                .extracting("reason")
                .isEqualTo(OtpException.Reason.ACCOUNT_NOT_FOUND);
    }

    @Test
    @DisplayName("C4: District Officer email cannot request or verify as Finance Approver")
    void testDistrictOfficerCannotAuthenticateAsFinanceApprover() {
        assertThatThrownBy(() -> otpService.generateAndSendOtp(DISTRICT_EMAIL, "ROLE_FINANCE_APPROVER", stubRequest()))
                .isInstanceOf(OtpException.class)
                .extracting("reason")
                .isEqualTo(OtpException.Reason.ACCOUNT_NOT_FOUND);
    }

    // ── SECTION D: System Administrator OTP Tests ─────────────────────────────

    @Test
    @DisplayName("D1: Admin (johnmekala4277@gmail.com) can request OTP")
    void testAdminRequestOtp() throws Exception {
        otpService.generateAndSendOtp(ADMIN_EMAIL, "ROLE_ADMIN", stubRequest());
        verify(emailService, times(1)).sendOfficerOtpEmail(eq(ADMIN_EMAIL), eq("System Administrator"), anyString(), anyInt());
    }

    @Test
    @DisplayName("D2: Admin OTP verification succeeds and grants ROLE_ADMIN")
    void testAdminVerifyOtpSuccess() throws Exception {
        String[] capturedRaw = {null};
        doAnswer(inv -> { capturedRaw[0] = inv.getArgument(2); return null; })
                .when(emailService).sendOfficerOtpEmail(anyString(), anyString(), anyString(), anyInt());

        otpService.generateAndSendOtp(ADMIN_EMAIL, "ROLE_ADMIN", stubRequest());
        User authUser = otpService.verifyOtp(ADMIN_EMAIL, capturedRaw[0], "ROLE_ADMIN");

        assertThat(authUser).isNotNull();
        assertThat(authUser.getEmail()).isEqualTo(ADMIN_EMAIL);
        boolean hasRole = authUser.getRoles().stream().anyMatch(r -> "ROLE_ADMIN".equals(r.getName()));
        assertThat(hasRole).isTrue();
    }

    @Test
    @DisplayName("D3: Field Officer email cannot request or verify as Admin")
    void testFieldOfficerCannotAuthenticateAsAdmin() {
        assertThatThrownBy(() -> otpService.generateAndSendOtp(FIELD_EMAIL, "ROLE_ADMIN", stubRequest()))
                .isInstanceOf(OtpException.class)
                .extracting("reason")
                .isEqualTo(OtpException.Reason.ACCOUNT_NOT_FOUND);
    }

    @Test
    @DisplayName("D4: District Officer email cannot request or verify as Admin")
    void testDistrictOfficerCannotAuthenticateAsAdmin() {
        assertThatThrownBy(() -> otpService.generateAndSendOtp(DISTRICT_EMAIL, "ROLE_ADMIN", stubRequest()))
                .isInstanceOf(OtpException.class)
                .extracting("reason")
                .isEqualTo(OtpException.Reason.ACCOUNT_NOT_FOUND);
    }

    @Test
    @DisplayName("D5: Finance Approver email cannot request or verify as Admin")
    void testFinanceApproverCannotAuthenticateAsAdmin() {
        assertThatThrownBy(() -> otpService.generateAndSendOtp(FINANCE_EMAIL, "ROLE_ADMIN", stubRequest()))
                .isInstanceOf(OtpException.class)
                .extracting("reason")
                .isEqualTo(OtpException.Reason.ACCOUNT_NOT_FOUND);
    }

    @Test
    @DisplayName("D6: Admin email cannot request or verify as Field Officer, District Officer, or Finance Approver")
    void testAdminCannotAuthenticateAsOfficerRoles() {
        assertThatThrownBy(() -> otpService.generateAndSendOtp(ADMIN_EMAIL, "ROLE_FIELD_OFFICER", stubRequest()))
                .isInstanceOf(OtpException.class)
                .extracting("reason")
                .isEqualTo(OtpException.Reason.ACCOUNT_NOT_FOUND);

        assertThatThrownBy(() -> otpService.generateAndSendOtp(ADMIN_EMAIL, "ROLE_DISTRICT_OFFICER", stubRequest()))
                .isInstanceOf(OtpException.class)
                .extracting("reason")
                .isEqualTo(OtpException.Reason.ACCOUNT_NOT_FOUND);

        assertThatThrownBy(() -> otpService.generateAndSendOtp(ADMIN_EMAIL, "ROLE_FINANCE_APPROVER", stubRequest()))
                .isInstanceOf(OtpException.class)
                .extracting("reason")
                .isEqualTo(OtpException.Reason.ACCOUNT_NOT_FOUND);
    }
}
