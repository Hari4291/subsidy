package com.example.digital_subsidy.repository;

import com.example.digital_subsidy.model.DisbursementRelease;
import com.example.digital_subsidy.model.FundUtilization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface FundUtilizationRepository extends JpaRepository<FundUtilization, Long> {

    Optional<FundUtilization> findByRelease(DisbursementRelease release);

    List<FundUtilization> findByApplicationId(Long applicationId);

    List<FundUtilization> findBySchemeId(Long schemeId);

    List<FundUtilization> findByState(String state);

    List<FundUtilization> findByDistrict(String district);

    @Query("SELECT SUM(fu.utilizedAmount) FROM FundUtilization fu")
    BigDecimal sumTotalUtilizedAmount();

    @Query("SELECT SUM(fu.utilizedAmount) FROM FundUtilization fu WHERE fu.scheme.id = :schemeId")
    BigDecimal sumTotalUtilizedAmountBySchemeId(@Param("schemeId") Long schemeId);

    @Query("SELECT SUM(fu.utilizedAmount) FROM FundUtilization fu WHERE UPPER(fu.state) = UPPER(:state)")
    BigDecimal sumTotalUtilizedAmountByState(@Param("state") String state);

    @Query("SELECT SUM(fu.utilizedAmount) FROM FundUtilization fu WHERE UPPER(fu.district) = UPPER(:district)")
    BigDecimal sumTotalUtilizedAmountByDistrict(@Param("district") String district);

    @Query("SELECT SUM(fu.utilizedAmount) FROM FundUtilization fu WHERE UPPER(fu.state) = UPPER(:state) AND UPPER(fu.district) = UPPER(:district)")
    BigDecimal sumTotalUtilizedAmountByStateAndDistrict(@Param("state") String state, @Param("district") String district);
}
