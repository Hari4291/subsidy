package com.example.digital_subsidy.service;

/**
 * Typed exception for all OTP business-rule violations.
 * Each Reason maps to a specific user-facing message and HTTP outcome.
 */
public class OtpException extends RuntimeException {

    public enum Reason {
        ACCOUNT_NOT_FOUND,
        ACCOUNT_INACTIVE,
        RESEND_COOLDOWN,
        SMTP_FAILURE,
        OTP_NOT_FOUND,
        OTP_EXPIRED,
        OTP_ALREADY_USED,
        INVALID_OTP,
        MAX_ATTEMPTS_EXCEEDED
    }

    private final Reason reason;

    public OtpException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
