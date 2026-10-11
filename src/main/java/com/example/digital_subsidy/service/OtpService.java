package com.example.digital_subsidy.service;

import com.example.digital_subsidy.model.FieldOfficerOtp;
import com.example.digital_subsidy.model.User;
import com.example.digital_subsidy.repository.FieldOfficerOtpRepository;
import com.example.digital_subsidy.repository.UserRepository;
import com.example.digital_subsidy.util.AuditLogger;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Core business logic for Field Officer OTP authentication.
 *
 * Security guarantees:
 *  - OTP is generated via SecureRandom (cryptographically secure)
 *  - Only the BCrypt hash of the OTP is persisted
 *  - Raw OTP is never logged or returned through APIs
 *  - New OTP always invalidates previous active OTPs
 *  - OTP is single-use; re-use is rejected
 *  - After max failed attempts the OTP is invalidated
 *  - Resend is rate-limited by cooldown period
 */
@Service
public class OtpService {

    private static final Logger logger = LoggerFactory.getLogger(OtpService.class);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int OTP_DIGITS = 6;
    private static final int OTP_LOWER_BOUND = 100_000; // inclusive
    private static final int OTP_UPPER_BOUND = 999_999; // inclusive

    @Value("${otp.expiry.minutes:5}")
    private int otpExpiryMinutes;

    @Value("${otp.max.attempts:5}")
    private int otpMaxAttempts;

    @Value("${otp.resend.cooldown.seconds:60}")
    private int otpResendCooldownSeconds;

    private final FieldOfficerOtpRepository otpRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditLogger auditLogger;

    public OtpService(FieldOfficerOtpRepository otpRepository,
                      UserRepository userRepository,
                      PasswordEncoder passwordEncoder,
                      EmailService emailService,
                      AuditLogger auditLogger) {
        this.otpRepository   = otpRepository;
        this.userRepository  = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService    = emailService;
        this.auditLogger     = auditLogger;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generate a new OTP for the given email, invalidate previous OTPs,
     * hash and persist the new OTP, then send it via SMTP.
     *
     * @param email   The Field Officer's registered email
     * @param request The incoming HTTP request (used for IP capture)
     * @throws OtpException for business-rule violations (inactive, wrong role, cooldown)
     * @throws RuntimeException for SMTP or infrastructure failures
     */
    @Transactional
    public void generateAndSendOtp(String email, String expectedRole, HttpServletRequest request) {
        String cleanEmail = email.trim().toLowerCase();

        // 1. Look up the user
        User user = userRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> new OtpException(OtpException.Reason.ACCOUNT_NOT_FOUND,
                        "No account found for: " + cleanEmail));

        // 2. Validate officer/admin role eligibility
        boolean isEligibleRole = user.getRoles().stream()
                .anyMatch(r -> "ROLE_FIELD_OFFICER".equals(r.getName())
                            || "ROLE_DISTRICT_OFFICER".equals(r.getName())
                            || "ROLE_FINANCE_APPROVER".equals(r.getName())
                            || "ROLE_ADMIN".equals(r.getName()));
        if (!isEligibleRole) {
            logger.warn("[OTP] OTP request for non-officer account: {}", cleanEmail);
            throw new OtpException(OtpException.Reason.ACCOUNT_NOT_FOUND,
                    "Account is not eligible for OTP authentication");
        }

        if (expectedRole != null && !expectedRole.isBlank()) {
            boolean hasExpectedRole = user.getRoles().stream()
                    .anyMatch(r -> expectedRole.equals(r.getName()));
            if (!hasExpectedRole) {
                logger.warn("[OTP] Role mismatch for {}: expected {}, user has different roles", cleanEmail, expectedRole);
                throw new OtpException(OtpException.Reason.ACCOUNT_NOT_FOUND,
                        "Account is not eligible for this role's OTP authentication");
            }
        }

        // 3. Account must be active/enabled
        if (!user.isEnabled()) {
            throw new OtpException(OtpException.Reason.ACCOUNT_INACTIVE,
                    "Account is not active");
        }

        // 4. Enforce resend cooldown
        enforceResendCooldown(user);

        // 5. Invalidate any existing ACTIVE OTPs for this user
        int invalidated = otpRepository.invalidateActiveOtpsForUser(user);
        if (invalidated > 0) {
            logger.debug("[OTP] Invalidated {} previous active OTP(s) for user: {}", invalidated, cleanEmail);
        }

        // 6. Generate cryptographically secure 6-digit OTP
        String rawOtp = generateSecureOtp();

        // 7. Hash the OTP — NEVER store the raw OTP
        String otpHash = passwordEncoder.encode(rawOtp);

        // 8. Persist OTP metadata
        FieldOfficerOtp otpRecord = new FieldOfficerOtp();
        otpRecord.setUser(user);
        otpRecord.setOtpHash(otpHash);
        otpRecord.setCreatedAt(LocalDateTime.now());
        otpRecord.setExpiresAt(LocalDateTime.now().plusMinutes(otpExpiryMinutes));
        otpRecord.setStatus("ACTIVE");
        otpRecord.setAttemptCount(0);
        otpRecord.setCreatedIp(extractIp(request));
        otpRepository.save(otpRecord);

        // 9. Send OTP via SMTP
        String roleTitle = resolveRoleTitle(user);
        try {
            emailService.sendOfficerOtpEmail(cleanEmail, roleTitle, rawOtp, otpExpiryMinutes);
        } catch (Exception e) {
            logger.error("[OTP] SMTP failure for: {} — {}", cleanEmail, e.getMessage());
            throw new OtpException(OtpException.Reason.SMTP_FAILURE,
                    "Failed to send OTP email. Please try again later.");
        }

        // 10. Audit — do NOT log rawOtp
        auditLogger.log("OTP_REQUESTED", "FieldOfficerOtp", otpRecord.getId(),
                "OTP requested for " + roleTitle + ": " + cleanEmail);

        logger.info("[OTP] OTP generated and sent for {}: {}", roleTitle, cleanEmail);
    }

