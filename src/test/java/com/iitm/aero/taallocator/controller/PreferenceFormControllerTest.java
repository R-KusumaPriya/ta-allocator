package com.iitm.aero.taallocator.controller;

import com.iitm.aero.taallocator.model.PreferenceResponse;
import com.iitm.aero.taallocator.repository.CourseRepository;
import com.iitm.aero.taallocator.repository.PreferenceRepository;
import com.iitm.aero.taallocator.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
public class PreferenceFormControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PreferenceRepository preferenceRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private StudentRepository studentRepository;

    @InjectMocks
    private PreferenceFormController preferenceFormController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(preferenceFormController).build();
    }

    @Test
    void testShowPreferenceForm() throws Exception {
        when(courseRepository.findAll()).thenReturn(Collections.emptyList());
        when(studentRepository.findByIsAvailableTrue()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/preferences/submit"))
                .andExpect(status().isOk())
                .andExpect(view().name("preference-form"))
                .andExpect(model().attributeExists("courses"))
                .andExpect(model().attributeExists("students"));
    }

    @Test
    void testProcessPreferenceForm_UpdatesExistingPreference() throws Exception {
        PreferenceResponse existing = new PreferenceResponse();
        existing.setId(42L);
        existing.setSelectCourse("AS5100");
        existing.setFacultyName("Old Faculty");
        existing.setTaPreference1("AE22B001");

        when(preferenceRepository.findBySelectCourseIgnoreCase("AS5100")).thenReturn(Optional.of(existing));

        mockMvc.perform(post("/preferences/submit")
                        .param("courseNo", "AS5100")
                        .param("facultyName", "Prof. Murthy")
                        .param("email", "mhsn@iitm.ac.in")
                        .param("preference1", "AE22B109")
                        .param("preference2", "AE22B110")
                        .param("preference3", "AE25S005")
                        .param("preference4", "AE22B010"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/preferences/submit"))
                .andExpect(flash().attributeExists("message"));

        ArgumentCaptor<PreferenceResponse> captor = ArgumentCaptor.forClass(PreferenceResponse.class);
        verify(preferenceRepository, times(1)).save(captor.capture());

        PreferenceResponse saved = captor.getValue();
        assertEquals(42L, saved.getId(), "Must update existing entity ID without duplicating");
        assertEquals("AS5100", saved.getSelectCourse());
        assertEquals("Prof. Murthy", saved.getFacultyName());
        assertEquals("mhsn@iitm.ac.in", saved.getEmailAddress());
        assertEquals("AE22B109", saved.getTaPreference1());
        assertEquals("AE22B110", saved.getTaPreference2());
        assertEquals("AE25S005", saved.getTaPreference3());
        assertEquals("AE22B010", saved.getTaPreference4());
    }

    @Test
    void testProcessPreferenceForm_BlankCourseNo_FailsGracefully() throws Exception {
        mockMvc.perform(post("/preferences/submit")
                        .param("courseNo", "   ")
                        .param("facultyName", "Prof. Unknown"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/preferences/submit"))
                .andExpect(flash().attribute("error", "Course number is required."));

        verify(preferenceRepository, never()).save(any());
    }

    @Test
    void testProcessPreferenceForm_NewPreference_Success() throws Exception {
        when(preferenceRepository.findBySelectCourseIgnoreCase("AS6000")).thenReturn(Optional.empty());

        mockMvc.perform(post("/preferences/submit")
                        .param("courseNo", "AS6000")
                        .param("facultyName", "Prof. Bharath")
                        .param("email", "bharath@iitm.ac.in")
                        .param("preference1", "AE25M021"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/preferences/submit"))
                .andExpect(flash().attributeExists("message"));

        ArgumentCaptor<PreferenceResponse> captor = ArgumentCaptor.forClass(PreferenceResponse.class);
        verify(preferenceRepository, times(1)).save(captor.capture());

        PreferenceResponse saved = captor.getValue();
        assertEquals("AS6000", saved.getSelectCourse());
        assertEquals("Prof. Bharath", saved.getFacultyName());
        assertEquals("AE25M021", saved.getTaPreference1());
    }
}

