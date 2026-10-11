package com.example.digital_subsidy.repository;

import com.example.digital_subsidy.model.FieldOfficerOtp;
import com.example.digital_subsidy.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface FieldOfficerOtpRepository extends JpaRepository<FieldOfficerOtp, Long> {

    /**
     * Find the latest ACTIVE OTP for a given user (most recently created).
     */
    Optional<FieldOfficerOtp> findTopByUserAndStatusOrderByCreatedAtDesc(User user, String status);

    /**
     * Bulk-invalidate all ACTIVE OTPs for a user before creating a new one.
     * This ensures only one active OTP exists per user at any time.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE FieldOfficerOtp o SET o.status = 'INVALIDATED' " +
           "WHERE o.user = :user AND o.status = 'ACTIVE'")
    int invalidateActiveOtpsForUser(@Param("user") User user);

    /**
     * Find the most recently created OTP for a user (any status).
     * Used to enforce resend cooldown.
     */
    Optional<FieldOfficerOtp> findTopByUserOrderByCreatedAtDesc(User user);

    /**
     * Count active OTPs for a user (should always be 0 or 1).
     */
    long countByUserAndStatus(User user, String status);

    /**
     * Expire any OTPs that have passed their expiresAt time and are still ACTIVE.
     * Called periodically or lazily during verification.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE FieldOfficerOtp o SET o.status = 'EXPIRED' " +
           "WHERE o.status = 'ACTIVE' AND o.expiresAt < :now")
    int expireOverdueOtps(@Param("now") LocalDateTime now);
}
