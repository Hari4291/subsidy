package com.example.digital_subsidy.repository;

import com.example.digital_subsidy.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByBeneficiaryId(Long beneficiaryId);
    List<Document> findByApplicationId(Long applicationId);
    List<Document> findByBeneficiaryIdAndDocumentType(Long beneficiaryId, String documentType);
    List<Document> findByMilestoneIdOrderByIdDesc(Long milestoneId);
    List<Document> findByApplicationIdAndMilestoneId(Long applicationId, Long milestoneId);
    List<Document> findByApplicationIdAndMilestoneIdAndStatus(Long applicationId, Long milestoneId, String status);
}
