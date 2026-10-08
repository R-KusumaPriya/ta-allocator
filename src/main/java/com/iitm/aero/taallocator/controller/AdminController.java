package com.iitm.aero.taallocator.controller;

import com.iitm.aero.taallocator.model.Course;
import com.iitm.aero.taallocator.repository.CourseRepository;
import com.iitm.aero.taallocator.repository.StudentRepository;
import com.iitm.aero.taallocator.service.AllocationService;
import com.iitm.aero.taallocator.service.DataUploadService;
import com.iitm.aero.taallocator.service.EmailService;
import com.iitm.aero.taallocator.service.GoogleFormsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class AdminController {

    @Autowired
    private DataUploadService dataUploadService;
    
    @Autowired
    private CourseRepository courseRepository;
    
    @Autowired
    private StudentRepository studentRepository;
    
    @Autowired
    private AllocationService allocationService;
    
    @Autowired
    private EmailService emailService;
    
    @Autowired
    private GoogleFormsService googleFormsService;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("courseCount", courseRepository.count());
        model.addAttribute("studentCount", studentRepository.count());
        model.addAttribute("availableStudentCount", studentRepository.findByIsAvailableTrue().size());
        
        // Get all unique group IDs to manage
        List<Double> groupIds = courseRepository.findAll().stream()
                .map(Course::getGroupId)
                .distinct()
                .sorted()
                .toList();
        model.addAttribute("groupIds", groupIds);
        
        return "dashboard";
    }

    @PostMapping("/upload/courses")
    public String uploadCourses(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            dataUploadService.parseAndSaveCourses(file);
            redirectAttributes.addFlashAttribute("message", "Courses uploaded successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error uploading courses: " + e.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/upload/students")
    public String uploadStudents(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            dataUploadService.parseAndSaveStudents(file);
            redirectAttributes.addFlashAttribute("message", "Students uploaded successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error uploading students: " + e.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/upload/faculty")
    public String uploadFaculty(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            dataUploadService.parseAndSaveFaculty(file);
            redirectAttributes.addFlashAttribute("message", "Faculty uploaded successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error uploading faculty: " + e.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/upload/preferences")
    public String uploadPreferences(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            dataUploadService.parseAndSavePreferences(file);
            redirectAttributes.addFlashAttribute("message", "Preferences uploaded successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error uploading preferences: " + e.getMessage());
        }
        return "redirect:/";
    }

    @GetMapping("/group")
    public String manageGroup(@RequestParam("id") double groupId, Model model) {
        List<Course> courses = courseRepository.findByGroupId(groupId);
        model.addAttribute("groupId", groupId);
        model.addAttribute("courses", courses);
        return "group-manager";
    }

    @PostMapping("/group/update-form")
    public String updateForm(@RequestParam("id") double groupId, 
                             @RequestParam("formId") String formId, 
                             RedirectAttributes redirectAttributes) {
        String result = googleFormsService.updateForm(groupId, formId);
        redirectAttributes.addFlashAttribute("message", result);
        return "redirect:/group?id=" + groupId;
    }

    @PostMapping("/group/send-preference-emails")
    public String sendPreferenceEmails(@RequestParam("id") double groupId, 
                                       @RequestParam("term") String term,
                                       @RequestParam("deadline") String deadline,
                                       @RequestParam("formLink") String formLink,
                                       RedirectAttributes redirectAttributes) {
        String result = emailService.sendPreferenceEmails(groupId, term, deadline, formLink);
        redirectAttributes.addFlashAttribute("message", result);
        return "redirect:/group?id=" + groupId;
    }

    @PostMapping("/group/allocate")
    public String runAllocation(@RequestParam("id") double groupId, RedirectAttributes redirectAttributes) {
        allocationService.allocateGroup(groupId);
        redirectAttributes.addFlashAttribute("message", "TA Allocation completed for Group " + groupId + ".");
        return "redirect:/group?id=" + groupId;
    }

    @PostMapping("/group/send-confirmation-emails")
    public String sendConfirmationEmails(@RequestParam("id") double groupId,
                                         @RequestParam("term") String term,
                                         RedirectAttributes redirectAttributes) {
        String result = emailService.sendConfirmationEmails(groupId, term);
        redirectAttributes.addFlashAttribute("message", result);
        return "redirect:/group?id=" + groupId;
    }
}
