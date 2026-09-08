package com.example.digital_subsidy.repository;

import com.example.digital_subsidy.model.Disbursement;
import com.example.digital_subsidy.model.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MilestoneRepository extends JpaRepository<Milestone, Long> {
    List<Milestone> findByDisbursementOrderByIdAsc(Disbursement disbursement);
}
