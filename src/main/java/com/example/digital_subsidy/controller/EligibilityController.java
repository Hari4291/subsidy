package com.example.digital_subsidy.controller;

import com.example.digital_subsidy.dto.EligibilityCheckRequestDto;
import com.example.digital_subsidy.dto.EligibilityCheckResponseDto;
import com.example.digital_subsidy.model.Beneficiary;
import com.example.digital_subsidy.model.EligibilityCriteria;
import com.example.digital_subsidy.model.RequiredDocument;
import com.example.digital_subsidy.model.Scheme;
import com.example.digital_subsidy.repository.EligibilityCriteriaRepository;
import com.example.digital_subsidy.repository.RequiredDocumentRepository;
import com.example.digital_subsidy.service.BeneficiaryService;
import com.example.digital_subsidy.service.DocumentService;
import com.example.digital_subsidy.service.EligibilityService;
import com.example.digital_subsidy.service.SchemeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.*;

@Controller
public class EligibilityController {

    private static final Logger log = LoggerFactory.getLogger(EligibilityController.class);

    private final SchemeService schemeService;
    private final BeneficiaryService beneficiaryService;
    private final EligibilityService eligibilityService;
    private final EligibilityCriteriaRepository criteriaRepository;
    private final RequiredDocumentRepository requiredDocumentRepository;
    private final DocumentService documentService;
    private final com.example.digital_subsidy.service.ApplicationService applicationService;

    public EligibilityController(SchemeService schemeService,
                                  BeneficiaryService beneficiaryService,
                                  EligibilityService eligibilityService,
                                  EligibilityCriteriaRepository criteriaRepository,
                                  RequiredDocumentRepository requiredDocumentRepository,
                                  DocumentService documentService,
                                  com.example.digital_subsidy.service.ApplicationService applicationService) {
        this.schemeService = schemeService;
        this.beneficiaryService = beneficiaryService;
        this.eligibilityService = eligibilityService;
        this.criteriaRepository = criteriaRepository;
        this.requiredDocumentRepository = requiredDocumentRepository;
        this.documentService = documentService;
        this.applicationService = applicationService;
    }

    @GetMapping("/api/schemes/{schemeId}/criteria")
    @ResponseBody
    public List<EligibilityCriteria> getSchemeCriteria(@PathVariable("schemeId") Long schemeId) {
        return criteriaRepository.findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(schemeId);
    }

    @GetMapping("/api/schemes/{schemeId}/documents")
    @ResponseBody
    public List<RequiredDocument> getSchemeDocuments(@PathVariable("schemeId") Long schemeId) {
        return requiredDocumentRepository.findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(schemeId);
    }

    @GetMapping("/beneficiary/check-eligibility")
    public String showEligibilityCheckForm(@RequestParam(value = "schemeId", required = false) Long schemeId,
                                           Authentication authentication,
                                           Model model) {
        String email = authentication.getName();
        Beneficiary beneficiary = beneficiaryService.getBeneficiaryByEmail(email).orElseThrow();

        List<Scheme> activeSchemes = schemeService.getAllActiveSchemes();
        Scheme selectedScheme = null;

        if (schemeId != null) {
            try {
                selectedScheme = schemeService.getSchemeById(schemeId);
            } catch (Exception e) {
                log.warn("Requested schemeId {} not found", schemeId);
            }
        }

        if (selectedScheme == null && !activeSchemes.isEmpty()) {
            selectedScheme = activeSchemes.get(0);
            schemeId = selectedScheme.getId();
        }

        EligibilityCheckRequestDto requestDto = new EligibilityCheckRequestDto();
        if (schemeId != null) {
            requestDto.setSchemeId(schemeId);
        }

        List<EligibilityCriteria> criteria = new ArrayList<>();
        List<RequiredDocument> requiredDocuments = new ArrayList<>();

        if (schemeId != null) {
            criteria = criteriaRepository.findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(schemeId);
            requiredDocuments = requiredDocumentRepository.findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(schemeId);
        }

        if (schemeId != null && beneficiary != null) {
            var resList = applicationService.getActiveRestrictions(beneficiary.getId(), schemeId);
            var activeRes = resList.stream().filter(com.example.digital_subsidy.model.ApplicationRestriction::isActive).findFirst().orElse(null);
            model.addAttribute("activeRestriction", activeRes);
        }

        model.addAttribute("requestDto", requestDto);
        model.addAttribute("beneficiary", beneficiary);
        model.addAttribute("schemes", activeSchemes);
        model.addAttribute("selectedScheme", selectedScheme);
        model.addAttribute("criteria", criteria);
        model.addAttribute("requiredDocuments", requiredDocuments);

        return "beneficiary/check-eligibility";
    }