    @Transactional
    public void generateAndSendOtp(String email, HttpServletRequest request) {
        generateAndSendOtp(email, null, request);
    }

    /**
     * Verify the supplied OTP for a Field Officer.
     *
     * @param email      Registered Field Officer email
     * @param suppliedOtp Raw OTP entered by the user
     * @return The authenticated User on success
     * @throws OtpException on any verification failure
     */
    @Transactional
    public User verifyOtp(String email, String suppliedOtp, String expectedRole) {
        String cleanEmail = email.trim().toLowerCase();

        // 1. Find user
        User user = userRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> new OtpException(OtpException.Reason.INVALID_OTP,
                        "Invalid OTP."));

        // Validate role if specified
        if (expectedRole != null && !expectedRole.isBlank()) {
            boolean hasExpectedRole = user.getRoles().stream()
                    .anyMatch(r -> expectedRole.equals(r.getName()));
            if (!hasExpectedRole) {
                logger.warn("[OTP] Role mismatch on verification for {}: expected {}", cleanEmail, expectedRole);
                throw new OtpException(OtpException.Reason.INVALID_OTP,
                        "Invalid OTP for this role.");
            }
        }

        // 2. Find the active OTP record
        FieldOfficerOtp otpRecord = otpRepository
                .findTopByUserAndStatusOrderByCreatedAtDesc(user, "ACTIVE")
                .orElseThrow(() -> new OtpException(OtpException.Reason.OTP_NOT_FOUND,
                        "No active OTP found. Please request a new OTP."));

        // 3. Check expiry
        if (!otpRecord.isNotExpired()) {
            otpRecord.setStatus("EXPIRED");
            otpRepository.save(otpRecord);
            auditLogger.log("OTP_EXPIRED", "FieldOfficerOtp", otpRecord.getId(),
                    "OTP expired for: " + cleanEmail);
            throw new OtpException(OtpException.Reason.OTP_EXPIRED,
                    "OTP has expired. Please request a new OTP.");
        }

        // 4. Check max attempts
        if (otpRecord.getAttemptCount() >= otpMaxAttempts) {
            otpRecord.setStatus("INVALIDATED");
            otpRepository.save(otpRecord);
            auditLogger.log("OTP_MAX_ATTEMPTS_EXCEEDED", "FieldOfficerOtp", otpRecord.getId(),
                    "Max attempts exceeded for: " + cleanEmail);
            throw new OtpException(OtpException.Reason.MAX_ATTEMPTS_EXCEEDED,
                    "Maximum OTP verification attempts exceeded. Please request a new OTP.");
        }

        // 5. Already used?
        if (otpRecord.isUsed()) {
            throw new OtpException(OtpException.Reason.OTP_ALREADY_USED,
                    "OTP has already been used. Please request a new OTP.");
        }

