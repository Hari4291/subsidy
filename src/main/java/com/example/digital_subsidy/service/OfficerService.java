package com.example.digital_subsidy.service;

import com.example.digital_subsidy.dto.OfficerRegistrationDto;
import com.example.digital_subsidy.exception.DuplicateResourceException;
import com.example.digital_subsidy.model.Officer;
import com.example.digital_subsidy.model.Role;
import com.example.digital_subsidy.model.User;
import com.example.digital_subsidy.repository.OfficerRepository;
import com.example.digital_subsidy.repository.RoleRepository;
import com.example.digital_subsidy.repository.UserRepository;
import com.example.digital_subsidy.util.AuditLogger;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class OfficerService {

    private final UserRepository userRepository;
    private final OfficerRepository officerRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogger auditLogger;

    public OfficerService(UserRepository userRepository,
                          OfficerRepository officerRepository,
                          RoleRepository roleRepository,
                          PasswordEncoder passwordEncoder,
                          AuditLogger auditLogger) {
        this.userRepository = userRepository;
        this.officerRepository = officerRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogger = auditLogger;
    }

    @Transactional
    public Officer createOfficer(OfficerRegistrationDto dto) {
        String cleanEmail = dto.getEmail().trim().toLowerCase();
        String cleanMobile = dto.getMobileNumber().trim();

        if (userRepository.existsByEmail(cleanEmail) || officerRepository.existsByEmail(cleanEmail)) {
            throw new DuplicateResourceException("Officer with email " + cleanEmail + " already exists");
        }
        if (userRepository.existsByMobileNumber(cleanMobile) || officerRepository.existsByMobileNumber(cleanMobile)) {
            throw new DuplicateResourceException("Officer with mobile " + cleanMobile + " already exists");
        }

        String roleName = "ROLE_" + dto.getOfficerType().toUpperCase();
        Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(new Role(roleName)));

        User user = new User();
        user.setFullName(dto.getFullName().trim());
        user.setEmail(cleanEmail);
        user.setMobileNumber(cleanMobile);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.getRoles().add(role);
        User savedUser = userRepository.save(user);

        Officer officer = new Officer();
        officer.setUser(savedUser);
        officer.setFullName(savedUser.getFullName());
        officer.setEmail(savedUser.getEmail());
        officer.setMobileNumber(savedUser.getMobileNumber());
        officer.setOfficerType(dto.getOfficerType().toUpperCase());
        officer.setState(dto.getState() != null ? dto.getState().trim() : "Andhra Pradesh");
        officer.setDistrict(dto.getDistrict() != null ? dto.getDistrict().trim() : "Kakinada");
        officer.setStatus("ACTIVE");

        Officer savedOfficer = officerRepository.save(officer);

        auditLogger.log("OFFICER_CREATED", "Officer", savedOfficer.getId(),
                "Created " + savedOfficer.getOfficerType() + ": " + savedOfficer.getEmail());

        return savedOfficer;
    }

    public List<Officer> getAllOfficers() {
        return officerRepository.findAll();
    }

    public Optional<Officer> getOfficerByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return officerRepository.findByUserEmailIgnoreCase(email.trim())
                .or(() -> officerRepository.findByEmailIgnoreCase(email.trim()))
                .or(() -> officerRepository.findByUserEmail(email));
    }

    public long countOfficers() {
        return officerRepository.count();
    }

    public long countOfficersByType(String officerType) {
        return officerRepository.countByOfficerType(officerType);
    }

    public List<Officer> getFieldOfficersByDistrict(String state, String district) {
        if (state != null && district != null && !district.isBlank()) {
            return officerRepository.findByOfficerTypeAndStateAndDistrict("FIELD_OFFICER", state, district);
        } else if (state != null && !state.isBlank()) {
            return officerRepository.findByOfficerTypeAndState("FIELD_OFFICER", state);
        }
        return officerRepository.findByOfficerType("FIELD_OFFICER");
    }

    @Transactional
    public void assignApplicationToOfficer(com.example.digital_subsidy.model.Application app, Officer officer, String assignedBy) {
        app.setAssignedOfficer(officer);
        app.setUpdatedAt(java.time.LocalDateTime.now());
        auditLogger.log("APPLICATION_REASSIGNED", "Application", app.getId(),
                "Application #" + app.getId() + " reassigned to Field Officer " + (officer != null ? officer.getEmail() : "UNASSIGNED") + " by " + assignedBy);
    }
}
