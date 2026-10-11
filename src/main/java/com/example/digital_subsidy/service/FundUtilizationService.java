package com.example.digital_subsidy.service;

import com.example.digital_subsidy.dto.DistrictFundUtilizationDto;
import com.example.digital_subsidy.dto.FundUtilizationSummaryDto;
import com.example.digital_subsidy.dto.SchemeFundUtilizationDto;
import com.example.digital_subsidy.dto.StateFundUtilizationDto;
import com.example.digital_subsidy.exception.ResourceNotFoundException;
import com.example.digital_subsidy.model.*;
import com.example.digital_subsidy.repository.*;
import com.example.digital_subsidy.util.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FundUtilizationService {

    private static final Logger log = LoggerFactory.getLogger(FundUtilizationService.class);

    private final SchemeRepository schemeRepository;
    private final ApplicationRepository applicationRepository;
    private final DisbursementReleaseRepository releaseRepository;
    private final FundUtilizationRepository utilizationRepository;
    private final RegionRepository regionRepository;
    private final AuditLogger auditLogger;

    public FundUtilizationService(SchemeRepository schemeRepository,
                                  ApplicationRepository applicationRepository,
                                  DisbursementReleaseRepository releaseRepository,
                                  FundUtilizationRepository utilizationRepository,
                                  RegionRepository regionRepository,
                                  AuditLogger auditLogger) {
        this.schemeRepository = schemeRepository;
        this.applicationRepository = applicationRepository;
        this.releaseRepository = releaseRepository;
        this.utilizationRepository = utilizationRepository;
        this.regionRepository = regionRepository;
        this.auditLogger = auditLogger;
    }

    @Transactional(readOnly = true)
    public FundUtilizationSummaryDto getSystemFundUtilizationSummary(String categoryFilter, String stateFilter, String districtFilter) {
        List<Scheme> schemes = schemeRepository.findAll();
        if (categoryFilter != null && !categoryFilter.isBlank()) {
            schemes = schemes.stream()
                    .filter(s -> categoryFilter.equalsIgnoreCase(s.getCategory()))
                    .collect(Collectors.toList());
        }

        BigDecimal totalAllocated = BigDecimal.ZERO;
        for (Scheme s : schemes) {
            if (s.getTotalBudget() != null) {
                totalAllocated = totalAllocated.add(s.getTotalBudget());
            }
        }

        List<Application> apps = applicationRepository.findAll();
        if (stateFilter != null && !stateFilter.isBlank()) {
            apps = apps.stream()
                    .filter(a -> a.getBeneficiary() != null && stateFilter.equalsIgnoreCase(a.getBeneficiary().getState()))
                    .collect(Collectors.toList());
        }
        if (districtFilter != null && !districtFilter.isBlank()) {
            apps = apps.stream()
                    .filter(a -> a.getBeneficiary() != null && districtFilter.equalsIgnoreCase(a.getBeneficiary().getDistrict()))
                    .collect(Collectors.toList());
        }
        if (categoryFilter != null && !categoryFilter.isBlank()) {
            apps = apps.stream()
                    .filter(a -> a.getScheme() != null && categoryFilter.equalsIgnoreCase(a.getScheme().getCategory()))
                    .collect(Collectors.toList());
        }

        BigDecimal totalSanctioned = BigDecimal.ZERO;
        for (Application a : apps) {
            if ("APPROVED".equalsIgnoreCase(a.getStatus()) || "COMPLETED".equalsIgnoreCase(a.getCurrentStage()) || "DISBURSEMENT".equalsIgnoreCase(a.getCurrentStage())) {
                if (a.getSanctionedAmount() != null) {
                    totalSanctioned = totalSanctioned.add(a.getSanctionedAmount());
                }
            }
        }

        List<DisbursementRelease> releases = releaseRepository.findAll();
        releases = releases.stream()
                .filter(r -> "SUCCESS".equalsIgnoreCase(r.getStatus()))
                .collect(Collectors.toList());

        if (stateFilter != null && !stateFilter.isBlank()) {
            releases = releases.stream()
                    .filter(r -> r.getApplication() != null && r.getApplication().getBeneficiary() != null &&
                            stateFilter.equalsIgnoreCase(r.getApplication().getBeneficiary().getState()))
                    .collect(Collectors.toList());
        }
        if (districtFilter != null && !districtFilter.isBlank()) {
            releases = releases.stream()
                    .filter(r -> r.getApplication() != null && r.getApplication().getBeneficiary() != null &&
                            districtFilter.equalsIgnoreCase(r.getApplication().getBeneficiary().getDistrict()))
                    .collect(Collectors.toList());
        }
        if (categoryFilter != null && !categoryFilter.isBlank()) {
            releases = releases.stream()
                    .filter(r -> r.getApplication() != null && r.getApplication().getScheme() != null &&
                            categoryFilter.equalsIgnoreCase(r.getApplication().getScheme().getCategory()))
                    .collect(Collectors.toList());
        }

        BigDecimal totalReleased = BigDecimal.ZERO;
        for (DisbursementRelease r : releases) {
            if (r.getReleasedAmount() != null) {
                totalReleased = totalReleased.add(r.getReleasedAmount());
            }
        }

        BigDecimal totalUtilized = BigDecimal.ZERO;
        List<FundUtilization> utilizations = utilizationRepository.findAll();
        if (stateFilter != null && !stateFilter.isBlank()) {
            utilizations = utilizations.stream()
                    .filter(u -> stateFilter.equalsIgnoreCase(u.getState()))
                    .collect(Collectors.toList());
        }
        if (districtFilter != null && !districtFilter.isBlank()) {
            utilizations = utilizations.stream()
                    .filter(u -> districtFilter.equalsIgnoreCase(u.getDistrict()))
                    .collect(Collectors.toList());
        }
        if (categoryFilter != null && !categoryFilter.isBlank()) {
            utilizations = utilizations.stream()
                    .filter(u -> u.getScheme() != null && categoryFilter.equalsIgnoreCase(u.getScheme().getCategory()))
                    .collect(Collectors.toList());
        }

        for (FundUtilization u : utilizations) {
            totalUtilized = totalUtilized.add(u.getUtilizedAmount());
        }

        // If no explicit utilization records yet exist for released funds, default to total released
        if (utilizations.isEmpty() && totalReleased.compareTo(BigDecimal.ZERO) > 0) {
            totalUtilized = totalReleased;
        }

        BigDecimal remainingAllocation = totalAllocated.subtract(totalReleased);
        if (remainingAllocation.compareTo(BigDecimal.ZERO) < 0) {
            remainingAllocation = BigDecimal.ZERO;
        }

        BigDecimal unutilizedReleased = totalReleased.subtract(totalUtilized);
        if (unutilizedReleased.compareTo(BigDecimal.ZERO) < 0) {
            unutilizedReleased = BigDecimal.ZERO;
        }

        double pct = 0.0;
        if (totalAllocated != null && totalAllocated.compareTo(BigDecimal.ZERO) > 0) {
            pct = totalUtilized.multiply(new BigDecimal("100"))
                    .divide(totalAllocated, 4, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        return new FundUtilizationSummaryDto(
                totalAllocated, totalSanctioned, totalReleased, totalUtilized,
                remainingAllocation, unutilizedReleased, pct
        );
    }

    @Transactional(readOnly = true)
    public List<SchemeFundUtilizationDto> getSchemeWiseFundUtilization(String categoryFilter, String stateFilter) {
        List<Scheme> schemes = schemeRepository.findAll();
        if (categoryFilter != null && !categoryFilter.isBlank()) {
            schemes = schemes.stream()
                    .filter(s -> categoryFilter.equalsIgnoreCase(s.getCategory()))
                    .collect(Collectors.toList());
        }

        List<SchemeFundUtilizationDto> result = new ArrayList<>();
        for (Scheme s : schemes) {
            SchemeFundUtilizationDto dto = new SchemeFundUtilizationDto();
            dto.setSchemeId(s.getId());
            dto.setSchemeName(s.getName());
            dto.setCategory(s.getCategory());

            BigDecimal allocated = s.getTotalBudget() != null ? s.getTotalBudget() : BigDecimal.ZERO;
            dto.setAllocatedAmount(allocated);

            List<Application> apps = applicationRepository.findBySchemeId(s.getId());
            if (stateFilter != null && !stateFilter.isBlank()) {
                apps = apps.stream()
                        .filter(a -> a.getBeneficiary() != null && stateFilter.equalsIgnoreCase(a.getBeneficiary().getState()))
                        .collect(Collectors.toList());
            }
            dto.setApplicationCount(apps.size());

            BigDecimal sanctioned = BigDecimal.ZERO;
            for (Application a : apps) {
                if ("APPROVED".equalsIgnoreCase(a.getStatus()) || "COMPLETED".equalsIgnoreCase(a.getCurrentStage()) || "DISBURSEMENT".equalsIgnoreCase(a.getCurrentStage())) {
                    if (a.getSanctionedAmount() != null) {
                        sanctioned = sanctioned.add(a.getSanctionedAmount());
                    }
                }
            }
            dto.setSanctionedAmount(sanctioned);

            List<DisbursementRelease> releases = releaseRepository.findAll().stream()
                    .filter(r -> r.getApplication() != null && s.getId().equals(r.getApplication().getScheme().getId()))
                    .filter(r -> "SUCCESS".equalsIgnoreCase(r.getStatus()))
                    .collect(Collectors.toList());

            if (stateFilter != null && !stateFilter.isBlank()) {
                releases = releases.stream()
                        .filter(r -> r.getApplication().getBeneficiary() != null && stateFilter.equalsIgnoreCase(r.getApplication().getBeneficiary().getState()))
                        .collect(Collectors.toList());
            }

            BigDecimal released = BigDecimal.ZERO;
            for (DisbursementRelease r : releases) {
                if (r.getReleasedAmount() != null) {
                    released = released.add(r.getReleasedAmount());
                }
            }
            dto.setReleasedAmount(released);

            BigDecimal utilized = utilizationRepository.sumTotalUtilizedAmountBySchemeId(s.getId());
            if (utilized == null || (utilized.compareTo(BigDecimal.ZERO) == 0 && released.compareTo(BigDecimal.ZERO) > 0)) {
                utilized = released;
            }
            dto.setUtilizedAmount(utilized);

            BigDecimal remaining = s.getRemainingBudget() != null ? s.getRemainingBudget() : allocated.subtract(released);
            if (remaining.compareTo(BigDecimal.ZERO) < 0) remaining = BigDecimal.ZERO;
            dto.setRemainingAmount(remaining);

            double schemePct = 0.0;
            if (allocated.compareTo(BigDecimal.ZERO) > 0) {
                schemePct = utilized.multiply(new BigDecimal("100"))
                        .divide(allocated, 4, RoundingMode.HALF_UP)
                        .doubleValue();
            }
            dto.setUtilizationPercentage(schemePct);
            dto.setLowBudgetWarning(schemePct >= 80.0);

            result.add(dto);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<StateFundUtilizationDto> getStateWiseFundUtilization() {
        List<String> states = regionRepository.findDistinctStateNames();
        if (states == null || states.isEmpty()) {
            states = List.of("TELANGANA", "ANDHRA PRADESH", "MAHARASHTRA", "KARNATAKA", "TAMIL NADU");
        }

        List<StateFundUtilizationDto> list = new ArrayList<>();
        for (String stateName : states) {
            StateFundUtilizationDto dto = new StateFundUtilizationDto();
            dto.setState(stateName);

            BigDecimal sanctioned = applicationRepository.sumTotalApprovedGrantAmountByState(stateName);
            if (sanctioned == null) sanctioned = BigDecimal.ZERO;

            BigDecimal released = releaseRepository.findAll().stream()
                    .filter(r -> "SUCCESS".equalsIgnoreCase(r.getStatus()))
                    .filter(r -> r.getApplication() != null && r.getApplication().getBeneficiary() != null &&
                            stateName.equalsIgnoreCase(r.getApplication().getBeneficiary().getState()))
                    .map(DisbursementRelease::getReleasedAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            dto.setReleasedAmount(released);

            if (sanctioned.compareTo(released) < 0) {
                sanctioned = released;
            }
            dto.setSanctionedAmount(sanctioned);

            BigDecimal utilized = utilizationRepository.sumTotalUtilizedAmountByState(stateName);
            if (utilized == null || (utilized.compareTo(BigDecimal.ZERO) == 0 && released.compareTo(BigDecimal.ZERO) > 0)) {
                utilized = released;
            }
            dto.setUtilizedAmount(utilized);

            List<Application> apps = applicationRepository.findAll().stream()
                    .filter(a -> a.getBeneficiary() != null && stateName.equalsIgnoreCase(a.getBeneficiary().getState()))
                    .collect(Collectors.toList());
            dto.setApplicationCount(apps.size());

            BigDecimal remaining = sanctioned.subtract(released);
            if (remaining.compareTo(BigDecimal.ZERO) < 0) remaining = BigDecimal.ZERO;
            dto.setRemainingAmount(remaining);

            double statePct = 0.0;
            if (sanctioned.compareTo(BigDecimal.ZERO) > 0) {
                statePct = utilized.multiply(new BigDecimal("100"))
                        .divide(sanctioned, 4, RoundingMode.HALF_UP)
                        .doubleValue();
            }
            dto.setUtilizationPercentage(statePct);

            list.add(dto);
        }
        return list;
    }

    @Transactional(readOnly = true)
    public List<DistrictFundUtilizationDto> getDistrictWiseFundUtilization(String stateFilter) {
        List<Application> apps = applicationRepository.findAll();
        if (stateFilter != null && !stateFilter.isBlank()) {
            apps = apps.stream()
                    .filter(a -> a.getBeneficiary() != null && stateFilter.equalsIgnoreCase(a.getBeneficiary().getState()))
                    .collect(Collectors.toList());
        }

        Map<String, List<Application>> groupedByDistrict = apps.stream()
                .filter(a -> a.getBeneficiary() != null && a.getBeneficiary().getDistrict() != null)
                .collect(Collectors.groupingBy(a -> a.getBeneficiary().getDistrict()));

        List<DistrictFundUtilizationDto> list = new ArrayList<>();
        for (Map.Entry<String, List<Application>> entry : groupedByDistrict.entrySet()) {
            String districtName = entry.getKey();
            List<Application> districtApps = entry.getValue();

            DistrictFundUtilizationDto dto = new DistrictFundUtilizationDto();
            dto.setDistrict(districtName);
            dto.setState(districtApps.get(0).getBeneficiary().getState());
            dto.setApplicationCount(districtApps.size());

            BigDecimal sanctioned = BigDecimal.ZERO;
            for (Application a : districtApps) {
                if ("APPROVED".equalsIgnoreCase(a.getStatus()) || "COMPLETED".equalsIgnoreCase(a.getCurrentStage()) || "DISBURSEMENT".equalsIgnoreCase(a.getCurrentStage())) {
                    if (a.getSanctionedAmount() != null) {
                        sanctioned = sanctioned.add(a.getSanctionedAmount());
                    }
                }
            }

            BigDecimal released = releaseRepository.findAll().stream()
                    .filter(r -> "SUCCESS".equalsIgnoreCase(r.getStatus()))
                    .filter(r -> r.getApplication() != null && r.getApplication().getBeneficiary() != null &&
                            districtName.equalsIgnoreCase(r.getApplication().getBeneficiary().getDistrict()))
                    .map(DisbursementRelease::getReleasedAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            dto.setReleasedAmount(released);

            if (sanctioned.compareTo(released) < 0) {
                sanctioned = released;
            }
            dto.setSanctionedAmount(sanctioned);

            BigDecimal utilized = utilizationRepository.sumTotalUtilizedAmountByDistrict(districtName);
            if (utilized == null || (utilized.compareTo(BigDecimal.ZERO) == 0 && released.compareTo(BigDecimal.ZERO) > 0)) {
                utilized = released;
            }
            dto.setUtilizedAmount(utilized);

            BigDecimal remaining = sanctioned.subtract(released);
            if (remaining.compareTo(BigDecimal.ZERO) < 0) remaining = BigDecimal.ZERO;
            dto.setRemainingAmount(remaining);

            double districtPct = 0.0;
            if (sanctioned.compareTo(BigDecimal.ZERO) > 0) {
                districtPct = utilized.multiply(new BigDecimal("100"))
                        .divide(sanctioned, 4, RoundingMode.HALF_UP)
                        .doubleValue();
            }
            dto.setUtilizationPercentage(districtPct);

            list.add(dto);
        }
        return list;
    }

    @Transactional
    public FundUtilization ensureUtilizationForRelease(DisbursementRelease release) {
        if (release == null || release.getId() == null) {
            return null;
        }

        FundUtilization existing = utilizationRepository.findByRelease(release).orElse(null);
        if (existing != null) {
            return existing;
        }

        FundUtilization fu = new FundUtilization();
        fu.setRelease(release);
        fu.setDisbursement(release.getDisbursement());
        fu.setMilestone(release.getMilestone());
        fu.setApplication(release.getApplication());
        fu.setScheme(release.getApplication() != null ? release.getApplication().getScheme() : null);

        if (release.getApplication() != null && release.getApplication().getBeneficiary() != null) {
            fu.setState(release.getApplication().getBeneficiary().getState());
            fu.setDistrict(release.getApplication().getBeneficiary().getDistrict());
        }

        fu.setSanctionedAmount(release.getApplication() != null ? release.getApplication().getSanctionedAmount() : BigDecimal.ZERO);
        fu.setReleasedAmount(release.getReleasedAmount() != null ? release.getReleasedAmount() : BigDecimal.ZERO);
        fu.setUtilizedAmount(release.getReleasedAmount() != null ? release.getReleasedAmount() : BigDecimal.ZERO);
        fu.setUtilizationStatus("FULLY_UTILIZED");
        fu.setUtilizationDate(LocalDateTime.now());
        fu.setSubmittedBy(release.getReleasedBy());

        FundUtilization saved = utilizationRepository.save(fu);
        auditLogger.log("FUND_UTILIZATION_RECORDED", "FundUtilization", saved.getId(),
                "Auto-recorded utilization for release ID: " + release.getId() + " amount: " + release.getReleasedAmount());
        return saved;
    }

    @Transactional
    public FundUtilization recordFundUtilization(Long releaseId, BigDecimal utilizedAmount, String remarks, String username) {
        if (releaseId == null) {
            throw new IllegalArgumentException("Release ID is required for recording utilization.");
        }
        if (utilizedAmount == null || utilizedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Utilized amount cannot be negative or null.");
        }

        DisbursementRelease release = releaseRepository.findById(releaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Disbursement release not found with ID: " + releaseId));

        if (utilizedAmount.compareTo(release.getReleasedAmount()) > 0) {
            throw new IllegalArgumentException("Utilized amount (" + utilizedAmount + ") cannot exceed released amount (" + release.getReleasedAmount() + ").");
        }

        FundUtilization fu = utilizationRepository.findByRelease(release).orElse(null);
        if (fu == null) {
            fu = new FundUtilization();
            fu.setRelease(release);
            fu.setDisbursement(release.getDisbursement());
            fu.setMilestone(release.getMilestone());
            fu.setApplication(release.getApplication());
            fu.setScheme(release.getApplication() != null ? release.getApplication().getScheme() : null);
            if (release.getApplication() != null && release.getApplication().getBeneficiary() != null) {
                fu.setState(release.getApplication().getBeneficiary().getState());
                fu.setDistrict(release.getApplication().getBeneficiary().getDistrict());
            }
            fu.setSanctionedAmount(release.getApplication() != null ? release.getApplication().getSanctionedAmount() : BigDecimal.ZERO);
            fu.setReleasedAmount(release.getReleasedAmount());
        }

        fu.setUtilizedAmount(utilizedAmount);
        if (utilizedAmount.compareTo(release.getReleasedAmount()) >= 0) {
            fu.setUtilizationStatus("FULLY_UTILIZED");
        } else if (utilizedAmount.compareTo(BigDecimal.ZERO) > 0) {
            fu.setUtilizationStatus("PARTIALLY_UTILIZED");
        } else {
            fu.setUtilizationStatus("PENDING");
        }

        fu.setUtilizationDate(LocalDateTime.now());
        fu.setSubmittedBy(username);
        fu.setRemarks(remarks);

        FundUtilization saved = utilizationRepository.save(fu);
        auditLogger.log("FUND_UTILIZATION_UPDATED", "FundUtilization", saved.getId(),
                "Updated fund utilization for release ID: " + releaseId + " to: " + utilizedAmount);
        return saved;
    }
}
