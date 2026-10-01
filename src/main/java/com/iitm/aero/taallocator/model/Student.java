package com.iitm.aero.taallocator.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "students")
public class Student {
    @Id
    private String rollNo;
    
    private String name;
    private String cgpa; 
    private String course;
    private String program; 
    
    private boolean isAvailable = true;

    // Getters and Setters
    public String getRollNo() { return rollNo; }
    public void setRollNo(String rollNo) { this.rollNo = rollNo; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCgpa() { return cgpa; }
    public void setCgpa(String cgpa) { this.cgpa = cgpa; }
    public String getCourse() { return course; }
    public void setCourse(String course) { this.course = course; }
    public String getProgram() { return program; }
    public void setProgram(String program) { this.program = program; }
    public boolean isAvailable() { return isAvailable; }
    public void setAvailable(boolean available) { isAvailable = available; }
}