        // 6. Verify supplied OTP against stored hash
        if (!passwordEncoder.matches(suppliedOtp, otpRecord.getOtpHash())) {
            // Increment failed attempts
            otpRecord.setAttemptCount(otpRecord.getAttemptCount() + 1);

            if (otpRecord.getAttemptCount() >= otpMaxAttempts) {
                otpRecord.setStatus("INVALIDATED");
                otpRepository.save(otpRecord);
                auditLogger.log("OTP_VERIFICATION_FAILED", "FieldOfficerOtp", otpRecord.getId(),
                        "OTP verification failed (max attempts reached) for: " + cleanEmail);
                throw new OtpException(OtpException.Reason.MAX_ATTEMPTS_EXCEEDED,
                        "Maximum OTP verification attempts exceeded. Please request a new OTP.");
            }

            otpRepository.save(otpRecord);
            auditLogger.log("OTP_VERIFICATION_FAILED", "FieldOfficerOtp", otpRecord.getId(),
                    "Invalid OTP attempt " + otpRecord.getAttemptCount() + "/" + otpMaxAttempts
                            + " for: " + cleanEmail);
            throw new OtpException(OtpException.Reason.INVALID_OTP,
                    "Invalid OTP. " + (otpMaxAttempts - otpRecord.getAttemptCount()) + " attempt(s) remaining.");
        }

        // 7. OTP is valid — mark as used
        otpRecord.setUsed(true);
        otpRecord.setStatus("USED");
        otpRecord.setVerifiedAt(LocalDateTime.now());
        otpRepository.save(otpRecord);

        String roleTitle = resolveRoleTitle(user);
        auditLogger.log("OTP_VERIFICATION_SUCCESS", "FieldOfficerOtp", otpRecord.getId(),
                "OTP verified successfully for " + roleTitle + ": " + cleanEmail);

        logger.info("[OTP] Successful OTP verification for {}: {}", roleTitle, cleanEmail);
        return user;
    }

    @Transactional
    public User verifyOtp(String email, String suppliedOtp) {
        return verifyOtp(email, suppliedOtp, null);
    }

    /**
     * Returns how many seconds remain before a resend is allowed.
     * Returns 0 if resend is immediately allowed.
     */
    public long resendCooldownRemaining(String email) {
        String cleanEmail = email.trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmail(cleanEmail);
        if (userOpt.isEmpty()) return 0;

        Optional<FieldOfficerOtp> lastOtp = otpRepository.findTopByUserOrderByCreatedAtDesc(userOpt.get());
        if (lastOtp.isEmpty()) return 0;

        LocalDateTime allowedAt = lastOtp.get().getCreatedAt().plusSeconds(otpResendCooldownSeconds);
        long secondsRemaining = ChronoUnit.SECONDS.between(LocalDateTime.now(), allowedAt);
        return Math.max(0, secondsRemaining);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private String resolveRoleTitle(User user) {
        if (user == null || user.getRoles() == null) return "User";
        boolean isAdmin = user.getRoles().stream().anyMatch(r -> "ROLE_ADMIN".equals(r.getName()));
        if (isAdmin) return "System Administrator";
        boolean isDistrict = user.getRoles().stream().anyMatch(r -> "ROLE_DISTRICT_OFFICER".equals(r.getName()));
        if (isDistrict) return "District Officer";
        boolean isFinance = user.getRoles().stream().anyMatch(r -> "ROLE_FINANCE_APPROVER".equals(r.getName()));
        if (isFinance) return "Finance Approver";
        boolean isField = user.getRoles().stream().anyMatch(r -> "ROLE_FIELD_OFFICER".equals(r.getName()));
        if (isField) return "Field Officer";
        return "User";
    }

    /** Generates a cryptographically secure 6-digit numeric OTP string. */
    public String generateSecureOtp() {
        // Produces a number in [100000, 999999] inclusive — always 6 digits
        int otp = OTP_LOWER_BOUND + SECURE_RANDOM.nextInt(OTP_UPPER_BOUND - OTP_LOWER_BOUND + 1);
        return String.valueOf(otp);
    }

    /** Enforces minimum time between resend requests. */
    private void enforceResendCooldown(User user) {
        Optional<FieldOfficerOtp> lastOtp = otpRepository.findTopByUserOrderByCreatedAtDesc(user);
        if (lastOtp.isPresent()) {
            LocalDateTime allowedAt = lastOtp.get().getCreatedAt().plusSeconds(otpResendCooldownSeconds);
            if (LocalDateTime.now().isBefore(allowedAt)) {
                long secondsLeft = ChronoUnit.SECONDS.between(LocalDateTime.now(), allowedAt);
                throw new OtpException(OtpException.Reason.RESEND_COOLDOWN,
                        "Please wait " + secondsLeft + " second(s) before requesting a new OTP.");
            }
        }
    }

    /** Extract client IP from request, respecting X-Forwarded-For if present. */
    private String extractIp(HttpServletRequest request) {
        if (request == null) return null;
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
