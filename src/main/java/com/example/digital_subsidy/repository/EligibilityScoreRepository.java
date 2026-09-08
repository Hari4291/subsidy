package com.example.digital_subsidy.repository;

import com.example.digital_subsidy.model.Application;
import com.example.digital_subsidy.model.EligibilityScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EligibilityScoreRepository extends JpaRepository<EligibilityScore, Long> {
    Optional<EligibilityScore> findByApplication(Application application);
}
