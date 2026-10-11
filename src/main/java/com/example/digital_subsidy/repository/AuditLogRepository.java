package com.example.digital_subsidy.repository;

import com.example.digital_subsidy.model.AuditLog;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findTop50ByOrderByTimestampDesc();

    @Query("SELECT a FROM AuditLog a WHERE a.userRole IS NULL OR (UPPER(a.userRole) NOT LIKE '%BENEFICIARY%' AND UPPER(a.userRole) != 'BENEFICIARY') ORDER BY a.timestamp DESC")
    List<AuditLog> findOperationalAuditLogs(Pageable pageable);

    default List<AuditLog> findTop50OperationalAuditLogs() {
        return findOperationalAuditLogs(PageRequest.of(0, 50));
    }
}

