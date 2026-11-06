package com.iitm.aero.taallocator.controller;

import com.iitm.aero.taallocator.dto.AllocationPreviewDto;
import com.iitm.aero.taallocator.model.Course;
import com.iitm.aero.taallocator.model.Student;
import com.iitm.aero.taallocator.repository.CourseRepository;
import com.iitm.aero.taallocator.repository.StudentRepository;
import com.iitm.aero.taallocator.service.AllocationService;
import com.iitm.aero.taallocator.service.DataUploadService;
import com.iitm.aero.taallocator.service.EmailService;
import com.iitm.aero.taallocator.service.GoogleFormsService;
import com.iitm.aero.taallocator.repository.FacultyRepository;
import com.iitm.aero.taallocator.repository.PreferenceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpServletResponse;

import java.beans.PropertyEditorSupport;

import java.util.Collections;
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
    private FacultyRepository facultyRepository;
    
    @Autowired
    private PreferenceRepository preferenceRepository;
    
    @Autowired
    private AllocationService allocationService;
    
    @Autowired
    private EmailService emailService;
    
    @Autowired
    private GoogleFormsService googleFormsService;

    @PostMapping("/admin/reset")
    public String resetAllData(RedirectAttributes redirectAttributes) {
        try {
            preferenceRepository.deleteAll();
            courseRepository.deleteAll();
            studentRepository.deleteAll();
            facultyRepository.deleteAll();
            redirectAttributes.addFlashAttribute("message", "All data and allocations have been reset successfully. You can now upload fresh CSV files.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to reset data: " + e.getMessage());
        }
        return "redirect:/";
    }

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
        model.addAttribute("availableStudents", getUnassignedStudents());
        return "group-manager";
    }

    @GetMapping({"/group/preview", "/group/{groupId}/preview-allocation"})
    public String previewAllocation(@PathVariable(value = "groupId", required = false) Double pathGroupId,
                                     @RequestParam(value = "id", required = false) Double idParam,
                                     Model model) {
        double groupId = (idParam != null) ? idParam : ((pathGroupId != null) ? pathGroupId : 0.0);
        List<Course> courses = courseRepository.findByGroupId(groupId);
        List<AllocationPreviewDto> previewList = allocationService.previewGroupAllocation(groupId);
        model.addAttribute("groupId", groupId);
        model.addAttribute("courses", courses);
        model.addAttribute("previewList", previewList);
        model.addAttribute("previewResults", previewList);
        model.addAttribute("availableStudents", getUnassignedStudents());
        return "group-manager";
    }

    @PostMapping("/group/update-form")
    public String updateForm(@RequestParam("id") double groupId, 
                             @RequestParam("formId") String formId, 
                             RedirectAttributes redirectAttributes) {
        try {
            String result = googleFormsService.updateForm(groupId, formId);
            redirectAttributes.addFlashAttribute("message", result);
        } catch (Throwable t) {
            redirectAttributes.addFlashAttribute("error", "Error syncing Google Form: " + t.getMessage());
        }
        return "redirect:/group?id=" + groupId;
    }

    @PostMapping("/group/send-preference-emails")
    public String sendPreferenceEmails(@RequestParam("id") double groupId, 
                                       @RequestParam("term") String term,
                                       @RequestParam(value = "deadline", required = false, defaultValue = "") String deadline,
                                       @RequestParam(value = "formLink", required = false, defaultValue = "") String formLink,
                                       @RequestParam(value = "customNote", required = false) String customNote,
                                       RedirectAttributes redirectAttributes) {
        try {
            String result;
            if (customNote != null && !customNote.trim().isEmpty()) {
                result = emailService.sendPreferenceEmails(groupId, term, deadline, formLink, customNote);
            } else {
                result = emailService.sendPreferenceEmails(groupId, term, deadline, formLink);
            }
            redirectAttributes.addFlashAttribute("message", result);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error sending preference emails: " + e.getMessage());
        }
        return "redirect:/group?id=" + groupId;
    }

    @PostMapping("/group/allocate")
    public String runAllocation(@RequestParam("id") double groupId, RedirectAttributes redirectAttributes) {
        try {
            allocationService.allocateGroup(groupId);
            redirectAttributes.addFlashAttribute("message", "TA Allocation completed for Group " + groupId + ".");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error running allocation: " + e.getMessage());
        }
        return "redirect:/group?id=" + groupId;
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(Long.class, "courseId", new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                if (text == null || text.trim().isEmpty()) {
                    setValue(null);
                    return;
                }
                try {
                    setValue(Long.parseLong(text.trim()));
                } catch (NumberFormatException e) {
                    String digits = text.replaceAll("\\D+", "");
                    if (!digits.isEmpty()) {
                        try {
                            setValue(Long.parseLong(digits));
                            return;
                        } catch (NumberFormatException ignored) {}
                    }
                    setValue((long) Math.abs(text.hashCode()));
                }
            }
        });
    }

    @PostMapping({"/group/unassign", "/group/{groupId}/unassign"})
    public String unassignTa(@PathVariable(value = "groupId", required = false) Double pathGroupId,
                             @RequestParam(value = "id", required = false) Double idParam,
                             @RequestParam(value = "courseNo", required = false) String courseNo,
                             @RequestParam(value = "courseId", required = false) Long courseId,
                             @RequestParam("slot") int slot,
                             RedirectAttributes redirectAttributes) {
        double groupId = (idParam != null) ? idParam : ((pathGroupId != null) ? pathGroupId : 0.0);
        try {
            if (courseNo != null && !courseNo.trim().isEmpty()) {
                allocationService.unassignTa(courseNo.trim(), slot);
            } else if (courseId != null) {
                allocationService.unassignTa(courseId, slot);
            } else {
                throw new IllegalArgumentException("Course identifier must be provided");
            }
            redirectAttributes.addFlashAttribute("message", "TA unassigned successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error unassigning TA: " + e.getMessage());
        }
        return "redirect:/group?id=" + groupId;
    }

    @PostMapping("/group/send-confirmation-emails")
    public String sendConfirmationEmails(@RequestParam("id") double groupId,
                                         @RequestParam("term") String term,
                                         @RequestParam(value = "customInstructions", required = false) String customInstructions,
                                         RedirectAttributes redirectAttributes) {
        try {
            String result;
            if (customInstructions != null && !customInstructions.trim().isEmpty()) {
                result = emailService.sendConfirmationEmails(groupId, term, customInstructions);
            } else {
                result = emailService.sendConfirmationEmails(groupId, term);
            }
            redirectAttributes.addFlashAttribute("message", result);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error sending confirmation emails: " + e.getMessage());
        }
        return "redirect:/group?id=" + groupId;
    }

    @PostMapping({"/group/override-ta", "/group/{groupId}/override-ta"})
    public String overrideTa(@PathVariable(value = "groupId", required = false) Double pathGroupId,
                             @RequestParam(value = "id", required = false) Double idParam,
                             @RequestParam(value = "courseNo", required = false) String courseNo,
                             @RequestParam(value = "courseId", required = false) Long courseId,
                             @RequestParam("slot") int slot,
                             @RequestParam("newRollNo") String newRollNo,
                             RedirectAttributes redirectAttributes) {
        double groupId = (idParam != null) ? idParam : ((pathGroupId != null) ? pathGroupId : 0.0);
        try {
            if (courseNo != null && !courseNo.trim().isEmpty()) {
                allocationService.overrideAssignment(courseNo.trim(), slot, newRollNo);
            } else if (courseId != null) {
                allocationService.overrideAssignment(courseId, slot, newRollNo);
            } else {
                throw new IllegalArgumentException("Course identifier must be provided");
            }
            redirectAttributes.addFlashAttribute("message", "TA assignment overridden successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error overriding TA assignment: " + e.getMessage());
        }
        return "redirect:/group?id=" + groupId;
    }

    @PostMapping({"/group/rollback", "/group/{groupId}/rollback-allocation"})
    public String rollbackAllocation(@PathVariable(value = "groupId", required = false) Double pathGroupId,
                                     @RequestParam(value = "id", required = false) Double idParam,
                                     RedirectAttributes redirectAttributes) {
        double groupId = (idParam != null) ? idParam : ((pathGroupId != null) ? pathGroupId : 0.0);
        try {
            int count = allocationService.rollbackGroupAllocations(groupId);
            redirectAttributes.addFlashAttribute("message", "Rolled back allocations for Group " + groupId + ". Released " + count + " TA(s).");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error rolling back allocations: " + e.getMessage());
        }
        return "redirect:/group?id=" + groupId;
    }

    @PostMapping("/group/{groupId}/send-pref-emails")
    public String sendPrefEmails(@PathVariable("groupId") double groupId,
                                 @RequestParam(value = "formUrl", required = false, defaultValue = "") String formUrl,
                                 @RequestParam(value = "deadlineDate", required = false, defaultValue = "") String deadlineDate,
                                 @RequestParam(value = "customNote", required = false, defaultValue = "") String customNote,
                                 @RequestParam(value = "term", required = false, defaultValue = "Coming Semester") String term,
                                 @RequestParam(value = "deadline", required = false) String deadline,
                                 @RequestParam(value = "formLink", required = false) String formLink,
                                 RedirectAttributes redirectAttributes) {
        try {
            String effectiveFormUrl = (formUrl != null && !formUrl.trim().isEmpty()) ? formUrl : formLink;
            String effectiveDeadline = (deadlineDate != null && !deadlineDate.trim().isEmpty()) ? deadlineDate : deadline;
            if (effectiveFormUrl == null) effectiveFormUrl = "";
            if (effectiveDeadline == null) effectiveDeadline = "";
            String result = emailService.sendPreferenceEmails(groupId, term, effectiveDeadline, effectiveFormUrl, customNote);
            redirectAttributes.addFlashAttribute("message", result);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error sending preference emails: " + e.getMessage());
        }
        return "redirect:/group?id=" + groupId;
    }

    @PostMapping("/group/{groupId}/send-confirm-emails")
    public String sendConfirmEmails(@PathVariable("groupId") double groupId,
                                    @RequestParam(value = "semesterHeading", required = false, defaultValue = "") String semesterHeading,
                                    @RequestParam(value = "customInstructions", required = false, defaultValue = "") String customInstructions,
                                    @RequestParam(value = "term", required = false) String term,
                                    RedirectAttributes redirectAttributes) {
        try {
            String effectiveTerm = (semesterHeading != null && !semesterHeading.trim().isEmpty()) ? semesterHeading : term;
            if (effectiveTerm == null || effectiveTerm.trim().isEmpty()) effectiveTerm = "Coming Semester";
            String result = emailService.sendConfirmationEmails(groupId, effectiveTerm, customInstructions);
            redirectAttributes.addFlashAttribute("message", result);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error sending confirmation emails: " + e.getMessage());
        }
        return "redirect:/group?id=" + groupId;
    }

    @GetMapping("/export/results")
    public void exportResults(@RequestParam(value = "groupId", required = false) Double groupId,
                              HttpServletResponse response) throws Exception {
        response.setContentType("text/csv");
        String filename = (groupId != null) ? "ta_allocations_group_" + groupId + ".csv" : "ta_allocations_all.csv";
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        dataUploadService.exportAllocationsCsv(groupId, response.getWriter());
    }

    private List<Student> getUnassignedStudents() {
        List<Course> allCourses = courseRepository.findAll();
        java.util.Set<String> assignedRolls = new java.util.HashSet<>();
        if (allCourses != null) {
            for (Course c : allCourses) {
                if (c != null) {
                    addRollIfAssigned(assignedRolls, c.getAssignedTa1());
                    addRollIfAssigned(assignedRolls, c.getAssignedTa2());
                }
            }
        }
        List<Student> availableInDb = studentRepository.findByIsAvailableTrue();
        if (availableInDb == null) return Collections.emptyList();
        return availableInDb.stream()
                .filter(s -> s.getRollNo() != null && !assignedRolls.contains(s.getRollNo().trim().toUpperCase()))
                .collect(java.util.stream.Collectors.toList());
    }

    private void addRollIfAssigned(java.util.Set<String> assignedRolls, String val) {
        if (val == null || val.trim().isEmpty() || DataUploadService.isInvalidPlaceholder(val)) return;
        String roll = val.trim();
        if (roll.contains(":")) roll = roll.split(":", 2)[0].trim();
        if (!DataUploadService.isInvalidPlaceholder(roll)) {
            assignedRolls.add(roll.toUpperCase());
        }
    }
}
