package com.example.digital_subsidy.repository;

import com.example.digital_subsidy.model.AnomalyFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnomalyFlagRepository extends JpaRepository<AnomalyFlag, Long> {
    List<AnomalyFlag> findByApplicationId(Long applicationId);
    List<AnomalyFlag> findByStatus(String status);
    List<AnomalyFlag> findByApplicationBeneficiaryState(String state);
    List<AnomalyFlag> findByApplicationBeneficiaryStateAndApplicationBeneficiaryDistrict(String state, String district);
    long countByStatus(String status);
}
