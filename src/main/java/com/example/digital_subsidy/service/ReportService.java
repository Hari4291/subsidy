package com.example.digital_subsidy.service;

import com.example.digital_subsidy.repository.ApplicationRepository;
import com.example.digital_subsidy.repository.BeneficiaryRepository;
import com.example.digital_subsidy.repository.DisbursementRepository;
import com.example.digital_subsidy.repository.SchemeRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    private final SchemeRepository schemeRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final ApplicationRepository applicationRepository;
    private final DisbursementRepository disbursementRepository;

    public ReportService(SchemeRepository schemeRepository,
                          BeneficiaryRepository beneficiaryRepository,
                          ApplicationRepository applicationRepository,
                          DisbursementRepository disbursementRepository) {
        this.schemeRepository = schemeRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.applicationRepository = applicationRepository;
        this.disbursementRepository = disbursementRepository;
    }

    public Map<String, Object> getSystemStatistics() {
        Map<String, Object> stats = new HashMap<>();

        long activeSchemes = schemeRepository.countByStatus("ACTIVE");
        long registeredBeneficiaries = beneficiaryRepository.count();
        long applicationsProcessed = applicationRepository.count();
        long approvedApplications = applicationRepository.countByStatus("APPROVED");
        long pendingApplications = applicationRepository.countByStatus("PENDING");
        long rejectedApplications = applicationRepository.countByStatus("REJECTED");

        BigDecimal totalApprovedGrant = applicationRepository.sumTotalApprovedGrantAmount();
        BigDecimal totalDisbursed = disbursementRepository.sumTotalDisbursedAmount();

        stats.put("activeSchemes", activeSchemes);
        stats.put("registeredBeneficiaries", registeredBeneficiaries);
        stats.put("applicationsProcessed", applicationsProcessed);
        stats.put("approvedApplications", approvedApplications);
        stats.put("pendingApplications", pendingApplications);
        stats.put("rejectedApplications", rejectedApplications);
        stats.put("totalApprovedGrant", totalApprovedGrant != null ? totalApprovedGrant : BigDecimal.ZERO);
        stats.put("totalDisbursed", totalDisbursed != null ? totalDisbursed : BigDecimal.ZERO);

        return stats;
    }
}
