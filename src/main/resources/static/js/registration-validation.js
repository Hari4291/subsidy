document.addEventListener("DOMContentLoaded", function () {
  const form = document.getElementById("registrationForm");
  if (!form) return;

  const fullNameInput = document.getElementById("fullName");
  const emailInput = document.getElementById("email");
  const mobileInput = document.getElementById("mobileNumber");
  const passwordInput = document.getElementById("password");
  const confirmPasswordInput = document.getElementById("confirmPassword");
  const ageInput = document.getElementById("age");
  const addressInput = document.getElementById("address");
  const pincodeInput = document.getElementById("pincode");
  const identityTypeSelect = document.getElementById("identityType");
  const identityNumberInput = document.getElementById("identityNumber");
  const stateSelect = document.getElementById("state");
  const districtSelect = document.getElementById("district");
  const termsCheckbox = document.getElementById("termsAgreed");
  const submitBtn = document.getElementById("submitBtn");

  // Regex Patterns
  const nameRegex = /^[a-zA-Z ]{3,50}$/;
  const emailRegex = /^[a-zA-Z0-9._%+-]+@gmail\.com$/;
  const mobileRegex = /^[6-9]\d{9}$/;
  const passwordRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/;
  const pincodeRegex = /^\d{6}$/;
  const addressAllowedRegex = /^[a-zA-Z0-9 /-]+$/;

  // Real-time Address Input Filtering (Disallow illegal special chars immediately on keypress)
  if (addressInput) {
    addressInput.addEventListener("input", function () {
      // Strip disallowed characters immediately
      this.value = this.value.replace(/[^a-zA-Z0-9 /\-]/g, "");
      validateAddress();
      checkFormValidity();
    });
  }

  // Real-time Mobile Filtering (Digits only, max 10)
  if (mobileInput) {
    mobileInput.addEventListener("input", function () {
      this.value = this.value.replace(/\D/g, "").slice(0, 10);
      validateMobile();
      checkFormValidity();
    });
  }

  // Real-time Age Filtering (Whole numbers 0-120)
  if (ageInput) {
    ageInput.addEventListener("input", function () {
      this.value = this.value.replace(/\D/g, "");
      validateAge();
      checkFormValidity();
    });
  }

  // Password Visibility Toggle
  const togglePasswordBtn = document.getElementById("togglePassword");
  const togglePasswordIcon = document.getElementById("togglePasswordIcon");
  if (togglePasswordBtn && passwordInput && togglePasswordIcon) {
    togglePasswordBtn.addEventListener("click", function () {
      const isPassword = passwordInput.getAttribute("type") === "password";
      passwordInput.setAttribute("type", isPassword ? "text" : "password");
      togglePasswordIcon.classList.toggle("fa-eye", !isPassword);
      togglePasswordIcon.classList.toggle("fa-eye-slash", isPassword);
    });
  }

  // Confirm Password Visibility Toggle
  const toggleConfirmPasswordBtn = document.getElementById("toggleConfirmPassword");
  const toggleConfirmPasswordIcon = document.getElementById("toggleConfirmPasswordIcon");
  if (toggleConfirmPasswordBtn && confirmPasswordInput && toggleConfirmPasswordIcon) {
    toggleConfirmPasswordBtn.addEventListener("click", function () {
      const isPassword = confirmPasswordInput.getAttribute("type") === "password";
      confirmPasswordInput.setAttribute("type", isPassword ? "text" : "password");
      toggleConfirmPasswordIcon.classList.toggle("fa-eye", !isPassword);
      toggleConfirmPasswordIcon.classList.toggle("fa-eye-slash", isPassword);
    });
  }

  // Real-time Pincode Filtering
  if (pincodeInput) {
    pincodeInput.addEventListener("input", function () {
      this.value = this.value.replace(/\D/g, "").slice(0, 6);
      validatePincode();
      checkFormValidity();
    });
  }

  // Dynamic District fetch helper
  function loadDistrictsForState(selectedState, targetDistrictValue = "") {
    if (!districtSelect) return;

    if (!selectedState) {
      districtSelect.innerHTML = '<option value="">-- Select District --</option>';
      districtSelect.value = "";
      districtSelect.disabled = true;
      validateField(districtSelect, false, "District selection is required");
      checkFormValidity();
      return;
    }

    districtSelect.disabled = true;
    districtSelect.innerHTML = '<option value="">-- Loading Districts --</option>';

    fetch("/api/districts/" + encodeURIComponent(selectedState))
      .then(response => {
        if (!response.ok) throw new Error("HTTP " + response.status);
        return response.json();
      })
      .then(data => {
        districtSelect.innerHTML = '<option value="">-- Select District --</option>';
        if (data && data.length > 0) {
          data.forEach(district => {
            const opt = document.createElement("option");
            opt.value = district;
            opt.textContent = district;
            if (targetDistrictValue && district.toLowerCase() === targetDistrictValue.toLowerCase()) {
              opt.selected = true;
            }
            districtSelect.appendChild(opt);
          });
          districtSelect.disabled = false;
        } else {
          districtSelect.innerHTML = '<option value="">-- No Districts Available --</option>';
          districtSelect.disabled = true;
        }
        validateField(districtSelect, districtSelect.value !== "", "District selection is required");
        checkFormValidity();
      })
      .catch(() => {
        districtSelect.innerHTML = '<option value="">-- Error Loading Districts --</option>';
        districtSelect.disabled = true;
        validateField(districtSelect, false, "Unable to load districts");
        checkFormValidity();
      });
  }

  // State & District change event listeners
  if (stateSelect && districtSelect) {
    stateSelect.addEventListener("change", function () {
      loadDistrictsForState(this.value);
    });

    districtSelect.addEventListener("change", function () {
      validateField(districtSelect, this.value !== "", "District selection is required");
      checkFormValidity();
    });

    // Check initial state on page load (e.g. form validation redisplay or pre-filled value)
    if (stateSelect.value) {
      const initialDistrict = districtSelect.getAttribute("data-initial-val") || districtSelect.value || "";
      loadDistrictsForState(stateSelect.value, initialDistrict);
    } else {
      districtSelect.disabled = true;
    }
  }

  // Add event listeners for all fields
  fullNameInput?.addEventListener("input", () => { validateFullName(); checkFormValidity(); });
  emailInput?.addEventListener("input", () => { validateEmail(); checkFormValidity(); });
  passwordInput?.addEventListener("input", () => { validatePassword(); validateConfirmPassword(); checkFormValidity(); });
  confirmPasswordInput?.addEventListener("input", () => { validateConfirmPassword(); checkFormValidity(); });
  identityTypeSelect?.addEventListener("change", () => { validateIdentity(); checkFormValidity(); });
  identityNumberInput?.addEventListener("input", () => { validateIdentity(); checkFormValidity(); });
  termsCheckbox?.addEventListener("change", () => { checkFormValidity(); });

  function validateFullName() {
    const val = fullNameInput.value.trim();
    return validateField(fullNameInput, nameRegex.test(val), "Full Name must be 3-50 letters and spaces only without numbers or special characters");
  }

  function validateEmail() {
    const val = emailInput.value.trim();
    return validateField(emailInput, emailRegex.test(val), "Only valid @gmail.com email addresses are accepted (e.g. name@gmail.com)");
  }

  function validateMobile() {
    const val = mobileInput.value.trim();
    return validateField(mobileInput, mobileRegex.test(val), "Mobile number must be exactly 10 digits starting with 6, 7, 8, or 9");
  }

  function validatePassword() {
    const val = passwordInput.value;
    const isValid = passwordRegex.test(val);
    validateField(passwordInput, isValid, "Password must be at least 8 chars with 1 uppercase, 1 lowercase, 1 digit, and 1 special char");
    
    // Password Strength Meter
    const strengthMeter = document.getElementById("passwordStrength");
    if (strengthMeter) {
      if (val.length === 0) {
        strengthMeter.textContent = "";
      } else if (!isValid) {
        strengthMeter.textContent = "Strength: Weak";
        strengthMeter.className = "form-text text-danger font-weight-bold";
      } else if (val.length >= 12) {
        strengthMeter.textContent = "Strength: Strong";
        strengthMeter.className = "form-text text-success font-weight-bold";
      } else {
        strengthMeter.textContent = "Strength: Medium";
        strengthMeter.className = "form-text text-warning font-weight-bold";
      }
    }
    return isValid;
  }

  function validateConfirmPassword() {
    const val = confirmPasswordInput.value;
    const matches = val === passwordInput.value && val !== "";
    return validateField(confirmPasswordInput, matches, "Confirm Password must match Password");
  }

  function validateAge() {
    const rawVal = ageInput ? ageInput.value.trim() : "";
    if (rawVal === "" || /\D/.test(rawVal)) {
      return validateField(ageInput, false, "Age must be a whole number between 0 and 120");
    }
    const val = parseInt(rawVal, 10);
    const valid = !isNaN(val) && val >= 0 && val <= 120;
    return validateField(ageInput, valid, "Age must be a whole number between 0 and 120");
  }

  function validateAddress() {
    const val = addressInput.value.trim();
    const lenValid = val.length >= 10 && val.length <= 200;
    const charValid = addressAllowedRegex.test(val);
    return validateField(addressInput, lenValid && charValid, "Address must be 10-200 chars. Special characters like @, $, %, &, *, +, ! are not allowed");
  }

  function validatePincode() {
    const val = pincodeInput.value.trim();
    return validateField(pincodeInput, pincodeRegex.test(val), "Pincode must be exactly 6 numeric digits");
  }

  function validateIdentity() {
    const type = identityTypeSelect.value;
    const val = identityNumberInput.value.trim().toUpperCase();
    let valid = false;
    let msg = "";

    if (type === "Aadhaar") {
      valid = /^\d{12}$/.test(val);
      msg = "Aadhaar number must be exactly 12 numeric digits";
    } else if (type === "PAN") {
      valid = /^[A-Z]{5}\d{4}[A-Z]{1}$/.test(val);
      msg = "PAN format must be 5 uppercase letters, 4 digits, 1 uppercase letter (e.g. ABCDE1234F)";
    } else {
      msg = "Select Identity Type";
    }
    return validateField(identityNumberInput, valid, msg);
  }

  function validateField(inputElement, isValid, errorMessage) {
    if (!inputElement) return false;
    const errorDiv = document.getElementById(inputElement.id + "Error");

    if (isValid) {
      inputElement.classList.remove("is-invalid", "is-invalid-custom");
      inputElement.classList.add("is-valid-custom");
      if (errorDiv) errorDiv.textContent = "";
    } else {
      inputElement.classList.remove("is-valid-custom");
      inputElement.classList.add("is-invalid-custom");
      if (errorDiv) errorDiv.textContent = errorMessage;
    }
    return isValid;
  }

  function checkFormValidity() {
    const allValid = validateFullName() &&
                     validateEmail() &&
                     validateMobile() &&
                     validatePassword() &&
                     validateConfirmPassword() &&
                     validateAge() &&
                     validateAddress() &&
                     validatePincode() &&
                     validateIdentity() &&
                     stateSelect.value !== "" &&
                     districtSelect.value !== "" &&
                     termsCheckbox.checked;

    if (submitBtn) {
      submitBtn.disabled = !allValid;
    }
  }
});
