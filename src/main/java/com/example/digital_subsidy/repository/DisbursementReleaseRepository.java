package com.example.digital_subsidy.repository;

import com.example.digital_subsidy.model.DisbursementRelease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DisbursementReleaseRepository extends JpaRepository<DisbursementRelease, Long> {

    List<DisbursementRelease> findByDisbursementIdOrderByReleaseDateDesc(Long disbursementId);

    List<DisbursementRelease> findByApplicationIdOrderByReleaseDateDesc(Long applicationId);

    @Query("SELECT r FROM DisbursementRelease r WHERE r.milestone.id = :milestoneId ORDER BY r.releaseDate DESC")
    List<DisbursementRelease> findByMilestoneId(@Param("milestoneId") Long milestoneId);
}
