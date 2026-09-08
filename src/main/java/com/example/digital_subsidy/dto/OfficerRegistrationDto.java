package com.example.digital_subsidy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class OfficerRegistrationDto {

    @NotBlank(message = "Full Name is required")
    @Pattern(regexp = "^[a-zA-Z ]{3,50}$", message = "Full Name must be 3-50 letters and spaces only")
    private String fullName;

    @NotBlank(message = "Email address is required")
    private String email;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Mobile number must be exactly 10 digits")
    private String mobileNumber;

    @NotBlank(message = "Password is required")
    private String password;

    @NotBlank(message = "Officer Type selection is required")
    @Pattern(regexp = "^(FIELD_OFFICER|DISTRICT_OFFICER|FINANCE_APPROVER)$", message = "Invalid officer type")
    private String officerType;

    private String state;
    private String district;

    public OfficerRegistrationDto() {}

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getOfficerType() {
        return officerType;
    }

    public void setOfficerType(String officerType) {
        this.officerType = officerType;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }
}