    @PostMapping("/beneficiary/check-eligibility")
    public String processEligibilityCheck(@Valid @ModelAttribute("requestDto") EligibilityCheckRequestDto requestDto,
                                          BindingResult bindingResult,
                                          @RequestParam(value = "docAadhaarFile", required = false) MultipartFile docAadhaarFile,
                                          @RequestParam(value = "docBankPassbookFile", required = false) MultipartFile docBankPassbookFile,
                                          HttpServletRequest request,
                                          HttpSession session,
                                          Authentication authentication,
                                          Model model) {
        log.info("=== ELIGIBILITY CHECK REQUEST START ===");
        String email = authentication != null ? authentication.getName() : "";
        Beneficiary beneficiary = beneficiaryService.getBeneficiaryByEmail(email).orElse(null);
        if (beneficiary == null) {
            model.addAttribute("errorMessage", "Beneficiary account not found.");
            return "redirect:/login";
        }
        log.info("Resolved Beneficiary ID: {} for user: {}", beneficiary.getId(), email);

        List<Scheme> activeSchemes = schemeService.getAllActiveSchemes();

        if (requestDto == null || requestDto.getSchemeId() == null || bindingResult.hasFieldErrors("schemeId")) {
            log.warn("Scheme selection missing in request DTO");
            model.addAttribute("errorMessage", "Please select a target grant / subsidy scheme.");
            return populateAndReturnForm(requestDto != null ? requestDto.getSchemeId() : null, requestDto, beneficiary, activeSchemes, model);
        }

        Long schemeId = requestDto.getSchemeId();
        log.info("Target Scheme ID: {}", schemeId);

        Scheme selectedScheme;
        try {
            selectedScheme = schemeService.getSchemeById(schemeId);
        } catch (Exception ex) {
            model.addAttribute("errorMessage", "Invalid scheme selected.");
            return populateAndReturnForm(null, requestDto, beneficiary, activeSchemes, model);
        }

        // Security & Integrity: Load criteria and required documents strictly for the selected scheme
        List<EligibilityCriteria> criteria = criteriaRepository.findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(schemeId);
        List<RequiredDocument> requiredDocuments = requiredDocumentRepository.findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(schemeId);

        // Bind and validate submitted dynamic answers from HttpServletRequest
        if (requestDto.getCriterionAnswers() == null) {
            requestDto.setCriterionAnswers(new HashMap<>());
        }
        Map<Long, String> criteriaErrors = new HashMap<>();
        boolean isFormSubmission = request != null && request.getParameterMap() != null && !request.getParameterMap().isEmpty();

        if (request != null) {
            for (EligibilityCriteria c : criteria) {
                String paramVal = request.getParameter("criterionAnswers[" + c.getId() + "]");
                if (paramVal == null) {
                    paramVal = request.getParameter("criterion_" + c.getId());
                }
                if (paramVal != null) {
                    paramVal = paramVal.trim();
                    requestDto.getCriterionAnswers().put(c.getId(), paramVal);
                }

                if (c.isRequired() && (paramVal == null || paramVal.isBlank())) {
                    boolean hasFallback = false;
                    String cName = c.getCriterionName() != null ? c.getCriterionName().toUpperCase() : "";
                    switch (cName) {
                        case "MAX_INCOME":
                            if (requestDto.getAnnualIncome() != null) hasFallback = true;
                            break;
                        case "FARMER_STATUS":
                            if (requestDto.getFarmerStatus() != null) hasFallback = true;
                            break;
                        case "LAND_HOLDING":
                            if (requestDto.getLandHoldingAcres() != null) hasFallback = true;
                            break;
                        case "PROPERTY_OWNERSHIP":
                        case "ROOFTOP_OWNERSHIP":
                        case "LAND_OWNERSHIP":
                        case "IRRIGATION_REQUIREMENT":
                            hasFallback = true;
                            break;
                        case "BUSINESS_TYPE":
                            if (requestDto.getBusinessType() != null) hasFallback = true;
                            break;
                        case "MIN_AGE":
                        case "MAX_AGE":
                        case "STATE_CHECK":
                        case "CATEGORY_CHECK":
                        case "IDENTITY_VERIFIED":
                        case "REQUIRED_DOCS":
                            hasFallback = true;
                            break;
                    }
                    if (isFormSubmission && !hasFallback) {
                        criteriaErrors.put(c.getId(), c.getQuestionText() + " is required.");
                        continue;
                    }
                }

                if (paramVal != null && !paramVal.isBlank()) {
                    String type = c.getAnswerType() != null ? c.getAnswerType().toUpperCase() :
                                 (c.getCriterionType() != null ? c.getCriterionType().toUpperCase() : "TEXT");
                    if ("NUMBER".equals(type) || "DECIMAL".equals(type) || "NUMERIC".equals(type) || "MAX_INCOME".equalsIgnoreCase(c.getCriterionName())) {
                        try {
                            java.math.BigDecimal numVal = new java.math.BigDecimal(paramVal);
                            if (numVal.compareTo(java.math.BigDecimal.ZERO) < 0) {
                                criteriaErrors.put(c.getId(), c.getQuestionText() + " cannot be negative.");
                            } else if ("MAX_INCOME".equalsIgnoreCase(c.getCriterionName())) {
                                requestDto.setAnnualIncome(numVal);
                            }
                        } catch (Exception e) {
                            criteriaErrors.put(c.getId(), "Please enter a valid numeric value for " + c.getQuestionText());
                        }
                    }
                }
            }
        }

        if (!criteriaErrors.isEmpty()) {
            log.warn("Criteria validation failed for scheme {}: {}", schemeId, criteriaErrors);
            model.addAttribute("criteriaErrors", criteriaErrors);
            model.addAttribute("errorMessage", criteriaErrors.values().iterator().next());
            return populateAndReturnForm(schemeId, requestDto, beneficiary, activeSchemes, model);
        }

        // Validate and Process Dynamic Required Documents
        Map<Long, String> documentErrors = new HashMap<>();
        Map<Long, MultipartFile> fileToSaveMap = new HashMap<>();

        for (RequiredDocument reqDoc : requiredDocuments) {
            MultipartFile file = null;
            if (request instanceof MultipartHttpServletRequest multipartRequest) {
                file = multipartRequest.getFile("docFile_" + reqDoc.getId());
            }
            if (file == null || file.isEmpty()) {
                if ("AADHAAR".equalsIgnoreCase(reqDoc.getDocumentType()) && docAadhaarFile != null && !docAadhaarFile.isEmpty()) {
                    file = docAadhaarFile;
                } else if ("BANK_PASSBOOK".equalsIgnoreCase(reqDoc.getDocumentType()) && docBankPassbookFile != null && !docBankPassbookFile.isEmpty()) {
                    file = docBankPassbookFile;
                }
            }

            if (file == null || file.isEmpty()) {
                if (reqDoc.isRequired()) {
                    documentErrors.put(reqDoc.getId(), reqDoc.getDocumentName() + " is required.");
                }
            } else {
                String err = validateDocumentFile(file, reqDoc);
                if (err != null) {
                    documentErrors.put(reqDoc.getId(), err);
                } else {
                    fileToSaveMap.put(reqDoc.getId(), file);
                }
            }
        }

        if (!documentErrors.isEmpty()) {
            log.warn("Document validation failed for scheme {}: {}", schemeId, documentErrors);
            model.addAttribute("documentErrors", documentErrors);
            model.addAttribute("errorMessage", "Please correct document upload errors and submit again.");
            return populateAndReturnForm(schemeId, requestDto, beneficiary, activeSchemes, model);
        }

        // Save uploaded valid documents (application_id will be linked at submission time)
        for (Map.Entry<Long, MultipartFile> entry : fileToSaveMap.entrySet()) {
            RequiredDocument reqDoc = requiredDocuments.stream()
                    .filter(d -> d.getId().equals(entry.getKey()))
                    .findFirst().orElse(null);
            if (reqDoc != null) {
                try {
                    log.info("Saving document: {} for type: {}", entry.getValue().getOriginalFilename(), reqDoc.getDocumentType());
                    // Pass null for application — ApplicationService.submitApplication() will link it
                    documentService.saveDocument(beneficiary, null, reqDoc.getDocumentType(), entry.getValue());
                } catch (Exception e) {
                    log.error("Failed to save document {}: {}", reqDoc.getDocumentName(), e.getMessage());
                }
            }
        }

        try {
            log.info("Evaluating eligibility score for Scheme ID: {}", schemeId);
            EligibilityCheckResponseDto result = eligibilityService.evaluateEligibility(beneficiary, requestDto);
            log.info("Eligibility score calculated: {}% (Eligible: {})", result.getTotalScore(), result.isEligible());

            // ── Store submitted answers + question snapshots in session ──
            // ApplicationController picks these up at submission time so they can be
            // persisted as ApplicationEligibilityAnswer rows (historical snapshot).
            Map<Long, String> criterionAnswers = requestDto.getCriterionAnswers();
            if (criterionAnswers != null && !criterionAnswers.isEmpty()) {
                // Build question text snapshots keyed by criterion ID
                Map<Long, String> questionSnapshots = new LinkedHashMap<>();
                Map<Long, Integer> displayOrders = new LinkedHashMap<>();
                for (EligibilityCriteria c : criteria) {
                    questionSnapshots.put(c.getId(), c.getQuestionText());
                    displayOrders.put(c.getId(), c.getDisplayOrder() != null ? c.getDisplayOrder() : 0);
                }
                session.setAttribute("pendingCriterionAnswers_" + schemeId, criterionAnswers);
                session.setAttribute("pendingQuestionSnapshots_" + schemeId, questionSnapshots);
                session.setAttribute("pendingDisplayOrders_" + schemeId, displayOrders);
                log.info("Stored {} criterion answers in session for scheme {}", criterionAnswers.size(), schemeId);
            }

            if (schemeId != null && beneficiary != null) {
                var resList = applicationService.getActiveRestrictions(beneficiary.getId(), schemeId);
                var activeRes = resList.stream().filter(com.example.digital_subsidy.model.ApplicationRestriction::isActive).findFirst().orElse(null);
                model.addAttribute("activeRestriction", activeRes);
            }

            model.addAttribute("result", result);
            model.addAttribute("beneficiary", beneficiary);
            model.addAttribute("scheme", selectedScheme);
            log.info("=== ELIGIBILITY CHECK REQUEST END SUCCESS ===");
            return "beneficiary/eligibility-result";
        } catch (Exception ex) {
            log.error("Unhandled exception during eligibility check for beneficiary {}: {}", beneficiary.getId(), ex.getMessage(), ex);
            model.addAttribute("errorMessage", "Unable to evaluate eligibility. Please verify form inputs and try again.");
            return populateAndReturnForm(schemeId, requestDto, beneficiary, activeSchemes, model);
        }
    }

