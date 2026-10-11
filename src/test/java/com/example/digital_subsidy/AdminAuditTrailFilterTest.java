package com.example.digital_subsidy;

import com.example.digital_subsidy.model.AuditLog;
import com.example.digital_subsidy.repository.AuditLogRepository;
import com.example.digital_subsidy.util.AuditLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class AdminAuditTrailFilterTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AuditLogger auditLogger;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("TEST 1: Admin Real-time Audit Trail includes operational roles and excludes BENEFICIARY")
    void test1_includesOperationalRolesAndExcludesBeneficiary() {
        AuditLog adminLog = auditLogRepository.save(new AuditLog("admin@gov.in", "ROLE_ADMIN", "SCHEME_CREATED", "Scheme", 1L, "Created scheme"));
        AuditLog fieldLog = auditLogRepository.save(new AuditLog("fo@gov.in", "ROLE_FIELD_OFFICER", "FIELD_VERIFIED", "Application", 2L, "Verified app"));
        AuditLog districtLog = auditLogRepository.save(new AuditLog("do@gov.in", "ROLE_DISTRICT_OFFICER", "SANCTIONED", "Application", 3L, "Sanctioned app"));
        AuditLog financeLog = auditLogRepository.save(new AuditLog("fa@gov.in", "ROLE_FINANCE_APPROVER", "RELEASED", "Disbursement", 4L, "Released term"));
        AuditLog benLog = auditLogRepository.save(new AuditLog("ben@gmail.com", "ROLE_BENEFICIARY", "APPLICATION_SUBMITTED", "Application", 5L, "Submitted app"));

        List<AuditLog> dashboardLogs = auditLogRepository.findTop50OperationalAuditLogs();

        assertThat(dashboardLogs)
                .extracting(AuditLog::getUserRole)
                .contains("ROLE_ADMIN", "ROLE_FIELD_OFFICER", "ROLE_DISTRICT_OFFICER", "ROLE_FINANCE_APPROVER")
                .doesNotContain("ROLE_BENEFICIARY");

        assertThat(dashboardLogs)
                .extracting(AuditLog::getId)
                .contains(adminLog.getId(), fieldLog.getId(), districtLog.getId(), financeLog.getId())
                .doesNotContain(benLog.getId());
    }

    @Test
    @DisplayName("TEST 2: Large volume of BENEFICIARY logs does not pollute or block operational logs")
    void test2_largeBeneficiaryVolumeDoesNotPolluteOperationalLogs() {
        for (int i = 1; i <= 100; i++) {
            auditLogRepository.save(new AuditLog("ben" + i + "@gmail.com", "ROLE_BENEFICIARY", "DOCUMENT_UPLOADED", "Document", (long) i, "Uploaded doc " + i));
        }

        AuditLog opLog1 = auditLogRepository.save(new AuditLog("admin@gov.in", "ROLE_ADMIN", "SCHEME_UPDATED", "Scheme", 101L, "Updated scheme"));
        AuditLog opLog2 = auditLogRepository.save(new AuditLog("fo@gov.in", "ROLE_FIELD_OFFICER", "INSPECTION_DONE", "Application", 102L, "Completed inspection"));

        List<AuditLog> dashboardLogs = auditLogRepository.findTop50OperationalAuditLogs();

        assertThat(dashboardLogs).hasSize(2);
        assertThat(dashboardLogs)
                .extracting(AuditLog::getId)
                .containsExactly(opLog2.getId(), opLog1.getId());
        assertThat(dashboardLogs)
                .extracting(AuditLog::getUserRole)
                .doesNotContain("ROLE_BENEFICIARY");
    }

    @Test
    @DisplayName("TEST 3: Latest-N filtering applies role exclusion BEFORE limiting results")
    void test3_latestNFilteringAppliesRoleExclusionBeforeLimiting() {
        LocalDateTime baseTime = LocalDateTime.now().minusHours(1);

        createTimedLog("ben1@gmail.com", "ROLE_BENEFICIARY", "BEN_ACTION_1", baseTime.plusMinutes(1));
        AuditLog finance = createTimedLog("fa@gov.in", "ROLE_FINANCE_APPROVER", "TERM_RELEASED", baseTime.plusMinutes(2));
        createTimedLog("ben2@gmail.com", "ROLE_BENEFICIARY", "BEN_ACTION_2", baseTime.plusMinutes(3));
        AuditLog admin = createTimedLog("admin@gov.in", "ROLE_ADMIN", "SCHEME_CONFIGURED", baseTime.plusMinutes(4));
        AuditLog field = createTimedLog("fo@gov.in", "ROLE_FIELD_OFFICER", "VERIFIED", baseTime.plusMinutes(5));
        createTimedLog("ben3@gmail.com", "ROLE_BENEFICIARY", "BEN_ACTION_3", baseTime.plusMinutes(6));
        AuditLog district = createTimedLog("do@gov.in", "ROLE_DISTRICT_OFFICER", "SANCTIONED", baseTime.plusMinutes(7));

        List<AuditLog> pageResult = auditLogRepository.findOperationalAuditLogs(PageRequest.of(0, 5));

        assertThat(pageResult).hasSize(4);
        assertThat(pageResult)
                .extracting(AuditLog::getId)
                .containsExactly(district.getId(), field.getId(), admin.getId(), finance.getId());
        assertThat(pageResult)
                .extracting(AuditLog::getUserRole)
                .doesNotContain("ROLE_BENEFICIARY");
    }

    @Test
    @DisplayName("TEST 4: Existing operational audit logging via AuditLogger works correctly")
    void test4_operationalAuditLoggingViaAuditLogger() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("fo.user@gov.in", "N/A", List.of(new SimpleGrantedAuthority("ROLE_FIELD_OFFICER")))
        );

        auditLogger.log("FIELD_VERIFICATION_COMPLETED", "Application", 99L, "All fields checked");

        List<AuditLog> dashboardLogs = auditLogRepository.findTop50OperationalAuditLogs();
        assertThat(dashboardLogs).hasSize(1);

        AuditLog log = dashboardLogs.get(0);
        assertThat(log.getUserEmail()).isEqualTo("fo.user@gov.in");
        assertThat(log.getUserRole()).isEqualTo("ROLE_FIELD_OFFICER");
        assertThat(log.getAction()).isEqualTo("FIELD_VERIFICATION_COMPLETED");
    }

    @Test
    @DisplayName("TEST 5: Beneficiary audit logs continue to be persisted but are hidden from Admin Audit Trail")
    void test5_beneficiaryAuditLogsPersistedButExcludedFromAdminTrail() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ben.user@gmail.com", "N/A", List.of(new SimpleGrantedAuthority("ROLE_BENEFICIARY")))
        );

        auditLogger.log("APPLICATION_SUBMITTED", "Application", 500L, "Beneficiary submitted application");

        List<AuditLog> allLogs = auditLogRepository.findAll();
        assertThat(allLogs).hasSize(1);
        assertThat(allLogs.get(0).getUserEmail()).isEqualTo("ben.user@gmail.com");
        assertThat(allLogs.get(0).getUserRole()).isEqualTo("ROLE_BENEFICIARY");

        List<AuditLog> adminDashboardLogs = auditLogRepository.findTop50OperationalAuditLogs();
        assertThat(adminDashboardLogs).isEmpty();
    }

    private AuditLog createTimedLog(String email, String role, String action, LocalDateTime time) {
        AuditLog log = new AuditLog(email, role, action, "Entity", 1L, "Remarks");
        log.setTimestamp(time);
        return auditLogRepository.save(log);
    }
}
