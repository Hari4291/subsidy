package com.example.digital_subsidy.repository;

import com.example.digital_subsidy.model.RequiredDocument;
import com.example.digital_subsidy.model.Scheme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RequiredDocumentRepository extends JpaRepository<RequiredDocument, Long> {
    List<RequiredDocument> findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(Long schemeId);
    List<RequiredDocument> findBySchemeId(Long schemeId);
    List<RequiredDocument> findByScheme(Scheme scheme);

    @org.springframework.transaction.annotation.Transactional
    void deleteByScheme(Scheme scheme);
}