    private String validateDocumentFile(MultipartFile file, RequiredDocument reqDoc) {
        if (file.isEmpty()) {
            return reqDoc.isRequired() ? reqDoc.getDocumentName() + " file is empty." : null;
        }

        long maxSizeInBytes = (long) (reqDoc.getMaxFileSizeMb() != null ? reqDoc.getMaxFileSizeMb() : 5) * 1024 * 1024;
        if (file.getSize() > maxSizeInBytes) {
            return "File size exceeds limit of " + reqDoc.getMaxFileSizeMb() + " MB.";
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.contains(".")) {
            return "Invalid file format.";
        }

        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toUpperCase();
        String allowedStr = reqDoc.getAllowedFileTypes() != null ? reqDoc.getAllowedFileTypes().toUpperCase() : "PDF, JPG, JPEG, PNG";

        boolean match = false;
        for (String allowed : allowedStr.split(",")) {
            if (allowed.trim().replace(".", "").equals(extension)) {
                match = true;
                break;
            }
        }

        if (!match) {
            return "Unsupported format (" + extension + "). Allowed: " + reqDoc.getAllowedFileTypes();
        }

        return null;
    }

    private String populateAndReturnForm(Long schemeId,
                                         EligibilityCheckRequestDto requestDto,
                                         Beneficiary beneficiary,
                                         List<Scheme> activeSchemes,
                                         Model model) {
        Scheme selectedScheme = null;
        List<EligibilityCriteria> criteria = new ArrayList<>();
        List<RequiredDocument> requiredDocuments = new ArrayList<>();

        if (schemeId != null) {
            try {
                selectedScheme = schemeService.getSchemeById(schemeId);
                criteria = criteriaRepository.findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(schemeId);
                requiredDocuments = requiredDocumentRepository.findBySchemeIdAndActiveTrueOrderByDisplayOrderAsc(schemeId);
            } catch (Exception ignored) {}
        }

        if (schemeId != null && beneficiary != null) {
            var resList = applicationService.getActiveRestrictions(beneficiary.getId(), schemeId);
            var activeRes = resList.stream().filter(com.example.digital_subsidy.model.ApplicationRestriction::isActive).findFirst().orElse(null);
            model.addAttribute("activeRestriction", activeRes);
        }

        model.addAttribute("requestDto", requestDto != null ? requestDto : new EligibilityCheckRequestDto());
        model.addAttribute("beneficiary", beneficiary);
        model.addAttribute("schemes", activeSchemes);
        model.addAttribute("selectedScheme", selectedScheme);
        model.addAttribute("criteria", criteria);
        model.addAttribute("requiredDocuments", requiredDocuments);

        return "beneficiary/check-eligibility";
    }
}
