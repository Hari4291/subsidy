package com.example.digital_subsidy.dto;

import jakarta.validation.constraints.*;

public class BeneficiaryRegistrationDto {

    @NotBlank(message = "Full Name is required")
    @Pattern(regexp = "^[a-zA-Z ]{3,50}$", message = "Full Name must be 3-50 characters long and contain only letters and spaces")
    private String fullName;

    @NotBlank(message = "Email address is required")
    @Pattern(regexp = "^[a-zA-Z0-9._%+-]+@gmail\\.com$", message = "Only valid @gmail.com email addresses are accepted")
    private String email;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Mobile number must be exactly 10 digits starting with 6, 7, 8, or 9")
    private String mobileNumber;

    @NotBlank(message = "Password is required")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
        message = "Password must be at least 8 characters with 1 uppercase, 1 lowercase, 1 digit, and 1 special character (@$!%*?&)"
    )
    private String password;

    @NotBlank(message = "Confirm Password is required")
    private String confirmPassword;

    @NotNull(message = "Age is required")
    @Min(value = 0, message = "Age cannot be negative")
    @Max(value = 120, message = "Age cannot exceed 120 years")
    private Integer age;

    @NotBlank(message = "Gender selection is required")
    @Pattern(regexp = "^(Male|Female|Other)$", message = "Select a valid gender option")
    private String gender;

    @NotBlank(message = "Category selection is required")
    @Pattern(regexp = "^(General|OBC|SC|ST|Minority|Other)$", message = "Select a valid social category")
    private String category;

    @NotBlank(message = "State selection is required")
    private String state;

    @NotBlank(message = "District selection is required")
    private String district;

    @NotBlank(message = "Address is required")
    @Size(min = 10, max = 200, message = "Address must be between 10 and 200 characters")
    @Pattern(regexp = "^[a-zA-Z0-9 /-]+$", message = "Address contains invalid characters. Only letters, numbers, spaces, slash (/), and hyphen (-) are allowed.")
    private String address;

    @NotBlank(message = "Pincode is required")
    @Pattern(regexp = "^\\d{6}$", message = "Pincode must be exactly 6 digits")
    private String pincode;

    @NotBlank(message = "Identity Type selection is required")
    @Pattern(regexp = "^(Aadhaar|PAN)$", message = "Identity Type must be Aadhaar or PAN")
    private String identityType;

    @NotBlank(message = "Identity Number is required")
    private String identityNumber;

    @AssertTrue(message = "You must agree to the portal terms and conditions")
    private boolean termsAgreed;

    public BeneficiaryRegistrationDto() {}

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

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
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

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPincode() {
        return pincode;
    }

    public void setPincode(String pincode) {
        this.pincode = pincode;
    }

    public String getIdentityType() {
        return identityType;
    }

    public void setIdentityType(String identityType) {
        this.identityType = identityType;
    }

    public String getIdentityNumber() {
        return identityNumber;
    }

    public void setIdentityNumber(String identityNumber) {
        this.identityNumber = identityNumber;
    }

    public boolean isTermsAgreed() {
        return termsAgreed;
    }

    public void setTermsAgreed(boolean termsAgreed) {
        this.termsAgreed = termsAgreed;
    }
}
