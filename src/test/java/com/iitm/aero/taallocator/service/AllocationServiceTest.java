package com.iitm.aero.taallocator.service;

import com.iitm.aero.taallocator.dto.AllocationPreviewDto;
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
import java.util.List;
import java.util.Optional;

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

    @Test
    void testUnassignTa_ClearsSlotAndReturnsStudentAvailability() {
        // Arrange
        Long courseId = 1L;
        course.setId(courseId);
        course.setAssignedTa1("AE22B001: Student One");
        student1.setAvailable(false);
        student1.setCourse("AS5570");

        when(courseRepository.findById(String.valueOf(courseId))).thenReturn(Optional.of(course));
        when(courseRepository.findAll()).thenReturn(Collections.singletonList(course));
        when(studentRepository.findByRollNoIgnoreCase("AE22B001")).thenReturn(Optional.of(student1));

        // Act
        allocationService.unassignTa(courseId, 1);

        // Assert: a) A course with assignedTa1 has it cleared to null after unassignTa(courseId, 1)
        assertNull(course.getAssignedTa1(), "Course assignedTa1 should be null after unassignment");
        assertNull(course.getTa1(), "Course ta1 should also be null");

        // Assert: b) The unassigned student's isAvailable flag returns from false to true
        assertTrue(student1.isAvailable(), "Student isAvailable flag should return from false to true");

        verify(courseRepository, times(1)).save(course);
        verify(studentRepository, times(1)).save(student1);
    }

    @Test
    void testUnassignTa_Slot2Success() {
        // Arrange
        Long courseId = 2L;
        course.setId(courseId);
        course.setAssignedTa2("AE22B002: Student Two");
        student2.setAvailable(false);

        when(courseRepository.findById(String.valueOf(courseId))).thenReturn(Optional.of(course));
        when(courseRepository.findAll()).thenReturn(Collections.singletonList(course));
        when(studentRepository.findByRollNoIgnoreCase("AE22B002")).thenReturn(Optional.of(student2));

        // Act
        allocationService.unassignTa(courseId, 2);

        // Assert
        assertNull(course.getAssignedTa2());
        assertNull(course.getTa2());
        assertTrue(student2.isAvailable());
        verify(courseRepository, times(1)).save(course);
        verify(studentRepository, times(1)).save(student2);
    }

    @Test
    void testUnassignTa_AlreadyNullSlot_DoesNothing() {
        // Arrange
        Long courseId = 1L;
        course.setAssignedTa1(null);

        when(courseRepository.findById(String.valueOf(courseId))).thenReturn(Optional.of(course));

        // Act
        allocationService.unassignTa(courseId, 1);

        // Assert
        verify(courseRepository, never()).save(any());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void testUnassignTa_StudentAssignedToAnotherCourse_AvailabilityRemainsFalse() {
        // Arrange
        Long courseId = 1L;
        course.setId(courseId);
        course.setAssignedTa1("AE22B001: Student One");
        student1.setAvailable(false);

        Course otherCourse = new Course();
        otherCourse.setCourseNo("AS6000");
        otherCourse.setAssignedTa1("AE22B001: Student One");

        when(courseRepository.findById(String.valueOf(courseId))).thenReturn(Optional.of(course));
        when(courseRepository.findAll()).thenReturn(Arrays.asList(course, otherCourse));

        // Act
        allocationService.unassignTa(courseId, 1);

        // Assert
        assertNull(course.getAssignedTa1());
        assertFalse(student1.isAvailable(), "Student should remain unavailable because they are assigned to another course");
        verify(courseRepository, times(1)).save(course);
        verify(studentRepository, never()).save(any());
    }

    @Test
    void testUnassignTa_CourseNotFound_ThrowsIllegalArgumentException() {
        // Arrange
        Long nonExistentCourseId = 999L;
        when(courseRepository.findById(String.valueOf(nonExistentCourseId))).thenReturn(Optional.empty());
        when(courseRepository.findAll()).thenReturn(Collections.emptyList());

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> 
            allocationService.unassignTa(nonExistentCourseId, 1)
        );
        assertTrue(ex.getMessage().contains("Course not found"));
    }

    @Test
    void testPreviewDoesNotMutateDb() {
        // Arrange
        pref.setTaPreference1("AE22B001: Student One");
        pref.setTaPreference2("AE22B002: Student Two");

        when(courseRepository.findByGroupId(5.0)).thenReturn(Collections.singletonList(course));
        when(preferenceRepository.findAll()).thenReturn(Collections.singletonList(pref));
        when(studentRepository.findAll()).thenReturn(Arrays.asList(student1, student2));

        // Act
        List<AllocationPreviewDto> previews = allocationService.previewGroupAllocation(5.0);

        // Assert
        assertNotNull(previews);
        assertEquals(2, previews.size()); // 2 slots for the course
        assertEquals("AS5570", previews.get(0).getCourseNo());
        assertEquals("AE22B001", previews.get(0).getMatchedRollNo());
        assertEquals("AE22B002", previews.get(1).getMatchedRollNo());

        // Verify that neither course nor student repositories were mutated/saved
        verify(courseRepository, never()).save(any(Course.class));
        verify(studentRepository, never()).save(any(Student.class));
        assertTrue(student1.isAvailable(), "Student 1 should still be available in DB / memory");
        assertTrue(student2.isAvailable(), "Student 2 should still be available in DB / memory");
    }

    @Test
    void testOverrideAssignment() {
        // Arrange
        Long courseId = 1L;
        course.setId(courseId);
        course.setAssignedTa1("AE22B001: Student One [8.5] (B.Tech)");
        student1.setAvailable(false);

        student3.setName("Student Three");
        student3.setCgpa("9.1");
        student3.setProgram("M.Tech");
        student3.setAvailable(true);

        when(courseRepository.findById(String.valueOf(courseId))).thenReturn(Optional.of(course));
        when(courseRepository.findAll()).thenReturn(Collections.singletonList(course));
        when(studentRepository.findByRollNoIgnoreCase("AE22B001")).thenReturn(Optional.of(student1));
        when(studentRepository.findByRollNoIgnoreCase("AE22B003")).thenReturn(Optional.of(student3));

        // Act 1: Override slot 1 with student 3
        allocationService.overrideAssignment(courseId, 1, "AE22B003");

        // Assert 1
        assertTrue(student1.isAvailable(), "Previous student should be marked available");
        assertFalse(student3.isAvailable(), "New student should be marked unavailable");
        assertEquals("AE22B003: Student Three [9.1] (M.Tech)", course.getAssignedTa1());
        verify(courseRepository, times(1)).save(course);
        verify(studentRepository, times(1)).save(student1);
        verify(studentRepository, times(1)).save(student3);

        // Act 2: Override slot 1 with "NONE" to clear
        allocationService.overrideAssignment(courseId, 1, "NONE");

        // Assert 2
        assertNull(course.getAssignedTa1());
        assertTrue(student3.isAvailable(), "Student 3 should now be restored to available");
        verify(courseRepository, times(2)).save(course);
        verify(studentRepository, times(2)).save(student3);
    }

    @Test
    void testRollbackGroupAllocations() {
        // Arrange
        course.setAssignedTa1("AE22B001: Student One");
        course.setAssignedTa2("AE22B002: Student Two");
        student1.setAvailable(false);
        student2.setAvailable(false);

        when(courseRepository.findByGroupId(5.0)).thenReturn(Collections.singletonList(course));
        when(courseRepository.findAll()).thenReturn(Collections.singletonList(course));
        when(studentRepository.findByRollNoIgnoreCase("AE22B001")).thenReturn(Optional.of(student1));
        when(studentRepository.findByRollNoIgnoreCase("AE22B002")).thenReturn(Optional.of(student2));

        // Act
        int releasedCount = allocationService.rollbackGroupAllocations(5.0);

        // Assert
        assertEquals(2, releasedCount);
        assertNull(course.getAssignedTa1());
        assertNull(course.getAssignedTa2());
        assertTrue(student1.isAvailable(), "Student 1 should be released and available");
        assertTrue(student2.isAvailable(), "Student 2 should be released and available");
        verify(courseRepository, times(1)).save(course);
        verify(studentRepository, times(1)).save(student1);
        verify(studentRepository, times(1)).save(student2);
    }

    @Test
    void testUnassignTa_Slot2_Success() {
        Long courseId = 2L;
        course.setId(courseId);
        course.setAssignedTa2("AE22B002: Student Two");
        student2.setAvailable(false);
        student2.setCourse("AS5570");

        when(courseRepository.findById(String.valueOf(courseId))).thenReturn(Optional.of(course));
        when(courseRepository.findAll()).thenReturn(Collections.singletonList(course));
        when(studentRepository.findByRollNoIgnoreCase("AE22B002")).thenReturn(Optional.of(student2));

        allocationService.unassignTa(courseId, 2);

        assertNull(course.getAssignedTa2(), "Course assignedTa2 should be cleared");
        assertNull(course.getTa2(), "Course ta2 should be cleared");
        assertTrue(student2.isAvailable(), "Student should be marked available");
        assertNull(student2.getCourse(), "Student course field should be cleared");
        verify(courseRepository, times(1)).save(course);
        verify(studentRepository, times(1)).save(student2);
    }

    @Test
    void testUnassignTa_SlotAlreadyEmpty_DoesNothingGracefully() {
        Long courseId = 1L;
        course.setId(courseId);
        course.setTa1(null);
        course.setAssignedTa1(null);

        when(courseRepository.findById(String.valueOf(courseId))).thenReturn(Optional.of(course));

        assertDoesNotThrow(() -> allocationService.unassignTa(courseId, 1));

        verify(courseRepository, never()).save(any());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void testOverrideAssignment_CourseNotFound_ThrowsException() {
        Long nonExistentCourseId = 999L;
        when(courseRepository.findById(String.valueOf(nonExistentCourseId))).thenReturn(Optional.empty());
        when(courseRepository.findAll()).thenReturn(Collections.emptyList());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            allocationService.overrideAssignment(nonExistentCourseId, 1, "AE22B001")
        );
        assertTrue(ex.getMessage().contains("Course not found"));
    }

    @Test
    void testOverrideAssignment_StudentNotFound_ThrowsException() {
        Long courseId = 1L;
        course.setId(courseId);
        course.setAssignedTa1(null);

        when(courseRepository.findById(String.valueOf(courseId))).thenReturn(Optional.of(course));
        when(studentRepository.findByRollNoIgnoreCase("AE99B999")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            allocationService.overrideAssignment(courseId, 1, "AE99B999")
        );
        assertTrue(ex.getMessage().contains("Student not found"));
        verify(courseRepository, never()).save(any());
    }

    @Test
    void testOverrideAssignment_SlotAlreadyEmpty_AssignsNewStudentDirectly() {
        Long courseId = 1L;
        course.setId(courseId);
        course.setAssignedTa1(null);

        student3.setName("Student Three");
        student3.setCgpa("8.8");
        student3.setProgram("M.S");
        student3.setAvailable(true);

        when(courseRepository.findById(String.valueOf(courseId))).thenReturn(Optional.of(course));
        when(studentRepository.findByRollNoIgnoreCase("AE22B003")).thenReturn(Optional.of(student3));
        when(courseRepository.findAll()).thenReturn(Collections.singletonList(course));

        allocationService.overrideAssignment(courseId, 1, "AE22B003");

        assertFalse(student3.isAvailable(), "New student should be marked unavailable");
        assertEquals("AS5570", student3.getCourse());
        assertNotNull(course.getAssignedTa1());
        assertTrue(course.getAssignedTa1().startsWith("AE22B003"));
        verify(courseRepository, times(1)).save(course);
        verify(studentRepository, times(1)).save(student3);
    }

    @Test
    void testOverrideAssignment_StudentAlreadyAssignedElsewhere_FreesFromOldCourse() {
        Long courseAId = 1L;
        course.setId(courseAId);
        course.setCourseNo("AS5570");
        course.setAssignedTa1(null);

        Course courseB = new Course();
        courseB.setId(2L);
        courseB.setCourseNo("AS6000");
        courseB.setAssignedTa1("AE22B003: Student Three [8.8] (M.S)");

        student3.setName("Student Three");
        student3.setCgpa("8.8");
        student3.setProgram("M.S");
        student3.setAvailable(false);
        student3.setCourse("AS6000");

        when(courseRepository.findById(String.valueOf(courseAId))).thenReturn(Optional.of(course));
        when(studentRepository.findByRollNoIgnoreCase("AE22B003")).thenReturn(Optional.of(student3));
        when(courseRepository.findAll()).thenReturn(Arrays.asList(course, courseB));

        allocationService.overrideAssignment(courseAId, 1, "AE22B003");

        assertNull(courseB.getAssignedTa1(), "Course B Slot 1 must be cleared");
        assertNotNull(course.getAssignedTa1(), "Course A Slot 1 must be assigned");
        assertTrue(course.getAssignedTa1().startsWith("AE22B003"));
        assertFalse(student3.isAvailable(), "Student 3 should remain unavailable");
        assertEquals("AS5570", student3.getCourse(), "Student 3 course must now point to AS5570");

        verify(courseRepository, times(1)).save(courseB);
        verify(courseRepository, times(1)).save(course);
        verify(studentRepository, times(1)).save(student3);
    }

    @Test
    void testRollbackGroupAllocations_EmptyGroup_ReturnsZero() {
        when(courseRepository.findByGroupId(99.0)).thenReturn(Collections.emptyList());

        int released = allocationService.rollbackGroupAllocations(99.0);

        assertEquals(0, released);
        verify(courseRepository, never()).save(any());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void testAllocateGroup_EmptyCourseList_ReturnsEarly() {
        when(courseRepository.findByGroupId(99.0)).thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> allocationService.allocateGroup(99.0));

        verify(courseRepository, never()).save(any());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void testAllocateGroup_EmptyAvailableStudents_SavesUnassignedCourses() {
        pref.setTaPreference1("AE22B001: Student One");

        when(courseRepository.findByGroupId(5.0)).thenReturn(Collections.singletonList(course));
        when(preferenceRepository.findAll()).thenReturn(Collections.singletonList(pref));
        when(studentRepository.findAll()).thenReturn(Collections.emptyList());

        allocationService.allocateGroup(5.0);

        assertNull(course.getTa1(), "TA1 should remain unassigned");
        assertNull(course.getTa2(), "TA2 should remain unassigned");
        verify(courseRepository, times(1)).save(course);
        verify(studentRepository, never()).save(any());
    }

    @Test
    void testAllocateGroup_AllNoPreference_AllocatesBasedOnCgpa() {
        pref.setTaPreference1("No preference. You choose for me.");
        pref.setTaPreference2("No preference. You choose for me.");
        pref.setTaPreference3(null);
        pref.setTaPreference4(null);

        student1.setCgpa("9.5");
        student2.setCgpa("8.5");
        student3.setCgpa("6.0");

        when(courseRepository.findByGroupId(5.0)).thenReturn(Collections.singletonList(course));
        when(preferenceRepository.findAll()).thenReturn(Collections.singletonList(pref));
        when(studentRepository.findAll()).thenReturn(Arrays.asList(student1, student2, student3));

        allocationService.allocateGroup(5.0);

        List<String> assignedRolls = Arrays.asList(course.getTa1(), course.getTa2());
        assertTrue(assignedRolls.contains("AE22B001"), "Highest CGPA student should be allocated");
        assertTrue(assignedRolls.contains("AE22B002"), "Second highest CGPA student should be allocated");
        assertFalse(student1.isAvailable(), "Student 1 should be unavailable");
        assertFalse(student2.isAvailable(), "Student 2 should be unavailable");
        assertTrue(student3.isAvailable(), "Lower CGPA student 3 should remain available");

        verify(courseRepository, times(1)).save(course);
        verify(studentRepository, times(2)).save(any(Student.class));
    }
}
