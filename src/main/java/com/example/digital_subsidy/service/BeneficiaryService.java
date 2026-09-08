package com.example.digital_subsidy.service;

import com.example.digital_subsidy.dto.BeneficiaryRegistrationDto;
import com.example.digital_subsidy.exception.DuplicateResourceException;
import com.example.digital_subsidy.model.Beneficiary;
import com.example.digital_subsidy.model.Role;
import com.example.digital_subsidy.model.User;
import com.example.digital_subsidy.model.Region;
import com.example.digital_subsidy.repository.BeneficiaryRepository;
import com.example.digital_subsidy.repository.RegionRepository;
import com.example.digital_subsidy.repository.RoleRepository;
import com.example.digital_subsidy.repository.UserRepository;
import com.example.digital_subsidy.util.AuditLogger;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class BeneficiaryService {

    private final UserRepository userRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final RoleRepository roleRepository;
    private final RegionRepository regionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogger auditLogger;

    public BeneficiaryService(UserRepository userRepository,
                              BeneficiaryRepository beneficiaryRepository,
                              RoleRepository roleRepository,
                              RegionRepository regionRepository,
                              PasswordEncoder passwordEncoder,
                              AuditLogger auditLogger) {
        this.userRepository = userRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.roleRepository = roleRepository;
        this.regionRepository = regionRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogger = auditLogger;
    }

    @Transactional
    public Beneficiary registerBeneficiary(BeneficiaryRegistrationDto dto) {
        // 1. Password match check
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new IllegalArgumentException("Password and Confirm Password do not match");
        }

        // 1b. State-District Validation (DB Source of Truth)
        if (dto.getState() != null && dto.getDistrict() != null) {
            String stateTrimmed = dto.getState().trim();
            String districtTrimmed = dto.getDistrict().trim();
            List<Region> validRegions = regionRepository.findByStateNameAndActiveTrueOrderByDistrictName(stateTrimmed);
            if (!validRegions.isEmpty()) {
                boolean matches = validRegions.stream()
                        .anyMatch(r -> r.getDistrictName().equalsIgnoreCase(districtTrimmed));
                if (!matches) {
                    throw new IllegalArgumentException("Selected district '" + districtTrimmed + "' does not belong to state '" + stateTrimmed + "'");
                }
            }
        }

        // 2. Duplicate Email Check
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new DuplicateResourceException("An account with email " + dto.getEmail() + " already exists");
        }

        // 3. Duplicate Mobile Check (Only 10 digits stored)
        String mobileClean = dto.getMobileNumber().trim();
        if (userRepository.existsByMobileNumber(mobileClean)) {
            throw new DuplicateResourceException("Mobile number " + mobileClean + " is already registered");
        }

        // 4. Duplicate Identity Check
        String identityClean = dto.getIdentityNumber().trim().toUpperCase();
        if (beneficiaryRepository.existsByIdentityNumber(identityClean)) {
            throw new DuplicateResourceException(dto.getIdentityType() + " number is already registered in the system");
        }

        // 5. Identity Format Business Validation
        if ("Aadhaar".equalsIgnoreCase(dto.getIdentityType())) {
            if (!identityClean.matches("^\\d{12}$")) {
                throw new IllegalArgumentException("Aadhaar Number must be exactly 12 numeric digits");
            }
        } else if ("PAN".equalsIgnoreCase(dto.getIdentityType())) {
            if (!identityClean.matches("^[A-Z]{5}\\d{4}[A-Z]{1}$")) {
                throw new IllegalArgumentException("PAN format must be 5 uppercase letters, 4 digits, and 1 uppercase letter (e.g. ABCDE1234F)");
            }
        } else {
            throw new IllegalArgumentException("Invalid Identity Type selected");
        }

        // 6. Fetch ROLE_BENEFICIARY
        Role role = roleRepository.findByName("ROLE_BENEFICIARY")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_BENEFICIARY")));

        // 7. Create User
        User user = new User();
        user.setFullName(dto.getFullName().trim());
        user.setEmail(dto.getEmail().trim().toLowerCase());
        user.setMobileNumber(mobileClean);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.getRoles().add(role);

        // 8. Create Beneficiary
        Beneficiary beneficiary = new Beneficiary();
        beneficiary.setUser(user);
        beneficiary.setAge(dto.getAge());
        beneficiary.setGender(dto.getGender());
        beneficiary.setCategory(dto.getCategory());
        beneficiary.setState(dto.getState().trim());
        beneficiary.setDistrict(dto.getDistrict().trim());
        beneficiary.setAddress(dto.getAddress().trim());
        beneficiary.setPincode(dto.getPincode().trim());
        beneficiary.setIdentityType(dto.getIdentityType());
        beneficiary.setIdentityNumber(identityClean);
        beneficiary.setIdentityStatus("PENDING");

        Beneficiary savedBeneficiary = beneficiaryRepository.save(beneficiary);

        // Audit Log
        auditLogger.log("BENEFICIARY_REGISTERED", "Beneficiary", savedBeneficiary.getId(), 
                "Beneficiary registered: " + savedBeneficiary.getUser().getEmail());

        return savedBeneficiary;
    }

    public Optional<Beneficiary> getBeneficiaryByEmail(String email) {
        return beneficiaryRepository.findByUserEmail(email);
    }
}
