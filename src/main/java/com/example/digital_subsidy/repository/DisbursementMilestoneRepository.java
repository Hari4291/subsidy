package com.example.digital_subsidy.repository;

import com.example.digital_subsidy.model.DisbursementMilestone;
import com.example.digital_subsidy.model.Scheme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DisbursementMilestoneRepository extends JpaRepository<DisbursementMilestone, Long> {
    List<DisbursementMilestone> findBySchemeOrderByDisplayOrderAsc(Scheme scheme);
    List<DisbursementMilestone> findBySchemeIdOrderByDisplayOrderAsc(Long schemeId);
    List<DisbursementMilestone> findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(Long schemeId);
    void deleteByScheme(Scheme scheme);
}
