package com.example.digital_subsidy.repository;

import com.example.digital_subsidy.model.Officer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OfficerRepository extends JpaRepository<Officer, Long> {

    Optional<Officer> findByUserEmail(String email);

    Optional<Officer> findByUserId(Long userId);

    List<Officer> findByOfficerType(String officerType);

    List<Officer> findByOfficerTypeAndStateAndDistrict(String officerType, String state, String district);

    long countByOfficerType(String officerType);

    boolean existsByEmail(String email);

    boolean existsByMobileNumber(String mobileNumber);
}
