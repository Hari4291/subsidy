package com.example.digital_subsidy.repository;

import com.example.digital_subsidy.model.Application;
import com.example.digital_subsidy.model.Beneficiary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {
    List<Beneficiary> findByBeneficiary(Beneficiary beneficiary);
    List<Application> findByBeneficiaryId(Long beneficiaryId);
    List<Application> findByBeneficiaryIdOrderByIdDesc(Long beneficiaryId);
    Optional<Application> findByApplicationNumber(String applicationNumber);

    List<Application> findByCurrentStage(String currentStage);
    List<Application> findByStatus(String status);
    List<Application> findByAssignedOfficerId(Long officerId);

    List<Application> findByCurrentStageAndBeneficiaryState(String currentStage, String state);
    List<Application> findByBeneficiaryState(String state);
    List<Application> findByBeneficiaryStateAndBeneficiaryDistrict(String state, String district);

    long countByStatus(String status);
    long countByCurrentStage(String currentStage);
    long countByBeneficiaryStateAndStatus(String state, String status);
    long countByBeneficiaryStateAndCurrentStage(String state, String stage);

    @Query("SELECT SUM(a.requestedAmount) FROM Application a WHERE a.status = 'APPROVED'")
    BigDecimal sumTotalApprovedGrantAmount();

    @Query("SELECT SUM(a.requestedAmount) FROM Application a WHERE a.status = 'APPROVED' AND a.beneficiary.state = :state")
    BigDecimal sumTotalApprovedGrantAmountByState(String state);

    @Query("SELECT SUM(a.requestedAmount) FROM Application a WHERE a.status = 'PENDING' AND a.beneficiary.state = :state")
    BigDecimal sumTotalPendingGrantAmountByState(String state);

    List<Application> findBySchemeId(Long schemeId);
}
