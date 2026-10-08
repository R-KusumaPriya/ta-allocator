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
    private Student student3;
    private Student student4;

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

        student3 = new Student();
        student3.setRollNo("AE22B003");
        student3.setAvailable(true);

        student4 = new Student();
        student4.setRollNo("AE22B004");
        student4.setAvailable(true);
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
    void testAllocateGroup_MaxTwoTAsEnforced() {
        // Arrange: Professor gives 4 valid choices, all 4 are available
        pref.setTaPreference1("AE22B001: Student One");
        pref.setTaPreference2("AE22B002: Student Two");
        pref.setTaPreference3("AE22B003: Student Three");
        pref.setTaPreference4("AE22B004: Student Four");

        when(courseRepository.findByGroupId(5.0)).thenReturn(Collections.singletonList(course));
        when(preferenceRepository.findAll()).thenReturn(Collections.singletonList(pref));
        when(studentRepository.findAll()).thenReturn(Arrays.asList(student1, student2, student3, student4));

        // Act
        allocationService.allocateGroup(5.0);

        // Assert
        assertEquals("AE22B001", course.getTa1());
        assertEquals("AE22B002", course.getTa2());
        assertFalse(student1.isAvailable());
        assertFalse(student2.isAvailable());
        assertTrue(student3.isAvailable(), "Student 3 should remain available (capped at 2 TAs)");
        assertTrue(student4.isAvailable(), "Student 4 should remain available (capped at 2 TAs)");
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
        // Arrange: Choice 1 is unavailable, Choice 2 is available
        pref.setTaPreference1("AE22B001: Student One");
        pref.setTaPreference2("AE22B002: Student Two");
        student1.setAvailable(false); // Already taken by another course

        when(courseRepository.findByGroupId(5.0)).thenReturn(Collections.singletonList(course));
        when(preferenceRepository.findAll()).thenReturn(Collections.singletonList(pref));
        when(studentRepository.findAll()).thenReturn(Arrays.asList(student1, student2));
        
        // Act
        allocationService.allocateGroup(5.0);

        // Assert
        assertEquals("AE22B002", course.getTa1(), "Should fall back to student 2 because student 1 was unavailable");
        assertNull(course.getTa2());
        verify(studentRepository, times(1)).save(student2);
    }

    @Test
    void testAllocateGroup_NoPreferenceOptionSkipped() {
        // Arrange: Choice 1 is "No preference. You choose for me.", Choice 2 is a valid student
        pref.setTaPreference1("No preference. You choose for me.");
        pref.setTaPreference2("AE22B001: Student One");

        when(courseRepository.findByGroupId(5.0)).thenReturn(Collections.singletonList(course));
        when(preferenceRepository.findAll()).thenReturn(Collections.singletonList(pref));
        when(studentRepository.findAll()).thenReturn(Collections.singletonList(student1));

        // Act
        allocationService.allocateGroup(5.0);

        // Assert
        assertEquals("AE22B001", course.getTa1());
        assertFalse(student1.isAvailable());
    }

    @Test
    void testAllocateGroup_NoPreferencesSubmittedForCourse() {
        // Arrange: Course exists, but no professor preferences submitted
        when(courseRepository.findByGroupId(5.0)).thenReturn(Collections.singletonList(course));
        when(preferenceRepository.findAll()).thenReturn(Collections.emptyList());

        // Act
        allocationService.allocateGroup(5.0);

        // Assert: Course untouched, no students saved
        assertNull(course.getTa1());
        assertNull(course.getTa2());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void testAllocateGroup_MultiplePreferencesTakesLatest() {
        // Arrange: Professor submitted two form responses for the same course; latest should take effect
        PreferenceResponse olderPref = new PreferenceResponse();
        olderPref.setSelectCourse("AS5570");
        olderPref.setTaPreference1("AE22B001: Student One");

        PreferenceResponse latestPref = new PreferenceResponse();
        latestPref.setSelectCourse("AS5570");
        latestPref.setTaPreference1("AE22B002: Student Two");

        when(courseRepository.findByGroupId(5.0)).thenReturn(Collections.singletonList(course));
        when(preferenceRepository.findAll()).thenReturn(Arrays.asList(olderPref, latestPref));
        when(studentRepository.findAll()).thenReturn(Arrays.asList(student1, student2));

        // Act
        allocationService.allocateGroup(5.0);

        // Assert: Should assign AE22B002 from the latest response
        assertEquals("AE22B002", course.getTa1());
        assertFalse(student2.isAvailable());
        assertTrue(student1.isAvailable(), "Older preference student should remain untouched");
    }

    @Test
    void testAllocateGroup_StudentNotFoundInDatabase() {
        // Arrange: Roll number in preference does not exist in StudentRepository
        pref.setTaPreference1("AE99B999: Ghost Student");
        pref.setTaPreference2("AE22B001: Student One");

        when(courseRepository.findByGroupId(5.0)).thenReturn(Collections.singletonList(course));
        when(preferenceRepository.findAll()).thenReturn(Collections.singletonList(pref));
        when(studentRepository.findAll()).thenReturn(Collections.singletonList(student1));

        // Act
        allocationService.allocateGroup(5.0);

        // Assert: Skips ghost student and allocates student1 as ta1
        assertEquals("AE22B001", course.getTa1());
        verify(studentRepository, times(1)).save(student1);
    }

    @Test
    void testAllocateGroup_RawRollNumberWithoutColon() {
        // Arrange: Professor/form submitted just the 8-character roll number without colon
        pref.setTaPreference1("AE22B001");

        when(courseRepository.findByGroupId(5.0)).thenReturn(Collections.singletonList(course));
        when(preferenceRepository.findAll()).thenReturn(Collections.singletonList(pref));
        when(studentRepository.findAll()).thenReturn(Collections.singletonList(student1));

        // Act
        allocationService.allocateGroup(5.0);

        // Assert
        assertEquals("AE22B001", course.getTa1());
        assertFalse(student1.isAvailable());
    }

    @Test
    void testAllocateGroup_MalformedShortPreferenceString() {
        // Arrange: Short invalid preference string
        pref.setTaPreference1("xyz");

        when(courseRepository.findByGroupId(5.0)).thenReturn(Collections.singletonList(course));
        when(preferenceRepository.findAll()).thenReturn(Collections.singletonList(pref));

        // Act & Assert (Should not throw exception)
        assertDoesNotThrow(() -> allocationService.allocateGroup(5.0));
        assertNull(course.getTa1());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void testAllocateGroup_CaseInsensitiveCourseMatching() {
        // Arrange: Course code is "AS5570", preference has "  as5570 "
        pref.setSelectCourse("  as5570 ");
        pref.setTaPreference1("AE22B001: Student One");

        when(courseRepository.findByGroupId(5.0)).thenReturn(Collections.singletonList(course));
        when(preferenceRepository.findAll()).thenReturn(Collections.singletonList(pref));
        when(studentRepository.findAll()).thenReturn(Collections.singletonList(student1));

        // Act
        allocationService.allocateGroup(5.0);

        // Assert
        assertEquals("AE22B001", course.getTa1());
        assertFalse(student1.isAvailable());
    }
}
