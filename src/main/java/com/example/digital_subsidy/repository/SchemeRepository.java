package com.example.digital_subsidy.repository;

import com.example.digital_subsidy.model.Scheme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SchemeRepository extends JpaRepository<Scheme, Long> {
    List<Scheme> findByStatus(String status);
    long countByStatus(String status);
}
