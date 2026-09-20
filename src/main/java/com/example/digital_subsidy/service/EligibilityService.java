package com.example.digital_subsidy.service;

import com.example.digital_subsidy.dto.EligibilityCheckRequestDto;
import com.example.digital_subsidy.dto.EligibilityCheckResponseDto;
import com.example.digital_subsidy.exception.ResourceNotFoundException;
import com.example.digital_subsidy.model.Beneficiary;
import com.example.digital_subsidy.model.EligibilityCriteria;
import com.example.digital_subsidy.model.Scheme;
import com.example.digital_subsidy.repository.EligibilityCriteriaRepository;
import com.example.digital_subsidy.repository.SchemeRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class EligibilityService {

    private final SchemeRepository schemeRepository;
    private final EligibilityCriteriaRepository criteriaRepository;

    public EligibilityService(SchemeRepository schemeRepository, EligibilityCriteriaRepository criteriaRepository) {
        this.schemeRepository = schemeRepository;
        this.criteriaRepository = criteriaRepository;
    }

    public EligibilityCheckResponseDto evaluateEligibility(Beneficiary beneficiary, EligibilityCheckRequestDto request) {
        Scheme scheme = schemeRepository.findById(request.getSchemeId())
                .orElseThrow(() -> new ResourceNotFoundException("Scheme not found with ID: " + request.getSchemeId()));

        List<EligibilityCriteria> criteriaList = criteriaRepository.findByScheme(scheme);

        EligibilityCheckResponseDto response = new EligibilityCheckResponseDto();
        response.setSchemeId(scheme.getId());
        response.setSchemeName(scheme.getName());

        int totalEarnedPoints = 0;
        int maxPossiblePoints = 0;
        boolean allMandatoryPassed = true;

        for (EligibilityCriteria criteria : criteriaList) {
            boolean passed = evaluateCriterion(beneficiary, request, criteria);
            int weight = criteria.getWeightPoints() != null ? criteria.getWeightPoints() : 10;
            int earned = passed ? weight : 0;

            maxPossiblePoints += weight;
            totalEarnedPoints += earned;

            if (!passed) {
                // Hard mandatory core prerequisites that cause immediate disqualification
                String cName = criteria.getCriterionName() != null ? criteria.getCriterionName().toUpperCase() : "";
                if ("MAX_INCOME".equalsIgnoreCase(cName) || 
                    "MIN_AGE".equalsIgnoreCase(cName) || 
                    "MAX_AGE".equalsIgnoreCase(cName) || 
                    "STATE_CHECK".equalsIgnoreCase(cName)) {
                    allMandatoryPassed = false;
                }
            }

            EligibilityCheckResponseDto.CriterionResultDto resultDto = new EligibilityCheckResponseDto.CriterionResultDto(
                    criteria.getCriterionName(),
                    criteria.getDescription() != null ? criteria.getDescription() : criteria.getCriterionName(),
                    passed,
                    weight,
                    earned
            );
            response.getCriterionResults().add(resultDto);
        }

        // Scheme-specific evaluation logic: score percentage >= scheme.getMinQualifyingScore() and mandatory criteria passed
        int scorePercentage = maxPossiblePoints > 0 ? (totalEarnedPoints * 100) / maxPossiblePoints : 100;
        response.setTotalScore(scorePercentage);

        int minQualifyingScore = scheme.getMinQualifyingScore() != null ? scheme.getMinQualifyingScore() : 60;
        boolean isEligible = allMandatoryPassed && (scorePercentage >= minQualifyingScore);
        response.setEligible(isEligible);

        if (isEligible) {
            response.setStatusMessage("Congratulations! You satisfy the eligibility score requirement (" + scorePercentage + "% >= " + minQualifyingScore + "%) for " + scheme.getName() + ".");
        } else if (!allMandatoryPassed) {
            response.setStatusMessage("Based on system evaluation, your application does not meet the mandatory core prerequisites (e.g. Annual Income Ceiling) for " + scheme.getName() + ".");
        } else {
            response.setStatusMessage("Based on system evaluation, your calculated score (" + scorePercentage + "%) does not meet the required minimum score (" + minQualifyingScore + "%) for " + scheme.getName() + ".");
        }

        return response;
    }

    private boolean evaluateCriterion(Beneficiary beneficiary, EligibilityCheckRequestDto request, EligibilityCriteria criteria) {
        String name = criteria.getCriterionName() != null ? criteria.getCriterionName().toUpperCase() : "";
        String target = criteria.getTargetValue() != null ? criteria.getTargetValue() : "";
        String op = criteria.getOperator() != null ? criteria.getOperator().toUpperCase() : "EQUALS";
        String type = criteria.getAnswerType() != null ? criteria.getAnswerType().toUpperCase() : 
                     (criteria.getCriterionType() != null ? criteria.getCriterionType().toUpperCase() : "TEXT");

        // Fetch user submitted answer for this specific criterion
        String userAns = null;
        if (request.getCriterionAnswers() != null && criteria.getId() != null) {
            userAns = request.getCriterionAnswers().get(criteria.getId());
        }
        if ((userAns == null || userAns.trim().isEmpty()) && request.getAnswers() != null) {
            userAns = request.getAnswers().get(criteria.getCriterionName());
        }

        // If user submitted an explicit answer for this dynamic criterion
        if (userAns != null && !userAns.trim().isEmpty()) {
            userAns = userAns.trim();

            if ("BOOLEAN".equals(type) || "YES_NO".equals(type) || "IS_TRUE".equals(op)) {
                boolean isUserYes = "YES".equalsIgnoreCase(userAns) || "TRUE".equalsIgnoreCase(userAns) || "1".equals(userAns);
                boolean isTargetYes = "YES".equalsIgnoreCase(target) || "TRUE".equalsIgnoreCase(target) || "REQUIRED".equalsIgnoreCase(target) || "ACTIVE_FARMER".equalsIgnoreCase(target);
                if ("NOT_EQUALS".equals(op)) return isUserYes != isTargetYes;
                return isUserYes == isTargetYes;
            }

            // Numeric comparisons
            if ("NUMERIC".equals(type) || "NUMBER".equals(type) || "DECIMAL".equals(type) ||
                "GREATER_THAN".equals(op) || "GREATER_THAN_OR_EQUAL".equals(op) || "LESS_THAN".equals(op) ||
                "LESS_THAN_OR_EQUAL".equals(op) || "BETWEEN".equals(op) || "GTE".equals(op) || "LTE".equals(op)) {
                try {
                    BigDecimal numUser = new BigDecimal(userAns);
                    if ("BETWEEN".equals(op)) {
                        String[] parts = target.split("[,-]");
                        if (parts.length >= 2) {
                            BigDecimal min = new BigDecimal(parts[0].trim());
                            BigDecimal max = new BigDecimal(parts[1].trim());
                            return numUser.compareTo(min) >= 0 && numUser.compareTo(max) <= 0;
                        }
                    }
                    BigDecimal numTarget = new BigDecimal(target);
                    if ("GREATER_THAN".equals(op) || "GT".equals(op)) {
                        return numUser.compareTo(numTarget) > 0;
                    } else if ("GREATER_THAN_OR_EQUAL".equals(op) || "GTE".equals(op)) {
                        return numUser.compareTo(numTarget) >= 0;
                    } else if ("LESS_THAN".equals(op) || "LT".equals(op)) {
                        return numUser.compareTo(numTarget) < 0;
                    } else if ("LESS_THAN_OR_EQUAL".equals(op) || "LTE".equals(op)) {
                        return numUser.compareTo(numTarget) <= 0;
                    } else if ("NOT_EQUALS".equals(op)) {
                        return numUser.compareTo(numTarget) != 0;
                    } else {
                        return numUser.compareTo(numTarget) == 0;
                    }
                } catch (Exception e) {
                    // Ignore parse error and fall back to categorical check below
                }
            }

            // String / Selection / Choice comparisons
            if ("IN".equals(op)) {
                if (target.isEmpty() || "ALL".equalsIgnoreCase(target)) return true;
                String[] allowedValues = target.split(",");
                for (String val : allowedValues) {
                    if (val.trim().equalsIgnoreCase(userAns)) return true;
                }
                return false;
            } else if ("CONTAINS".equals(op)) {
                return userAns.toLowerCase().contains(target.toLowerCase()) || target.toLowerCase().contains(userAns.toLowerCase());
            } else if ("NOT_EQUALS".equals(op)) {
                return !userAns.equalsIgnoreCase(target);
            } else {
                // EQUALS
                if (target.isEmpty() || "ALL".equalsIgnoreCase(target)) return true;
                String[] allowedValues = target.split(",");
                for (String val : allowedValues) {
                    if (val.trim().equalsIgnoreCase(userAns)) return true;
                }
                return false;
            }
        }

        // Fallback evaluation for profile-fetched criteria
        switch (name) {
            case "MIN_AGE":
                try {
                    int minAge = Integer.parseInt(target);
                    return beneficiary.getAge() >= minAge;
                } catch (NumberFormatException e) {
                    return true;
                }

            case "MAX_AGE":
                try {
                    int maxAge = Integer.parseInt(target);
                    return beneficiary.getAge() <= maxAge;
                } catch (NumberFormatException e) {
                    return true;
                }

            case "MAX_INCOME":
                if (request.getAnnualIncome() != null) {
                    try {
                        BigDecimal maxInc = new BigDecimal(target);
                        return request.getAnnualIncome().compareTo(maxInc) <= 0;
                    } catch (Exception e) {
                        return true;
                    }
                }
                return true;

            case "CATEGORY_CHECK":
                if (target.isEmpty() || target.equalsIgnoreCase("ALL")) return true;
                return target.toUpperCase().contains(beneficiary.getCategory().toUpperCase());

            case "STATE_CHECK":
                if (target.isEmpty() || target.equalsIgnoreCase("ALL")) return true;
                return target.toUpperCase().contains(beneficiary.getState().toUpperCase());

            case "IDENTITY_VERIFIED":
                return "VERIFIED".equalsIgnoreCase(beneficiary.getIdentityStatus()) || 
                       "PENDING".equalsIgnoreCase(beneficiary.getIdentityStatus());

            case "FARMER_STATUS":
                return request.getFarmerStatus() != null && (request.getFarmerStatus().equalsIgnoreCase(target) || "ACTIVE_FARMER".equalsIgnoreCase(request.getFarmerStatus()));

            case "LAND_HOLDING":
                if (request.getLandHoldingAcres() != null) {
                    try {
                        BigDecimal minLand = new BigDecimal(target);
                        return request.getLandHoldingAcres().compareTo(minLand) >= 0;
                    } catch (Exception e) {
                        return true;
                    }
                }
                return true;

            case "ROOFTOP_OWNERSHIP":
                return request.isRooftopOwnership();

            case "BUSINESS_TYPE":
                if (request.getBusinessType() == null) return false;
                if (target.isEmpty() || target.equalsIgnoreCase("ALL")) return true;
                String bType = request.getBusinessType().toUpperCase();
                return target.toUpperCase().contains(bType);

            default:
                return true;
        }
    }
}
