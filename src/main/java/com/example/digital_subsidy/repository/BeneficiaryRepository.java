package com.example.digital_subsidy.repository;

import com.example.digital_subsidy.model.Beneficiary;
import com.example.digital_subsidy.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long> {
    Optional<Beneficiary> findByUser(User user);
    Optional<Beneficiary> findByUserEmail(String email);
    boolean existsByIdentityNumber(String identityNumber);
}
