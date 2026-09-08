package com.example.digital_subsidy.dto;

public class RequiredDocumentDto {

    private Long id;
    private Long schemeId;
    private String documentName;
    private String documentType;
    private String description;
    private Boolean required = Boolean.TRUE;
    private String allowedFileTypes = "PDF, JPG, JPEG, PNG";
    private Integer maxFileSizeMb = 5;
    private Integer displayOrder = 1;
    private Boolean active = Boolean.TRUE;

    public RequiredDocumentDto() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSchemeId() {
        return schemeId;
    }

    public void setSchemeId(Long schemeId) {
        this.schemeId = schemeId;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getRequired() {
        return required != null ? required : true;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public String getAllowedFileTypes() {
        return allowedFileTypes;
    }

    public void setAllowedFileTypes(String allowedFileTypes) {
        this.allowedFileTypes = allowedFileTypes;
    }

    public Integer getMaxFileSizeMb() {
        return maxFileSizeMb != null ? maxFileSizeMb : 5;
    }

    public void setMaxFileSizeMb(Integer maxFileSizeMb) {
        this.maxFileSizeMb = maxFileSizeMb;
    }

    public Integer getDisplayOrder() {
        return displayOrder != null ? displayOrder : 1;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Boolean getActive() {
        return active != null ? active : true;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
