package com.example.digital_subsidy.repository;

import com.example.digital_subsidy.model.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RegionRepository extends JpaRepository<Region, Long> {
    
    @Query("SELECT DISTINCT r.stateName FROM Region r WHERE r.active = true ORDER BY r.stateName")
    List<String> findDistinctStateNames();

    List<Region> findByStateNameAndActiveTrueOrderByDistrictName(String stateName);
}
