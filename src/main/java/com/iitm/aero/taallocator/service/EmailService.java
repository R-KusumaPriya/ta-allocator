package com.iitm.aero.taallocator.service;

import com.iitm.aero.taallocator.model.Course;
import com.iitm.aero.taallocator.model.Faculty;
import com.iitm.aero.taallocator.model.Student;
import com.iitm.aero.taallocator.repository.CourseRepository;
import com.iitm.aero.taallocator.repository.FacultyRepository;
import com.iitm.aero.taallocator.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;
    
    @Autowired
    private CourseRepository courseRepository;
    
    @Autowired
    private FacultyRepository facultyRepository;
    
    @Autowired
    private StudentRepository studentRepository;

    public String sendPreferenceEmails(double groupId, String termString, String deadline, String formLink) {
        List<Course> courses = courseRepository.findByGroupId(groupId);
        if (courses.isEmpty()) {
            return "No courses found for Group ID: " + groupId;
        }
        
        int sentCount = 0;
        List<String> logs = new java.util.ArrayList<>();
        
        for (Course course : courses) {
            String email = resolveFacultyEmail(course);
            if (email == null || email.trim().isEmpty()) {
                logs.add("Skipped " + course.getCourseNo() + ": Faculty '" + course.getInstructorName() + "' has no email in database.");
                continue;
            }
            
            String fname = toCapitalCase(course.getInstructorName() != null ? course.getInstructorName() : "Colleague");
            String sub = termString + " TA Allotment Group " + groupId + " - " + course.getCourseNo();
            
            String msg = "Dear Prof. " + fname + ",\n\n" +
                    "Please fill up the following form at the earliest to help with the TA allotment for the coming semester. You are set to teach " +
                    course.getCourseNo() + " this semester.\n" +
                    "In the form, I am asking you to choose 4 preferences for each class that you are handling.\n" +
                    formLink + " \n\n" +
                    "While it will be convenient to have this done ASAP, I will wait till " +
                    deadline + " before I allot the TA and proceed.\n\n" +
                    "Best,\nNidish Narayanaa Balaji\nAssistant Professor,\n" +
                    "Department of Aerospace Engineering,\nIIT Madras, Chennai 600036, IN";
            
            String err = sendEmail(email, sub, msg, null);
            if (err == null) {
                sentCount++;
                logs.add("Sent to " + email + " (" + course.getCourseNo() + ")");
            } else {
                logs.add("FAILED for " + email + ": " + err);
            }
        }
        return "Preference Emails: " + sentCount + "/" + courses.size() + " sent. Details: " + String.join(" | ", logs);
    }

    public String sendConfirmationEmails(double groupId, String termString) {
        List<Course> courses = courseRepository.findByGroupId(groupId);
        if (courses.isEmpty()) {
            return "No courses found for Group ID: " + groupId;
        }
        
        int sentCount = 0;
        List<String> logs = new java.util.ArrayList<>();
        
        for (Course course : courses) {
            String email = resolveFacultyEmail(course);
            if (email == null || email.trim().isEmpty()) {
                logs.add("Skipped " + course.getCourseNo() + ": Faculty '" + course.getInstructorName() + "' not found in faculty table.");
                continue;
            }
            
            StringBuilder tasList = new StringBuilder();
            if (course.getTa1() != null && !course.getTa1().trim().isEmpty() && !course.getTa1().equalsIgnoreCase("xxxx")) {
                tasList.append("+ ").append(course.getTa1()).append(": ").append(getStudentName(course.getTa1())).append("\n");
            }
            if (course.getTa2() != null && !course.getTa2().trim().isEmpty() && !course.getTa2().equalsIgnoreCase("xxxx")) {
                tasList.append("+ ").append(course.getTa2()).append(": ").append(getStudentName(course.getTa2())).append("\n");
            }
            
            if (tasList.length() == 0) {
                logs.add("Skipped " + course.getCourseNo() + ": No TAs assigned yet. (Did you click 'Run Allocation Algorithm' first?)");
                continue;
            }
            
            String fname = toCapitalCase(course.getInstructorName() != null ? course.getInstructorName() : "Colleague");
            String sub = course.getCourseNo() + " " + termString + " TA Allotment Confirmation";
            
            String msg = "Dear Prof. " + fname + ",\n\n" +
                    "This email is to confirm that the following TA(s) has(have) " +
                    "been allotted to assist you for the class " +
                    course.getCourseNo() + " (" + course.getCourseName() + "), to be taught by you in the period " +
                    termString + ".\n" +
                    tasList.toString() +
                    "\nPlease disregard any previous emails you may have received in this regard.\n" +
                    "Dear TAs, please make it a point to reach out to the course professor and " +
                    "schedule a kickoff meeting within the next two working days.\n\n" +
                    "Best,\nNidish Narayanaa Balaji\nAssistant Professor,\n" +
                    "Department of Aerospace Engineering,\nIIT Madras, Chennai 600036, IN";
            
            // CC emails disabled during testing to prevent emailing student roll numbers
            String err = sendEmail(email, sub, msg, null);
            if (err == null) {
                sentCount++;
                logs.add("Sent to " + email + " (" + course.getCourseNo() + ")");
            } else {
                logs.add("FAILED for " + email + ": " + err);
            }
        }
        return "Confirmation Emails: " + sentCount + " sent. Details: " + String.join(" | ", logs);
    }

    private String resolveFacultyEmail(Course course) {
        if (course == null) return null;
        
        // 1. Try finding in faculty table by exact name
        if (course.getInstructorName() != null && !course.getInstructorName().trim().isEmpty()) {
            Faculty faculty = facultyRepository.findByNameIgnoreCase(course.getInstructorName().trim());
            if (faculty != null && faculty.getEmail() != null && !faculty.getEmail().trim().isEmpty()) {
                return faculty.getEmail().trim();
            }
            
            // 2. Try normalized whitespace matching in faculty table
            String normalizedCourseInstructor = course.getInstructorName().trim().replaceAll("\\s+", " ").toLowerCase();
            List<Faculty> allFaculty = facultyRepository.findAll();
            for (Faculty f : allFaculty) {
                if (f.getName() != null) {
                    String normalizedFacName = f.getName().trim().replaceAll("\\s+", " ").toLowerCase();
                    if (normalizedFacName.equals(normalizedCourseInstructor)) {
                        if (f.getEmail() != null && !f.getEmail().trim().isEmpty()) {
                            return f.getEmail().trim();
                        }
                    }
                }
            }
        }
        
        // 3. Fallback to direct facultyEmail on course entity if present
        if (course.getFacultyEmail() != null && !course.getFacultyEmail().trim().isEmpty()) {
            return course.getFacultyEmail().trim();
        }
        
        return null;
    }
    
    private String sendEmail(String to, String subject, String text, String[] cc) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            if (cc != null && cc.length > 0) {
                message.setCc(cc);
            }
            mailSender.send(message);
            System.out.println("Email successfully sent to: " + to);
            return null; // success
        } catch (Exception e) {
            System.err.println("Failed to send email to " + to + ": " + e.getMessage());
            return e.getMessage();
        }
    }
    
    private String getEmailForRollNo(String rollNo) {
        if (rollNo == null || rollNo.trim().isEmpty() || rollNo.equals("xxxx")) return null;
        return rollNo.toLowerCase() + "@smail.iitm.ac.in";
    }
    
    private String getStudentName(String rollNo) {
        if (rollNo == null || rollNo.trim().isEmpty() || rollNo.equals("xxxx")) return "Unknown";
        Optional<Student> opt = studentRepository.findById(rollNo);
        return opt.isPresent() ? toCapitalCase(opt.get().getName()) : "Unknown";
    }
    
    private String toCapitalCase(String str) {
        if (str == null || str.isEmpty()) return "";
        String[] words = str.toLowerCase().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.length() > 0) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
