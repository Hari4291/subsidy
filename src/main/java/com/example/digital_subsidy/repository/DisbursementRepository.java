package com.example.digital_subsidy.repository;

import com.example.digital_subsidy.model.Application;
import com.example.digital_subsidy.model.Disbursement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface DisbursementRepository extends JpaRepository<Disbursement, Long> {
    Optional<Disbursement> findByApplication(Application application);

    @Query("""
        SELECT DISTINCT d
        FROM Disbursement d
        LEFT JOIN FETCH d.milestones
        WHERE d.application.id = :applicationId
    """)
    Optional<Disbursement> findByApplicationIdWithMilestones(@Param("applicationId") Long applicationId);

    @Query("SELECT SUM(d.disbursedAmount) FROM Disbursement d")
    BigDecimal sumTotalDisbursedAmount();

    @Query("SELECT SUM(d.disbursedAmount) FROM Disbursement d WHERE d.application.beneficiary.state = :state")
    BigDecimal sumTotalDisbursedAmountByState(@Param("state") String state);
}
