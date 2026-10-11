package com.iitm.aero.taallocator.controller;

import com.iitm.aero.taallocator.model.Course;
import com.iitm.aero.taallocator.repository.CourseRepository;
import com.iitm.aero.taallocator.repository.StudentRepository;
import com.iitm.aero.taallocator.service.AllocationService;
import com.iitm.aero.taallocator.service.DataUploadService;
import com.iitm.aero.taallocator.service.EmailService;
import com.iitm.aero.taallocator.service.GoogleFormsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
public class AdminControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DataUploadService dataUploadService;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private AllocationService allocationService;
    @Mock
    private EmailService emailService;
    @Mock
    private GoogleFormsService googleFormsService;

    @InjectMocks
    private AdminController adminController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminController).build();
    }

    @Test
    void testIndexDashboard() throws Exception {
        Course course = new Course();
        course.setGroupId(0.0);
        course.setCourseNo("AS6000");

        when(courseRepository.count()).thenReturn(5L);
        when(studentRepository.count()).thenReturn(10L);
        when(studentRepository.findByIsAvailableTrue()).thenReturn(Collections.emptyList());
        when(courseRepository.findAll()).thenReturn(Collections.singletonList(course));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attributeExists("courseCount"))
                .andExpect(model().attributeExists("studentCount"))
                .andExpect(model().attributeExists("groupIds"));
    }

    @Test
    void testUploadCourses_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "courses.csv", "text/csv", "dummy".getBytes());

        mockMvc.perform(multipart("/upload/courses").file(file))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("message"));

        verify(dataUploadService, times(1)).parseAndSaveCourses(any());
    }

    @Test
    void testUploadCourses_ExceptionHandledGracefully() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "courses.csv", "text/csv", "corrupt".getBytes());
        doThrow(new RuntimeException("Malformed CSV line")).when(dataUploadService).parseAndSaveCourses(any());

        mockMvc.perform(multipart("/upload/courses").file(file))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void testManageGroup() throws Exception {
        when(courseRepository.findByGroupId(1.0)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/group").param("id", "1.0"))
                .andExpect(status().isOk())
                .andExpect(view().name("group-manager"))
                .andExpect(model().attribute("groupId", 1.0))
                .andExpect(model().attributeExists("courses"));
    }

    @Test
    void testUpdateForm() throws Exception {
        when(googleFormsService.updateForm(1.0, "form123")).thenReturn("Form updated successfully");

        mockMvc.perform(post("/group/update-form")
                        .param("id", "1.0")
                        .param("formId", "form123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/group?id=1.0"))
                .andExpect(flash().attribute("message", "Form updated successfully"));

        verify(googleFormsService, times(1)).updateForm(1.0, "form123");
    }

    @Test
    void testRunAllocation() throws Exception {
        mockMvc.perform(post("/group/allocate").param("id", "1.0"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/group?id=1.0"))
                .andExpect(flash().attributeExists("message"));

        verify(allocationService, times(1)).allocateGroup(1.0);
    }

    @Test
    void testSendPreferenceEmails() throws Exception {
        when(emailService.sendPreferenceEmails(eq(1.0), any(), any(), any()))
                .thenReturn("Emails sent successfully");

        mockMvc.perform(post("/group/send-preference-emails")
                        .param("id", "1.0")
                        .param("term", "Jul-Nov 2026")
                        .param("deadline", "Aug 10")
                        .param("formLink", "https://form.link"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/group?id=1.0"))
                .andExpect(flash().attribute("message", "Emails sent successfully"));

        verify(emailService, times(1)).sendPreferenceEmails(1.0, "Jul-Nov 2026", "Aug 10", "https://form.link");
    }

    @Test
    void testSendConfirmationEmails() throws Exception {
        when(emailService.sendConfirmationEmails(eq(1.0), eq("Jul-Nov 2026")))
                .thenReturn("Confirmations dispatched");

        mockMvc.perform(post("/group/send-confirmation-emails")
                        .param("id", "1.0")
                        .param("term", "Jul-Nov 2026"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/group?id=1.0"))
                .andExpect(flash().attribute("message", "Confirmations dispatched"));

        verify(emailService, times(1)).sendConfirmationEmails(1.0, "Jul-Nov 2026");
    }

    @Test
    void testExportResults_AllCourses() throws Exception {
        mockMvc.perform(get("/export/results"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"ta_allocations_all.csv\""));

        verify(dataUploadService, times(1)).exportAllocationsCsv(isNull(), any());
    }

    @Test
    void testExportResults_SpecificGroup() throws Exception {
        mockMvc.perform(get("/export/results").param("groupId", "2.0"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"ta_allocations_group_2.0.csv\""));

        verify(dataUploadService, times(1)).exportAllocationsCsv(eq(2.0), any());
    }
}
