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

    long countByStatus(String status);

    @Query("SELECT SUM(a.requestedAmount) FROM Application a WHERE a.status = 'APPROVED'")
    BigDecimal sumTotalApprovedGrantAmount();
}
