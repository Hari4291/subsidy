package com.example.digital_subsidy.repository;

import com.example.digital_subsidy.model.Grievance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GrievanceRepository extends JpaRepository<Grievance, Long> {
    Optional<Grievance> findByGrievanceNumber(String grievanceNumber);
    List<Grievance> findByBeneficiaryIdOrderByIdDesc(Long beneficiaryId);
    List<Grievance> findByAssignedOfficerIdOrderByIdDesc(Long officerId);

    @Query("SELECT g FROM Grievance g WHERE g.beneficiary IS NOT NULL AND (LOWER(TRIM(g.beneficiary.district)) = LOWER(TRIM(:district)) OR LOWER(TRIM(g.beneficiary.district)) LIKE CONCAT('%', LOWER(TRIM(:district)), '%') OR LOWER(TRIM(:district)) LIKE CONCAT('%', LOWER(TRIM(g.beneficiary.district)), '%')) ORDER BY g.id DESC")
    List<Grievance> findByDistrictIgnoreCaseAndTrim(@Param("district") String district);

    @Query("SELECT g FROM Grievance g WHERE g.beneficiary IS NOT NULL AND (LOWER(TRIM(g.beneficiary.state)) = LOWER(TRIM(:state)) OR LOWER(TRIM(g.beneficiary.state)) LIKE CONCAT('%', LOWER(TRIM(:state)), '%') OR LOWER(TRIM(:state)) LIKE CONCAT('%', LOWER(TRIM(g.beneficiary.state)), '%')) ORDER BY g.id DESC")
    List<Grievance> findByStateIgnoreCaseAndTrim(@Param("state") String state);

    List<Grievance> findByBeneficiaryStateOrderByIdDesc(String state);
    List<Grievance> findByBeneficiaryDistrictOrderByIdDesc(String district);
    List<Grievance> findByBeneficiaryDistrictAndStatusOrderByIdDesc(String district, String status);
    List<Grievance> findByBeneficiaryStateAndStatusOrderByIdDesc(String state, String status);

    long countByStatus(String status);
    long countByBeneficiaryDistrict(String district);
    long countByBeneficiaryDistrictAndStatus(String district, String status);
    long countByBeneficiaryState(String state);
    long countByBeneficiaryStateAndStatus(String state, String status);
}
