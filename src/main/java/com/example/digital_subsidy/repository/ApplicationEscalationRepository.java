package com.example.digital_subsidy.repository;

import com.example.digital_subsidy.model.ApplicationEscalation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationEscalationRepository extends JpaRepository<ApplicationEscalation, Long> {
    Optional<ApplicationEscalation> findByApplicationIdAndStatus(Long applicationId, String status);
    List<ApplicationEscalation> findByStatus(String status);
    List<ApplicationEscalation> findBySeverity(String severity);
    List<ApplicationEscalation> findByApplicationBeneficiaryState(String state);
    long countByStatus(String status);
}
