package com.iitm.aero.taallocator.controller;

import com.iitm.aero.taallocator.model.PreferenceResponse;
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
import java.util.Optional;

@Controller
public class PreferenceFormController {

    @Autowired
    private PreferenceRepository preferenceRepository;

    @Autowired(required = false)
    private CourseRepository courseRepository;

    @Autowired(required = false)
    private StudentRepository studentRepository;

    @GetMapping("/preferences/submit")
    public String showPreferenceForm(Model model) {
        if (courseRepository != null) {
            model.addAttribute("courses", courseRepository.findAll());
        }
        if (studentRepository != null) {
            model.addAttribute("students", studentRepository.findByIsAvailableTrue());
        }
        return "preference-form";
    }

    @PostMapping("/preferences/submit")
    public String processPreferenceForm(@RequestParam(value = "courseNo", required = false) String courseNo,
                                        @RequestParam(value = "selectCourse", required = false) String selectCourse,
                                        @RequestParam(value = "facultyName", required = false, defaultValue = "") String facultyName,
                                        @RequestParam(value = "email", required = false, defaultValue = "") String email,
                                        @RequestParam(value = "emailAddress", required = false, defaultValue = "") String emailAddress,
                                        @RequestParam(value = "preference1", required = false, defaultValue = "") String preference1,
                                        @RequestParam(value = "preference2", required = false, defaultValue = "") String preference2,
                                        @RequestParam(value = "preference3", required = false, defaultValue = "") String preference3,
                                        @RequestParam(value = "preference4", required = false, defaultValue = "") String preference4,
                                        @RequestParam(value = "taPreference1", required = false, defaultValue = "") String taPreference1,
                                        @RequestParam(value = "taPreference2", required = false, defaultValue = "") String taPreference2,
                                        @RequestParam(value = "taPreference3", required = false, defaultValue = "") String taPreference3,
                                        @RequestParam(value = "taPreference4", required = false, defaultValue = "") String taPreference4,
                                        RedirectAttributes redirectAttributes) {
        String effectiveCourseNo = (courseNo != null && !courseNo.trim().isEmpty()) ? courseNo.trim() 
                : ((selectCourse != null && !selectCourse.trim().isEmpty()) ? selectCourse.trim() : "");
        
        if (effectiveCourseNo.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Course number is required.");
            return "redirect:/preferences/submit";
        }

        String effectiveEmail = !email.trim().isEmpty() ? email.trim() : emailAddress.trim();
        String p1 = !preference1.trim().isEmpty() ? preference1.trim() : taPreference1.trim();
        String p2 = !preference2.trim().isEmpty() ? preference2.trim() : taPreference2.trim();
        String p3 = !preference3.trim().isEmpty() ? preference3.trim() : taPreference3.trim();
        String p4 = !preference4.trim().isEmpty() ? preference4.trim() : taPreference4.trim();

        Optional<PreferenceResponse> existingOpt = preferenceRepository.findBySelectCourseIgnoreCase(effectiveCourseNo);
        PreferenceResponse pref = existingOpt.orElseGet(PreferenceResponse::new);

        pref.setSelectCourse(effectiveCourseNo);
        pref.setFacultyName(facultyName);
        pref.setEmailAddress(effectiveEmail);
        pref.setTaPreference1(p1);
        pref.setTaPreference2(p2);
        pref.setTaPreference3(p3);
        pref.setTaPreference4(p4);
        pref.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("M/d/yyyy HH:mm:ss")));

        preferenceRepository.save(pref);

        redirectAttributes.addFlashAttribute("message", "Preferences submitted successfully for course " + effectiveCourseNo);
        return "redirect:/preferences/submit";
    }
}

