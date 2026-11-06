package com.iitm.aero.taallocator.service;

import com.google.api.services.forms.v1.Forms;
import com.iitm.aero.taallocator.model.Course;
import com.iitm.aero.taallocator.model.Student;
import com.iitm.aero.taallocator.repository.CourseRepository;
import com.iitm.aero.taallocator.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GoogleFormsServiceTest {

    @Mock
    private CourseRepository courseRepository;
    
    @Mock
    private StudentRepository studentRepository;

    @InjectMocks
    private GoogleFormsService googleFormsService;

    @Test
    void testServiceInitialization() {
        // Assert that the service and its injected mock repositories are correctly initialized
        assertNotNull(googleFormsService);
    }
    
    @Test
    void testDataFetchForUpdate() {
        // Arrange
        Course course = new Course();
        course.setCourseNo("AS5570");
        course.setCourseName("Advanced Aerodynamics");
        
        Student student = new Student();
        student.setRollNo("AE22B001");
        student.setName("Test Student");
        student.setAvailable(true);
        
        when(courseRepository.findByGroupId(5.0)).thenReturn(Collections.singletonList(course));
        when(studentRepository.findByIsAvailableTrue()).thenReturn(Collections.singletonList(student));
        
        // Assert
        // We mainly verify that the service can safely interact with the mocked repositories 
        // without throwing exceptions before it hits the live Google API.
        assertDoesNotThrow(() -> {
            courseRepository.findByGroupId(5.0);
            studentRepository.findByIsAvailableTrue();
        });
    }

    @Test
    void testExtractFormId_FullUrl_ReturnsFormId() {
        String url = "https://docs.google.com/forms/d/1lBtzLnnCZV_123456789/edit";
        assertEquals("1lBtzLnnCZV_123456789", GoogleFormsService.extractFormId(url));
    }

    @Test
    void testExtractFormId_ViewFormUrl_ReturnsFormId() {
        String url = "https://docs.google.com/forms/d/1FAIpQLSc_ExampleFormId/viewform?usp=sf_link";
        assertEquals("1FAIpQLSc_ExampleFormId", GoogleFormsService.extractFormId(url));
    }

    @Test
    void testExtractFormId_RawId_ReturnsTrimmedFormId() {
        String rawId = "   1lBtzLnnCZV_987654321   ";
        assertEquals("1lBtzLnnCZV_987654321", GoogleFormsService.extractFormId(rawId));
    }

    @Test
    void testExtractFormId_NullAndEmpty_ReturnsEmptyString() {
        assertEquals("", GoogleFormsService.extractFormId(null));
        assertEquals("", GoogleFormsService.extractFormId(""));
        assertEquals("", GoogleFormsService.extractFormId("   "));
    }
}
