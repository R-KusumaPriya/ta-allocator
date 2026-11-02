package com.iitm.aero.taallocator.controller;

import com.iitm.aero.taallocator.model.Course;
import com.iitm.aero.taallocator.model.PreferenceResponse;
import com.iitm.aero.taallocator.model.Student;
import com.iitm.aero.taallocator.repository.CourseRepository;
import com.iitm.aero.taallocator.repository.PreferenceRepository;
import com.iitm.aero.taallocator.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
public class PreferenceFormControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private PreferenceRepository preferenceRepository;

    @InjectMocks
    private PreferenceFormController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testShowPreferenceForm_ReturnsViewWithModel() throws Exception {
        Course course = new Course();
        course.setCourseNo("AS2100");
        Student student = new Student();
        student.setRollNo("AE22B001");

        when(courseRepository.findAll()).thenReturn(Collections.singletonList(course));
        when(studentRepository.findAll()).thenReturn(Collections.singletonList(student));

        mockMvc.perform(get("/preferences/submit"))
                .andExpect(status().isOk())
                .andExpect(view().name("submit-preference"))
                .andExpect(model().attributeExists("courses"))
                .andExpect(model().attributeExists("students"));
    }

    @Test
    void testProcessPreferenceForm_SavesPreferenceAndRedirects() throws Exception {
        when(preferenceRepository.findBySelectCourseIgnoreCase("AS2100")).thenReturn(Collections.emptyList());

        mockMvc.perform(post("/preferences/submit")
                        .param("courseNo", "AS2100")
                        .param("facultyName", "Dr. Bharath Govindarajan")
                        .param("facultyEmail", "bharath@aerospace.iitm.ac.in")
                        .param("pref1", "AE22B001: Student One")
                        .param("pref2", "AE22B002: Student Two")
                        .param("pref3", "No preference. You choose for me.")
                        .param("pref4", "I do not want TAs."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/preferences/submit"))
                .andExpect(flash().attributeExists("message"));

        verify(preferenceRepository, times(1)).save(any(PreferenceResponse.class));
    }
}
