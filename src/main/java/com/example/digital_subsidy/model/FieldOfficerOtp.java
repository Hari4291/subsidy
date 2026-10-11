package com.example.digital_subsidy.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Stores OTP metadata for Field Officer email-based login.
 * The raw OTP is NEVER persisted — only its BCrypt hash is stored.
 */
@Entity
@Table(
    name = "field_officer_otp",
    indexes = {
        @Index(name = "idx_fo_otp_user", columnList = "user_id"),
        @Index(name = "idx_fo_otp_status", columnList = "status"),
        @Index(name = "idx_fo_otp_expires", columnList = "expires_at")
    }
)
public class FieldOfficerOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Reference to the Field Officer's User record */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * BCrypt hash of the 6-digit OTP.
     * The raw OTP is never stored.
     */
    @Column(name = "otp_hash", nullable = false, length = 100)
    private String otpHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** Number of failed verification attempts for this OTP */
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    /**
     * Whether this OTP has been successfully used for login.
     * Once true, the OTP cannot be used again.
     */
    @Column(name = "used", nullable = false)
    private boolean used = false;

    /** When the OTP was successfully verified */
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    /**
     * Lifecycle status: ACTIVE | USED | EXPIRED | INVALIDATED
     * ACTIVE   — OTP was generated and is eligible for verification
     * USED     — OTP was successfully verified (single-use)
     * EXPIRED  — OTP passed its expiry time
     * INVALIDATED — Superseded by a new OTP or max attempts exceeded
     */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    /** Optional: IP address of the OTP request (for audit purposes) */
    @Column(name = "created_ip", length = 50)
    private String createdIp;

    public FieldOfficerOtp() {}

    // ── Getters & Setters ────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getOtpHash() { return otpHash; }
    public void setOtpHash(String otpHash) { this.otpHash = otpHash; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }

    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }

    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedIp() { return createdIp; }
    public void setCreatedIp(String createdIp) { this.createdIp = createdIp; }

    // ── Domain helpers ────────────────────────────────────────────────────────

    /** Returns true if this OTP has not yet expired */
    public boolean isNotExpired() {
        return LocalDateTime.now().isBefore(expiresAt);
    }

    /** Returns true if this OTP is still eligible for verification */
    public boolean isEligible(int maxAttempts) {
        return "ACTIVE".equals(status)
            && !used
            && isNotExpired()
            && attemptCount < maxAttempts;
    }
}
