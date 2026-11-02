package com.iitm.aero.taallocator.controller;

import com.iitm.aero.taallocator.model.Course;
import com.iitm.aero.taallocator.model.PreferenceResponse;
import com.iitm.aero.taallocator.model.Student;
import com.iitm.aero.taallocator.repository.CourseRepository;
import com.iitm.aero.taallocator.repository.PreferenceRepository;
import com.iitm.aero.taallocator.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Controller
public class PreferenceFormController {

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private PreferenceRepository preferenceRepository;

    @GetMapping("/preferences/submit")
    public String showPreferenceForm(Model model) {
        List<Course> courses = courseRepository.findAll().stream()
                .sorted(Comparator.comparing(Course::getCourseNo, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        List<Student> students = studentRepository.findAll().stream()
                .sorted(Comparator.comparing(Student::getRollNo, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        model.addAttribute("courses", courses);
        model.addAttribute("students", students);
        return "submit-preference";
    }

    @PostMapping("/preferences/submit")
    public String processPreferenceForm(@RequestParam("courseNo") String courseNo,
                                        @RequestParam("facultyName") String facultyName,
                                        @RequestParam(value = "facultyEmail", required = false) String facultyEmail,
                                        @RequestParam(value = "pref1", required = false) String pref1,
                                        @RequestParam(value = "pref2", required = false) String pref2,
                                        @RequestParam(value = "pref3", required = false) String pref3,
                                        @RequestParam(value = "pref4", required = false) String pref4,
                                        RedirectAttributes redirectAttributes) {
        try {
            if (courseNo == null || courseNo.trim().isEmpty()) {
                throw new IllegalArgumentException("Please select a valid course.");
            }

            String cNo = courseNo.trim();
            List<PreferenceResponse> existing = preferenceRepository.findBySelectCourseIgnoreCase(cNo);
            PreferenceResponse pref = (!existing.isEmpty()) ? existing.get(existing.size() - 1) : new PreferenceResponse();

            pref.setSelectCourse(cNo);
            pref.setFacultyName(facultyName != null ? facultyName.trim() : "");
            pref.setEmailAddress(facultyEmail != null ? facultyEmail.trim() : "");
            pref.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            pref.setTaPreference1(pref1 != null ? pref1.trim() : "");
            pref.setTaPreference2(pref2 != null ? pref2.trim() : "");
            pref.setTaPreference3(pref3 != null ? pref3.trim() : "");
            pref.setTaPreference4(pref4 != null ? pref4.trim() : "");

            preferenceRepository.save(pref);
            redirectAttributes.addFlashAttribute("message", "Preferences for " + cNo + " successfully recorded in database!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to save preferences: " + e.getMessage());
        }
        return "redirect:/preferences/submit";
    }
}
