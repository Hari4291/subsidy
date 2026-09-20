package com.example.digital_subsidy.repository;

import com.example.digital_subsidy.model.Application;
import com.example.digital_subsidy.model.Verification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VerificationRepository extends JpaRepository<Verification, Long> {
    List<Verification> findByApplicationOrderByVerifiedAtDesc(Application application);
    List<Verification> findByOfficerUserEmailOrderByIdDesc(String email);
    List<Verification> findByOfficerRoleAndApplicationBeneficiaryDistrictOrderByIdDesc(String officerRole, String district);
}
