package com.example.digital_subsidy.repository;

import com.example.digital_subsidy.model.ApplicationRestriction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationRestrictionRepository extends JpaRepository<ApplicationRestriction, Long> {

    List<ApplicationRestriction> findByBeneficiaryIdAndSchemeIdAndStatus(Long beneficiaryId, Long schemeId, String status);

    List<ApplicationRestriction> findByBeneficiaryIdAndStatus(Long beneficiaryId, String status);

    List<ApplicationRestriction> findByApplicationId(Long applicationId);

    @Query("SELECT r FROM ApplicationRestriction r WHERE r.beneficiary.id = :beneficiaryId AND r.scheme.id = :schemeId AND r.status = 'ACTIVE'")
    List<ApplicationRestriction> findActiveRestrictions(@Param("beneficiaryId") Long beneficiaryId, @Param("schemeId") Long schemeId);

    @Query("SELECT r FROM ApplicationRestriction r WHERE r.beneficiary.id = :beneficiaryId AND r.status = 'ACTIVE'")
    List<ApplicationRestriction> findAllActiveRestrictionsByBeneficiary(@Param("beneficiaryId") Long beneficiaryId);
}
