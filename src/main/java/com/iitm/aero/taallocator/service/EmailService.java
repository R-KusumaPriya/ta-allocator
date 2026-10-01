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

    public void sendPreferenceEmails(double groupId, String termString, String deadline, String formLink) {
        List<Course> courses = courseRepository.findByGroupId(groupId);
        
        for (Course course : courses) {
            Faculty faculty = facultyRepository.findByNameIgnoreCase(course.getInstructorName());
            if (faculty == null || faculty.getEmail() == null) continue;
            
            String fname = toCapitalCase(faculty.getName());
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
            
            sendEmail(faculty.getEmail(), sub, msg, null);
        }
    }

    public void sendConfirmationEmails(double groupId, String termString) {
        List<Course> courses = courseRepository.findByGroupId(groupId);
        
        for (Course course : courses) {
            Faculty faculty = facultyRepository.findByNameIgnoreCase(course.getInstructorName());
            if (faculty == null || faculty.getEmail() == null) continue;
            
            String ta1Email = getEmailForRollNo(course.getTa1());
            String ta2Email = getEmailForRollNo(course.getTa2());
            
            String ccs = "";
            StringBuilder tasList = new StringBuilder();
            
            if (ta1Email != null) {
                ccs += ta1Email;
                tasList.append("+ ").append(course.getTa1()).append(": ").append(getStudentName(course.getTa1())).append("\n");
            }
            if (ta2Email != null) {
                if (!ccs.isEmpty()) ccs += ",";
                ccs += ta2Email;
                tasList.append("+ ").append(course.getTa2()).append(": ").append(getStudentName(course.getTa2())).append("\n");
            }
            
            if (tasList.length() == 0) continue; // No TAs assigned
            
            String fname = toCapitalCase(faculty.getName());
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
            
            sendEmail(faculty.getEmail(), sub, msg, ccs.isEmpty() ? null : ccs.split(","));
        }
    }
    
    private void sendEmail(String to, String subject, String text, String[] cc) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            if (cc != null && cc.length > 0) {
                message.setCc(cc);
            }
            mailSender.send(message);
            System.out.println("Email sent to: " + to);
        } catch (Exception e) {
            System.err.println("Failed to send email to " + to + ": " + e.getMessage());
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
