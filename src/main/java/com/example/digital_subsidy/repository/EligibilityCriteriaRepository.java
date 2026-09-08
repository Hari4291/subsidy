package com.example.digital_subsidy.repository;

import com.example.digital_subsidy.model.EligibilityCriteria;
import com.example.digital_subsidy.model.Scheme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EligibilityCriteriaRepository extends JpaRepository<EligibilityCriteria, Long> {
    List<EligibilityCriteria> findByScheme(Scheme scheme);
    List<EligibilityCriteria> findBySchemeId(Long schemeId);
    List<EligibilityCriteria> findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(Long schemeId);
    List<EligibilityCriteria> findBySchemeIdOrderByDisplayOrderAsc(Long schemeId);
    
    @org.springframework.transaction.annotation.Transactional
    void deleteByScheme(Scheme scheme);
}
