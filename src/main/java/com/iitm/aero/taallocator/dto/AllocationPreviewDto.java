package com.iitm.aero.taallocator.dto;

public class AllocationPreviewDto {
    private String courseNo;
    private String courseName;
    private String instructorName;
    private int slotNumber;
    private String matchedRollNo;
    private String matchedStudentName;
    private String preferenceRank;
    private double matchScore;

    public AllocationPreviewDto() {}

    public AllocationPreviewDto(String courseNo, String courseName, String instructorName, 
                                int slotNumber, String matchedRollNo, String matchedStudentName, 
                                String preferenceRank, double matchScore) {
        this.courseNo = courseNo;
        this.courseName = courseName;
        this.instructorName = instructorName;
        this.slotNumber = slotNumber;
        this.matchedRollNo = matchedRollNo;
        this.matchedStudentName = matchedStudentName;
        this.preferenceRank = preferenceRank;
        this.matchScore = matchScore;
    }

    public String getCourseNo() { return courseNo; }
    public void setCourseNo(String courseNo) { this.courseNo = courseNo; }

    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }

    public String getInstructorName() { return instructorName; }
    public void setInstructorName(String instructorName) { this.instructorName = instructorName; }

    public int getSlotNumber() { return slotNumber; }
    public void setSlotNumber(int slotNumber) { this.slotNumber = slotNumber; }

    public String getMatchedRollNo() { return matchedRollNo; }
    public void setMatchedRollNo(String matchedRollNo) { this.matchedRollNo = matchedRollNo; }

    public String getMatchedStudentName() { return matchedStudentName; }
    public void setMatchedStudentName(String matchedStudentName) { this.matchedStudentName = matchedStudentName; }

    public String getPreferenceRank() { return preferenceRank; }
    public void setPreferenceRank(String preferenceRank) { this.preferenceRank = preferenceRank; }

    public double getMatchScore() { return matchScore; }
    public void setMatchScore(double matchScore) { this.matchScore = matchScore; }
}
