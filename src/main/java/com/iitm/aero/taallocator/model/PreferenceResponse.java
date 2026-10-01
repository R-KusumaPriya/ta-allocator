package com.iitm.aero.taallocator.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "preferences")
public class PreferenceResponse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String timestamp;
    private String emailAddress;
    private String facultyName;
    private String selectCourse;
    private String taPreference1;
    private String taPreference2;
    private String taPreference3;
    private String taPreference4;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }
    public String getFacultyName() { return facultyName; }
    public void setFacultyName(String facultyName) { this.facultyName = facultyName; }
    public String getSelectCourse() { return selectCourse; }
    public void setSelectCourse(String selectCourse) { this.selectCourse = selectCourse; }
    public String getTaPreference1() { return taPreference1; }
    public void setTaPreference1(String taPreference1) { this.taPreference1 = taPreference1; }
    public String getTaPreference2() { return taPreference2; }
    public void setTaPreference2(String taPreference2) { this.taPreference2 = taPreference2; }
    public String getTaPreference3() { return taPreference3; }
    public void setTaPreference3(String taPreference3) { this.taPreference3 = taPreference3; }
    public String getTaPreference4() { return taPreference4; }
    public void setTaPreference4(String taPreference4) { this.taPreference4 = taPreference4; }
}
