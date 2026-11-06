package com.iitm.aero.taallocator.controller;

import com.iitm.aero.taallocator.dto.AllocationPreviewDto;
import com.iitm.aero.taallocator.model.Course;
import com.iitm.aero.taallocator.model.Student;
import com.iitm.aero.taallocator.repository.CourseRepository;
import com.iitm.aero.taallocator.repository.FacultyRepository;
import com.iitm.aero.taallocator.repository.PreferenceRepository;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    private FacultyRepository facultyRepository;
    @Mock
    private PreferenceRepository preferenceRepository;
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
    void testResetAllData_Success() throws Exception {
        mockMvc.perform(post("/admin/reset"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("message"));

        verify(preferenceRepository, times(1)).deleteAll();
        verify(courseRepository, times(1)).deleteAll();
        verify(studentRepository, times(1)).deleteAll();
        verify(facultyRepository, times(1)).deleteAll();
    }

    @Test
    void testResetAllData_ExceptionHandled() throws Exception {
        doThrow(new RuntimeException("Database error during reset")).when(preferenceRepository).deleteAll();

        mockMvc.perform(post("/admin/reset"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("error"));
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
    void testUploadStudents_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "students.csv", "text/csv", "dummy".getBytes());

        mockMvc.perform(multipart("/upload/students").file(file))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("message"));

        verify(dataUploadService, times(1)).parseAndSaveStudents(any());
    }

    @Test
    void testUploadStudents_Exception() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "students.csv", "text/csv", "corrupt".getBytes());
        doThrow(new RuntimeException("Parse error")).when(dataUploadService).parseAndSaveStudents(any());

        mockMvc.perform(multipart("/upload/students").file(file))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void testUploadFaculty_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "faculty.csv", "text/csv", "dummy".getBytes());

        mockMvc.perform(multipart("/upload/faculty").file(file))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("message"));

        verify(dataUploadService, times(1)).parseAndSaveFaculty(any());
    }

    @Test
    void testUploadFaculty_Exception() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "faculty.csv", "text/csv", "corrupt".getBytes());
        doThrow(new RuntimeException("Faculty CSV error")).when(dataUploadService).parseAndSaveFaculty(any());

        mockMvc.perform(multipart("/upload/faculty").file(file))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void testUploadPreferences_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "preferences.csv", "text/csv", "dummy".getBytes());

        mockMvc.perform(multipart("/upload/preferences").file(file))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attributeExists("message"));

        verify(dataUploadService, times(1)).parseAndSavePreferences(any());
    }

    @Test
    void testUploadPreferences_Exception() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "preferences.csv", "text/csv", "corrupt".getBytes());
        doThrow(new RuntimeException("Preferences CSV error")).when(dataUploadService).parseAndSavePreferences(any());

        mockMvc.perform(multipart("/upload/preferences").file(file))
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
    void testPreviewAllocation() throws Exception {
        List<Course> courses = Collections.singletonList(new Course());
        List<AllocationPreviewDto> previews = Collections.singletonList(
                new AllocationPreviewDto("AS5100", "Mini Project", "MURTHY", 1, "AE22B109", "RAJASEGARANE", "Rank 1", 10000.0)
        );

        when(courseRepository.findByGroupId(1.0)).thenReturn(courses);
        when(allocationService.previewGroupAllocation(1.0)).thenReturn(previews);
        when(studentRepository.findByIsAvailableTrue()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/group/preview").param("id", "1.0"))
                .andExpect(status().isOk())
                .andExpect(view().name("group-manager"))
                .andExpect(model().attribute("groupId", 1.0))
                .andExpect(model().attribute("courses", courses))
                .andExpect(model().attribute("previewResults", previews))
                .andExpect(model().attributeExists("availableStudents"));
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
    void testSendPreferenceEmails_WithCustomNote() throws Exception {
        when(emailService.sendPreferenceEmails(eq(1.0), eq("Jul-Nov 2026"), eq("Aug 10"), eq("https://form.link"), eq("Please reply asap")))
                .thenReturn("Emails with custom note dispatched");

        mockMvc.perform(post("/group/send-preference-emails")
                        .param("id", "1.0")
                        .param("term", "Jul-Nov 2026")
                        .param("deadline", "Aug 10")
                        .param("formLink", "https://form.link")
                        .param("customNote", "Please reply asap"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/group?id=1.0"))
                .andExpect(flash().attribute("message", "Emails with custom note dispatched"));

        verify(emailService, times(1)).sendPreferenceEmails(1.0, "Jul-Nov 2026", "Aug 10", "https://form.link", "Please reply asap");
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
    void testSendConfirmationEmails_WithCustomInstructions() throws Exception {
        when(emailService.sendConfirmationEmails(eq(1.0), eq("Jul-Nov 2026"), eq("Schedule kickoff meetings promptly")))
                .thenReturn("Confirmations with instructions dispatched");

        mockMvc.perform(post("/group/send-confirmation-emails")
                        .param("id", "1.0")
                        .param("term", "Jul-Nov 2026")
                        .param("customInstructions", "Schedule kickoff meetings promptly"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/group?id=1.0"))
                .andExpect(flash().attribute("message", "Confirmations with instructions dispatched"));

        verify(emailService, times(1)).sendConfirmationEmails(1.0, "Jul-Nov 2026", "Schedule kickoff meetings promptly");
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

    @Test
    void testUnassignTa_Success() throws Exception {
        mockMvc.perform(post("/group/unassign")
                        .param("id", "1.0")
                        .param("courseNo", "AS5100")
                        .param("slot", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/group?id=1.0"))
                .andExpect(flash().attribute("message", "TA unassigned successfully."));

        verify(allocationService, times(1)).unassignTa("AS5100", 1);
    }

    @Test
    void testUnassignTa_Exception() throws Exception {
        doThrow(new RuntimeException("Unassign error")).when(allocationService).unassignTa(anyString(), anyInt());

        mockMvc.perform(post("/group/unassign")
                        .param("id", "1.0")
                        .param("courseNo", "AS5100")
                        .param("slot", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/group?id=1.0"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void testUnassignTa_ById() throws Exception {
        mockMvc.perform(post("/group/1.0/unassign")
                        .param("courseId", "1")
                        .param("slot", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/group?id=1.0"))
                .andExpect(flash().attribute("message", "TA unassigned successfully."));

        verify(allocationService, times(1)).unassignTa(1L, 1);
    }

    @Test
    void testOverrideTa_Success() throws Exception {
        mockMvc.perform(post("/group/override-ta")
                        .param("id", "1.0")
                        .param("courseNo", "AS5100")
                        .param("slot", "1")
                        .param("newRollNo", "AE22B003"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/group?id=1.0"))
                .andExpect(flash().attribute("message", "TA assignment overridden successfully."));

        verify(allocationService, times(1)).overrideAssignment("AS5100", 1, "AE22B003");
    }

    @Test
    void testOverrideTa_Exception() throws Exception {
        doThrow(new RuntimeException("Override failure")).when(allocationService).overrideAssignment(anyString(), anyInt(), anyString());

        mockMvc.perform(post("/group/override-ta")
                        .param("id", "1.0")
                        .param("courseNo", "AS5100")
                        .param("slot", "1")
                        .param("newRollNo", "AE22B003"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/group?id=1.0"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void testRollbackGroupAllocations_Success() throws Exception {
        when(allocationService.rollbackGroupAllocations(1.0)).thenReturn(3);

        mockMvc.perform(post("/group/rollback")
                        .param("id", "1.0"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/group?id=1.0"))
                .andExpect(flash().attribute("message", "Rolled back allocations for Group 1.0. Released 3 TA(s)."));

        verify(allocationService, times(1)).rollbackGroupAllocations(1.0);
    }

    @Test
    void testRollbackGroupAllocations_Exception() throws Exception {
        doThrow(new RuntimeException("Rollback error")).when(allocationService).rollbackGroupAllocations(1.0);

        mockMvc.perform(post("/group/rollback")
                        .param("id", "1.0"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/group?id=1.0"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void testGroupManager_FiltersOutStudentsAssignedInCourses() throws Exception {
        Course c1 = new Course();
        c1.setCourseNo("AS1300");
        c1.setTa1("AE22B001: John");
        c1.setTa2(null);
        c1.setGroupId(1.0);

        Student s1 = new Student();
        s1.setRollNo("AE22B001");
        s1.setName("John");
        s1.setAvailable(true); // marked available in student table but already assigned in course

        Student s2 = new Student();
        s2.setRollNo("AE22B002");
        s2.setName("Jane");
        s2.setAvailable(true);

        when(courseRepository.findByGroupId(1.0)).thenReturn(Collections.singletonList(c1));
        when(courseRepository.findAll()).thenReturn(Collections.singletonList(c1));
        when(studentRepository.findByIsAvailableTrue()).thenReturn(Arrays.asList(s1, s2));

        mockMvc.perform(get("/group").param("id", "1.0"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("availableStudents", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(model().attribute("availableStudents", org.hamcrest.Matchers.contains(s2)));
    }

    @Test
    void testUpdateForm_ThrowableCaught_SetsFlashError() throws Exception {
        when(googleFormsService.updateForm(1.0, "formErr")).thenThrow(new LinkageError("Native library issue"));

        mockMvc.perform(post("/group/update-form")
                        .param("id", "1.0")
                        .param("formId", "formErr"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/group?id=1.0"))
                .andExpect(flash().attribute("error", org.hamcrest.Matchers.containsString("Native library issue")));
    }
}
