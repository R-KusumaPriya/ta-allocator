package com.iitm.aero.taallocator.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "courses")
public class Course {
    @Id
    private String courseNo;
    
    private String semester;
    private int priority;
    private String courseName;
    private String instructorName;
    private int totalRegn;
    private double groupId;
    
    private String ta1;
    private String ta2;
    private String facultyEmail;
    private String column1;
    private String column2;
    private String column3;

    // Getters and Setters
    public String getFacultyEmail() { return facultyEmail; }
    public void setFacultyEmail(String facultyEmail) { this.facultyEmail = facultyEmail; }
    public String getCourseNo() { return courseNo; }
    public void setCourseNo(String courseNo) { this.courseNo = courseNo; }
    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public String getInstructorName() { return instructorName; }
    public void setInstructorName(String instructorName) { this.instructorName = instructorName; }
    public int getTotalRegn() { return totalRegn; }
    public void setTotalRegn(int totalRegn) { this.totalRegn = totalRegn; }
    public double getGroupId() { return groupId; }
    public void setGroupId(double groupId) { this.groupId = groupId; }
    public String getTa1() { return ta1; }
    public void setTa1(String ta1) { this.ta1 = ta1; }
    public String getTa2() { return ta2; }
    public void setTa2(String ta2) { this.ta2 = ta2; }
    public String getColumn1() { return column1; }
    public void setColumn1(String column1) { this.column1 = column1; }
    public String getColumn2() { return column2; }
    public void setColumn2(String column2) { this.column2 = column2; }
    public String getColumn3() { return column3; }
    public void setColumn3(String column3) { this.column3 = column3; }
}
