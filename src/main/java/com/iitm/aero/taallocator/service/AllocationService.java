package com.iitm.aero.taallocator.service;

import com.iitm.aero.taallocator.model.Course;
import com.iitm.aero.taallocator.model.PreferenceResponse;
import com.iitm.aero.taallocator.model.Student;
import com.iitm.aero.taallocator.repository.CourseRepository;
import com.iitm.aero.taallocator.repository.PreferenceRepository;
import com.iitm.aero.taallocator.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AllocationService {

    @Autowired
    private CourseRepository courseRepository;
    
    @Autowired
    private StudentRepository studentRepository;
    
    @Autowired
    private PreferenceRepository preferenceRepository;

    public void allocateGroup(double groupId) {
        List<Course> courses = courseRepository.findByGroupId(groupId);
        System.out.println("Allocating for Group ID: " + groupId + ". Found " + courses.size() + " courses.");
        
        for (Course course : courses) {
            String courseNo = course.getCourseNo() != null ? course.getCourseNo().trim() : "";
            System.out.println("Processing Course: " + courseNo);
            
            // Fetch all preferences and match manually to avoid case/space issues in DB query
            List<PreferenceResponse> allPrefs = preferenceRepository.findAll();
            List<PreferenceResponse> prefs = allPrefs.stream()
                .filter(p -> p.getSelectCourse() != null && p.getSelectCourse().trim().equalsIgnoreCase(courseNo))
                .toList();

            if (prefs.isEmpty()) {
                System.out.println("  -> No preferences found for course: " + courseNo);
                continue; // No preference submitted
            }
            
            // Assuming the latest preference is what we want, or just the first one found
            PreferenceResponse pref = prefs.get(prefs.size() - 1); 
            System.out.println("  -> Found preference from: " + pref.getFacultyName());
            
            // Check if professor said "I do not want TAs"
            if (pref.getTaPreference1() != null && pref.getTaPreference1().toLowerCase().contains("do not want tas")) {
                System.out.println("  -> Professor explicitly does not want TAs.");
                continue;
            }
            
            // Try assigning up to 2 TAs based on preference
            String[] taPrefs = {
                pref.getTaPreference1(), 
                pref.getTaPreference2(), 
                pref.getTaPreference3(), 
                pref.getTaPreference4()
            };
            
            int assignedCount = 0;
            for (String taPref : taPrefs) {
                if (taPref == null || taPref.trim().isEmpty() || taPref.toLowerCase().contains("no preference")) {
                    continue; // we can add random assignment logic later if needed
                }
                
                String rollNo = extractRollNo(taPref);
                if (rollNo == null) {
                    System.out.println("  -> Could not extract roll no from: " + taPref);
                    continue;
                }
                
                System.out.println("  -> Trying to assign TA with Roll No: " + rollNo);
                
                // Manual search for student to ignore case and spaces
                List<Student> allStudents = studentRepository.findAll();
                Optional<Student> studentOpt = allStudents.stream()
                    .filter(s -> s.getRollNo() != null && s.getRollNo().trim().equalsIgnoreCase(rollNo))
                    .findFirst();

                if (studentOpt.isPresent()) {
                    Student student = studentOpt.get();
                    if (student.isAvailable()) {
                        // Assign TA
                        if (assignedCount == 0) {
                            course.setTa1(student.getRollNo());
                        } else if (assignedCount == 1) {
                            course.setTa2(student.getRollNo());
                        }
                        
                        student.setAvailable(false);
                        student.setCourse(course.getCourseNo());
                        studentRepository.save(student);
                        
                        assignedCount++;
                        System.out.println("  -> Successfully assigned: " + student.getRollNo());
                        if (assignedCount >= 2) {
                            break; // Maximum 2 TAs per course
                        }
                    } else {
                        System.out.println("  -> Student " + rollNo + " is NOT available.");
                    }
                } else {
                    System.out.println("  -> Student " + rollNo + " not found in database.");
                }
            }
            courseRepository.save(course);
        }
    }
    
    private String extractRollNo(String prefString) {
        if (prefString.contains(":")) {
            return prefString.split(":")[0].trim();
        }
        // In case it's just the roll number
        if (prefString.trim().length() >= 8) {
            return prefString.trim().substring(0, 8).trim(); 
        }
        return null;
    }
}
