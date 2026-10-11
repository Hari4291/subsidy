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
 * Handles the Finance Approver OTP authentication flow.
 */
@Controller
@RequestMapping("/finance-login")
public class FinanceApproverOtpController {

    private static final Logger logger = LoggerFactory.getLogger(FinanceApproverOtpController.class);

    private final OtpService otpService;
    private final AuditLogger auditLogger;

    public FinanceApproverOtpController(OtpService otpService, AuditLogger auditLogger) {
        this.otpService  = otpService;
        this.auditLogger = auditLogger;
    }

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
            otpService.generateAndSendOtp(email.trim(), "ROLE_FINANCE_APPROVER", request);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "If the account is eligible, an OTP has been sent to your email.",
                    "cooldownSeconds", 60
            ));
        } catch (OtpException ex) {
            logger.warn("[FINANCE-OTP] Send-OTP rejected for '{}': reason={}", email, ex.getReason());
            String userMessage = switch (ex.getReason()) {
                case ACCOUNT_INACTIVE  -> "Your account is inactive. Please contact the administrator.";
                case RESEND_COOLDOWN   -> ex.getMessage();
                case SMTP_FAILURE      -> "Failed to send OTP. Please try again after some time.";
                default                -> "If the account is eligible, an OTP has been sent to your email.";
            };
            return ResponseEntity.ok(Map.of(
                    "success", ex.getReason() == OtpException.Reason.RESEND_COOLDOWN,
                    "message", userMessage
            ));
        } catch (Exception ex) {
            logger.error("[FINANCE-OTP] Unexpected error during send-otp for '{}': {}", email, ex.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false,
                                 "message", "An unexpected error occurred. Please try again."));
        }
    }

    @PostMapping("/verify-otp")
    public String verifyOtp(
            @RequestParam("email") String email,
            @RequestParam("otp")   String otp,
            HttpServletRequest  request,
            HttpServletResponse response,
            HttpSession         session,
            org.springframework.ui.Model model) {

        if (email == null || email.isBlank() || otp == null || otp.isBlank()) {
            model.addAttribute("financeOtpError", "Email and OTP are required.");
            model.addAttribute("financeOtpStep", true);
            model.addAttribute("financeOtpEmail", email);
            return "login";
        }

        try {
            User user = otpService.verifyOtp(email.trim(), otp.trim(), "ROLE_FINANCE_APPROVER");

            List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                    .map(r -> new SimpleGrantedAuthority(r.getName()))
                    .collect(Collectors.toList());

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            user.getEmail(),
                            null,
                            authorities
                    );

            SecurityContext ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(authToken);
            SecurityContextHolder.setContext(ctx);

            session.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);

            auditLogger.log("FINANCE_APPROVER_LOGIN_SUCCESS", "User", user.getId(),
                    "Finance Approver login via OTP: " + user.getEmail());

            logger.info("[FINANCE-OTP] Finance Approver authenticated, redirecting to dashboard: {}", user.getEmail());

            return "redirect:/finance/dashboard";

        } catch (OtpException ex) {
            logger.warn("[FINANCE-OTP] Verify-OTP failed for '{}': reason={}", email, ex.getReason());

            String errorMsg = switch (ex.getReason()) {
                case OTP_EXPIRED           -> "OTP has expired. Please request a new OTP.";
                case MAX_ATTEMPTS_EXCEEDED -> "Maximum OTP verification attempts exceeded. Please request a new OTP.";
                case OTP_ALREADY_USED      -> "This OTP has already been used. Please request a new OTP.";
                case OTP_NOT_FOUND         -> "No active OTP found. Please request a new OTP.";
                default                    -> ex.getMessage();
            };

            auditLogger.log("OTP_VERIFICATION_FAILED", "User", null,
                    "Finance Approver OTP verification failed for email: " + email + " | reason: " + ex.getReason());

            model.addAttribute("financeOtpError", errorMsg);
            model.addAttribute("financeOtpStep", true);
            model.addAttribute("financeOtpEmail", email);
            return "login";

        } catch (Exception ex) {
            logger.error("[FINANCE-OTP] Unexpected error during verify-otp for '{}': {}", email, ex.getMessage());
            model.addAttribute("financeOtpError", "An unexpected error occurred. Please try again.");
            model.addAttribute("financeOtpStep", true);
            model.addAttribute("financeOtpEmail", email);
            return "login";
        }
    }

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
