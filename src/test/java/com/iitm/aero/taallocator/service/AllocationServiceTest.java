package com.iitm.aero.taallocator.service;

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

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AllocationServiceTest {

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private PreferenceRepository preferenceRepository;

    @InjectMocks
    private AllocationService allocationService;

    private Course course;
    private PreferenceResponse pref;
    private Student student1;
    private Student student2;

    @BeforeEach
    void setUp() {
        course = new Course();
        course.setCourseNo("AS5570");
        course.setGroupId(5.0);

        pref = new PreferenceResponse();
        pref.setSelectCourse("AS5570");
        pref.setFacultyName("Prof. Smith");

        student1 = new Student();
        student1.setRollNo("AE22B001");
        student1.setAvailable(true);

        student2 = new Student();
        student2.setRollNo("AE22B002");
        student2.setAvailable(true);
    }

    @Test
    void testAllocateGroup_SuccessfulAllocation() {
        // Arrange
        pref.setTaPreference1("AE22B001: Student One");
        pref.setTaPreference2("AE22B002: Student Two");

        when(courseRepository.findByGroupId(5.0)).thenReturn(Collections.singletonList(course));
        when(preferenceRepository.findAll()).thenReturn(Collections.singletonList(pref));
        when(studentRepository.findAll()).thenReturn(Arrays.asList(student1, student2));
        
        // Act
        allocationService.allocateGroup(5.0);

        // Assert
        assertEquals("AE22B001", course.getTa1());
        assertEquals("AE22B002", course.getTa2());
        assertFalse(student1.isAvailable(), "Student 1 should be marked unavailable after allocation");
        assertFalse(student2.isAvailable(), "Student 2 should be marked unavailable after allocation");
        
        verify(courseRepository, times(1)).save(course);
        verify(studentRepository, times(2)).save(any(Student.class));
    }

    @Test
    void testAllocateGroup_DoNotWantTAs() {
        // Arrange
        pref.setTaPreference1("I do not want TAs.");

        when(courseRepository.findByGroupId(5.0)).thenReturn(Collections.singletonList(course));
        when(preferenceRepository.findAll()).thenReturn(Collections.singletonList(pref));
        
        // Act
        allocationService.allocateGroup(5.0);

        // Assert
        assertNull(course.getTa1(), "TA1 should be null if professor does not want TAs");
        assertNull(course.getTa2(), "TA2 should be null if professor does not want TAs");
        verify(courseRepository, never()).save(any());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void testAllocateGroup_StudentUnavailable() {
        // Arrange
        pref.setTaPreference1("AE22B001: Student One");
        student1.setAvailable(false); // Simulate student already assigned to another course

        when(courseRepository.findByGroupId(5.0)).thenReturn(Collections.singletonList(course));
        when(preferenceRepository.findAll()).thenReturn(Collections.singletonList(pref));
        when(studentRepository.findAll()).thenReturn(Collections.singletonList(student1));
        
        // Act
        allocationService.allocateGroup(5.0);

        // Assert
        assertNull(course.getTa1(), "Should not assign an unavailable student");
        verify(studentRepository, never()).save(any());
    }
}
