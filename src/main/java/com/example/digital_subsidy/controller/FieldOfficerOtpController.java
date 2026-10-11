package com.example.digital_subsidy.controller;

import com.example.digital_subsidy.model.User;
import com.example.digital_subsidy.service.OtpException;
import com.example.digital_subsidy.service.OtpService;
import com.example.digital_subsidy.util.AuditLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles the Field Officer OTP authentication flow.
 *
 * Endpoints (all whitelisted in SecurityConfig):
 *
 *   POST /field-officer-login/send-otp
 *   POST /field-officer-login/verify-otp
 *   GET  /field-officer-login/cooldown   (JSON — for countdown UI)
 *
 * Roles are loaded from the User entity retrieved after successful OTP
 * verification — they are NEVER accepted from the frontend.
 */
@Controller
@RequestMapping("/field-officer-login")
public class FieldOfficerOtpController {

    private static final Logger logger = LoggerFactory.getLogger(FieldOfficerOtpController.class);

    private final OtpService otpService;
    private final AuditLogger auditLogger;

    public FieldOfficerOtpController(OtpService otpService, AuditLogger auditLogger) {
        this.otpService   = otpService;
        this.auditLogger  = auditLogger;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SEND OTP
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Step 1: Receive the email, generate OTP, and dispatch via SMTP.
     * Returns JSON so the login page can update UI without a full page reload.
     */
    @PostMapping("/send-otp")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sendOtp(
            @RequestParam("email") String email,
            HttpServletRequest request) {

        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false,
                                 "message", "Email address is required."));
        }

        try {
            otpService.generateAndSendOtp(email.trim(), "ROLE_FIELD_OFFICER", request);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "If the account is eligible, an OTP has been sent to your email.",
                    "cooldownSeconds", 60
            ));
        } catch (OtpException ex) {
            logger.warn("[OTP-CONTROLLER] Send-OTP rejected for '{}': reason={}", email, ex.getReason());
            String userMessage = switch (ex.getReason()) {
                case ACCOUNT_INACTIVE  -> "Your account is inactive. Please contact the administrator.";
                case RESEND_COOLDOWN   -> ex.getMessage(); // already has friendly cooldown message
                case SMTP_FAILURE      -> "Failed to send OTP. Please try again after some time.";
                // For security: account not found and ineligible roles get the same message
                default                -> "If the account is eligible, an OTP has been sent to your email.";
            };
            return ResponseEntity.ok(Map.of(
                    "success", ex.getReason() == OtpException.Reason.RESEND_COOLDOWN,
                    "message", userMessage
            ));
        } catch (Exception ex) {
            logger.error("[OTP-CONTROLLER] Unexpected error during send-otp for '{}': {}", email, ex.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false,
                                 "message", "An unexpected error occurred. Please try again."));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VERIFY OTP & CREATE SPRING SECURITY AUTHENTICATION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Step 2: Verify the entered OTP. On success, create a real Spring Security
     * authentication and redirect to /field-officer/dashboard.
     * Roles are sourced exclusively from the database User entity.
     */
    @PostMapping("/verify-otp")
    public String verifyOtp(
            @RequestParam("email") String email,
            @RequestParam("otp")   String otp,
            HttpServletRequest  request,
            HttpServletResponse response,
            HttpSession         session,
            org.springframework.ui.Model model) {

        if (email == null || email.isBlank() || otp == null || otp.isBlank()) {
            model.addAttribute("otpError", "Email and OTP are required.");
            model.addAttribute("otpStep", true);
            model.addAttribute("otpEmail", email);
            return "login";
        }

        try {
            // Verify the OTP — returns the authenticated User on success
            User user = otpService.verifyOtp(email.trim(), otp.trim(), "ROLE_FIELD_OFFICER");

            // Build authorities from database roles — never from request
            List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                    .map(r -> new SimpleGrantedAuthority(r.getName()))
                    .collect(Collectors.toList());

            // Create a real Spring Security authentication token
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            user.getEmail(),
                            null,          // credentials are cleared after auth
                            authorities
                    );

            // Store in SecurityContext so Spring Security enforces authorization
            SecurityContext ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(authToken);
            SecurityContextHolder.setContext(ctx);

            // Persist SecurityContext in HTTP session (same as form login)
            session.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);

            auditLogger.log("FIELD_OFFICER_LOGIN_SUCCESS", "User", user.getId(),
                    "Field Officer login via OTP: " + user.getEmail());

            logger.info("[OTP] Field Officer authenticated, redirecting to dashboard: {}", user.getEmail());

            // Redirect to Field Officer dashboard (URL is hardcoded — not from request)
            return "redirect:/field-officer/dashboard";

        } catch (OtpException ex) {
            logger.warn("[OTP-CONTROLLER] Verify-OTP failed for '{}': reason={}", email, ex.getReason());

            String errorMsg = switch (ex.getReason()) {
                case OTP_EXPIRED           -> "OTP has expired. Please request a new OTP.";
                case MAX_ATTEMPTS_EXCEEDED -> "Maximum OTP verification attempts exceeded. Please request a new OTP.";
                case OTP_ALREADY_USED      -> "This OTP has already been used. Please request a new OTP.";
                case OTP_NOT_FOUND         -> "No active OTP found. Please request a new OTP.";
                default                    -> ex.getMessage(); // "Invalid OTP. N attempt(s) remaining."
            };

            auditLogger.log("OTP_VERIFICATION_FAILED", "User", null,
                    "OTP verification failed for email: " + email + " | reason: " + ex.getReason());

            model.addAttribute("otpError", errorMsg);
            model.addAttribute("otpStep", true);
            model.addAttribute("otpEmail", email);
            return "login";

        } catch (Exception ex) {
            logger.error("[OTP-CONTROLLER] Unexpected error during verify-otp for '{}': {}", email, ex.getMessage());
            model.addAttribute("otpError", "An unexpected error occurred. Please try again.");
            model.addAttribute("otpStep", true);
            model.addAttribute("otpEmail", email);
            return "login";
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // COOLDOWN CHECK (JSON)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the remaining resend cooldown in seconds.
     * Used by the UI countdown timer to show "Resend available in 00:XX".
     */
    @GetMapping("/cooldown")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getCooldown(
            @RequestParam("email") String email) {

        if (email == null || email.isBlank()) {
            return ResponseEntity.ok(Map.of("cooldownSeconds", 0));
        }

        long remaining = otpService.resendCooldownRemaining(email.trim());
        return ResponseEntity.ok(Map.of("cooldownSeconds", remaining));
    }
}
