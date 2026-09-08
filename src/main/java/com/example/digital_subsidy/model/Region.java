package com.example.digital_subsidy.model;

import jakarta.persistence.*;

@Entity
@Table(name = "regions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"state_name", "district_name"})
})
public class Region {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "state_name", nullable = false, length = 100)
    private String stateName;

    @Column(name = "district_name", nullable = false, length = 100)
    private String districtName;

    private boolean active = true;

    public Region() {}

    public Region(String stateName, String districtName) {
        this.stateName = stateName;
        this.districtName = districtName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStateName() {
        return stateName;
    }

    public void setStateName(String stateName) {
        this.stateName = stateName;
    }

    public String getDistrictName() {
        return districtName;
    }

    public void setDistrictName(String districtName) {
        this.districtName = districtName;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
