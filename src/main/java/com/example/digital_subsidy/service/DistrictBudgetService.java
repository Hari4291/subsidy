package com.example.digital_subsidy.service;

import com.example.digital_subsidy.model.Scheme;
import com.example.digital_subsidy.repository.ApplicationRepository;
import com.example.digital_subsidy.repository.DisbursementRepository;
import com.example.digital_subsidy.repository.SchemeRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DistrictBudgetService {

    private final SchemeRepository schemeRepository;
    private final ApplicationRepository applicationRepository;
    private final DisbursementRepository disbursementRepository;

    public DistrictBudgetService(SchemeRepository schemeRepository,
                                 ApplicationRepository applicationRepository,
                                 DisbursementRepository disbursementRepository) {
        this.schemeRepository = schemeRepository;
        this.applicationRepository = applicationRepository;
        this.disbursementRepository = disbursementRepository;
    }

    public Map<String, Object> getDistrictBudgetSummary(String state) {
        List<Scheme> activeSchemes = schemeRepository.findByStatus("ACTIVE");

        BigDecimal totalAllocated = BigDecimal.ZERO;
        BigDecimal totalRemaining = BigDecimal.ZERO;

        for (Scheme s : activeSchemes) {
            if (s.getTotalBudget() != null) totalAllocated = totalAllocated.add(s.getTotalBudget());
            if (s.getRemainingBudget() != null) totalRemaining = totalRemaining.add(s.getRemainingBudget());
        }

        BigDecimal approvedAmount = BigDecimal.ZERO;
        BigDecimal pendingAmount = BigDecimal.ZERO;
        BigDecimal disbursedAmount = BigDecimal.ZERO;

        if (state != null && !state.isBlank()) {
            BigDecimal appr = applicationRepository.sumTotalApprovedGrantAmountByState(state);
            if (appr != null) approvedAmount = appr;

            BigDecimal pend = applicationRepository.sumTotalPendingGrantAmountByState(state);
            if (pend != null) pendingAmount = pend;

            BigDecimal disb = disbursementRepository.sumTotalDisbursedAmountByState(state);
            if (disb != null) disbursedAmount = disb;
        } else {
            BigDecimal appr = applicationRepository.sumTotalApprovedGrantAmount();
            if (appr != null) approvedAmount = appr;

            BigDecimal disb = disbursementRepository.sumTotalDisbursedAmount();
            if (disb != null) disbursedAmount = disb;
        }

        boolean lowBudgetWarning = false;
        if (totalAllocated.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ratio = totalRemaining.divide(totalAllocated, 2, java.math.RoundingMode.HALF_UP);
            if (ratio.compareTo(new BigDecimal("0.20")) < 0) {
                lowBudgetWarning = true;
            }
        }

        List<Map<String, Object>> schemeUtilizationList = new ArrayList<>();
        for (Scheme s : activeSchemes) {
            Map<String, Object> item = new HashMap<>();
            item.put("schemeName", s.getName());
            item.put("category", s.getCategory());
            item.put("totalBudget", s.getTotalBudget() != null ? s.getTotalBudget() : BigDecimal.ZERO);
            item.put("remainingBudget", s.getRemainingBudget() != null ? s.getRemainingBudget() : BigDecimal.ZERO);

            BigDecimal used = (s.getTotalBudget() != null && s.getRemainingBudget() != null)
                    ? s.getTotalBudget().subtract(s.getRemainingBudget()) : BigDecimal.ZERO;
            item.put("utilizedBudget", used);

            int pct = 0;
            if (s.getTotalBudget() != null && s.getTotalBudget().compareTo(BigDecimal.ZERO) > 0) {
                pct = used.multiply(new BigDecimal("100")).divide(s.getTotalBudget(), 0, java.math.RoundingMode.HALF_UP).intValue();
            }
            item.put("utilizationPercentage", pct);
            item.put("lowWarning", pct >= 80);
            schemeUtilizationList.add(item);
        }

        int overallPct = 0;
        if (totalAllocated.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal used = totalAllocated.subtract(totalRemaining);
            overallPct = used.multiply(new BigDecimal("100")).divide(totalAllocated, 0, java.math.RoundingMode.HALF_UP).intValue();
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("state", state != null ? state : "ALL STATES");
        summary.put("totalAllocatedBudget", totalAllocated);
        summary.put("allocatedBudget", totalAllocated);
        summary.put("totalRemainingBudget", totalRemaining);
        summary.put("remainingBudget", totalRemaining);
        summary.put("approvedGrantAmount", approvedAmount);
        summary.put("approvedAmount", approvedAmount);
        summary.put("pendingGrantAmount", pendingAmount);
        summary.put("pendingAmount", pendingAmount);
        summary.put("disbursedGrantAmount", disbursedAmount);
        summary.put("disbursedAmount", disbursedAmount);
        summary.put("utilizationPercentage", overallPct);
        summary.put("lowBudgetWarning", lowBudgetWarning);
        summary.put("schemeUtilizations", schemeUtilizationList);

        return summary;
    }
}
