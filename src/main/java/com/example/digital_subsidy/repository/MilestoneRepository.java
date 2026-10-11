package com.example.digital_subsidy.repository;

import com.example.digital_subsidy.model.Disbursement;
import com.example.digital_subsidy.model.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface MilestoneRepository extends JpaRepository<Milestone, Long> {
    List<Milestone> findByDisbursementOrderByIdAsc(Disbursement disbursement);
    List<Milestone> findByComplianceVerificationStatus(String complianceVerificationStatus);
    List<Milestone> findByComplianceVerificationStatusIn(List<String> statuses);
    List<Milestone> findByDueDateBeforeAndComplianceVerificationStatusNot(LocalDate date, String verifiedStatus);
    List<Milestone> findByDueDateBetweenAndComplianceVerificationStatusNot(LocalDate startDate, LocalDate endDate, String verifiedStatus);

    @Query("SELECT m FROM Milestone m WHERE m.disbursement.application.id = :applicationId ORDER BY m.stageNumber ASC")
    List<Milestone> findByApplicationIdOrderAsc(@Param("applicationId") Long applicationId);

    @Query("SELECT m FROM Milestone m WHERE m.disbursement.application.beneficiary.id = :beneficiaryId ORDER BY m.id ASC")
    List<Milestone> findByBeneficiaryId(@Param("beneficiaryId") Long beneficiaryId);
}
