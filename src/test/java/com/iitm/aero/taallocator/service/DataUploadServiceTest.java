package com.iitm.aero.taallocator.service;

import com.iitm.aero.taallocator.model.Course;
import com.iitm.aero.taallocator.model.Faculty;
import com.iitm.aero.taallocator.model.PreferenceResponse;
import com.iitm.aero.taallocator.model.Student;
import com.iitm.aero.taallocator.repository.CourseRepository;
import com.iitm.aero.taallocator.repository.FacultyRepository;
import com.iitm.aero.taallocator.repository.PreferenceRepository;
import com.iitm.aero.taallocator.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DataUploadServiceTest {

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private FacultyRepository facultyRepository;
    @Mock
    private PreferenceRepository preferenceRepository;

    @InjectMocks
    private DataUploadService dataUploadService;

    @Test
    void testParseAndSaveCourses_DynamicHeaders() throws Exception {
        // Arrange
        String csvContent = "id,name,faculty_name,faculty_email,priority,group_id\n" +
                            "AS6000,Basic Concepts,Bharath G,bharath@test.com,5,0\n";
        MockMultipartFile file = new MockMultipartFile("file", "courses.csv", "text/csv", csvContent.getBytes());

        // Act
        dataUploadService.parseAndSaveCourses(file);

        // Assert
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Course>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(courseRepository).saveAll(captor.capture());

        List<Course> savedCourses = captor.getValue();
        assertEquals(1, savedCourses.size(), "Should have parsed 1 course");
        
        Course course = savedCourses.get(0);
        assertEquals("AS6000", course.getCourseNo());
        assertEquals(0.0, course.getGroupId());
        assertEquals(5, course.getPriority());
        assertEquals("Bharath G", course.getInstructorName());
        assertEquals("bharath@test.com", course.getFacultyEmail());
    }

    @Test
    void testParseAndSaveCourses_MalformedNumericFieldsDefaultToZero() throws Exception {
        // Arrange: Priority is "High", Group ID is "Core" (non-numeric)
        String csvContent = "id,name,faculty_name,faculty_email,priority,group_id\n" +
                            "AS2100,Aerospace Lab,Dr. Murugan,murugan@test.com,High,Core\n";
        MockMultipartFile file = new MockMultipartFile("file", "courses.csv", "text/csv", csvContent.getBytes());

        // Act
        dataUploadService.parseAndSaveCourses(file);

        // Assert: Defaults safely to 0 without throwing NumberFormatException
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Course>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(courseRepository).saveAll(captor.capture());

        Course course = captor.getValue().get(0);
        assertEquals(0, course.getPriority());
        assertEquals(0.0, course.getGroupId());
    }

    @Test
    void testParseAndSaveCourses_EmptyOrHeaderOnlyCSV() throws Exception {
        // Arrange: Only header line
        String csvContent = "id,name,faculty_name,faculty_email,priority,group_id\n";
        MockMultipartFile file = new MockMultipartFile("file", "courses.csv", "text/csv", csvContent.getBytes());

        // Act
        dataUploadService.parseAndSaveCourses(file);

        // Assert: Never saves to repository
        verify(courseRepository, never()).saveAll(any());
    }

    @Test
    void testParseAndSaveStudents_AvailabilityLogic() throws Exception {
        // Arrange: One student without a course ('xxxx'), one with a course ('AS2100')
        String csvContent = "id,name,email,gpa,program,advisor,available,course\n" +
                            "AE22B001,Student A,a@test.com,9.0,BTech,,TRUE,xxxx\n" +
                            "AE22B002,Student B,b@test.com,8.5,BTech,,TRUE,AS2100\n";
        MockMultipartFile file = new MockMultipartFile("file", "students.csv", "text/csv", csvContent.getBytes());

        // Act
        dataUploadService.parseAndSaveStudents(file);

        // Assert
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Student>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(studentRepository).saveAll(captor.capture());

        List<Student> savedStudents = captor.getValue();
        assertEquals(2, savedStudents.size(), "Should have parsed 2 students");
        
        Student studentA = savedStudents.get(0);
        assertEquals("AE22B001", studentA.getRollNo());
        assertTrue(studentA.isAvailable(), "Student with 'xxxx' course should be available");

        Student studentB = savedStudents.get(1);
        assertEquals("AE22B002", studentB.getRollNo());
        assertFalse(studentB.isAvailable(), "Student with an assigned course should NOT be available");
    }

    @Test
    void testParseAndSaveStudents_EmptyRowsSkipped() throws Exception {
        // Arrange: One valid student and one blank/empty row
        String csvContent = "id,name,email,gpa,program,advisor,available,course\n" +
                            "AE22B001,Student A,a@test.com,9.0,BTech,,TRUE,xxxx\n" +
                            " ,,,,,,\n";
        MockMultipartFile file = new MockMultipartFile("file", "students.csv", "text/csv", csvContent.getBytes());

        // Act
        dataUploadService.parseAndSaveStudents(file);

        // Assert: Only 1 student parsed
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Student>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(studentRepository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());
    }

    @Test
    void testParseAndSaveFaculty_Success() throws Exception {
        // Arrange
        String csvContent = "name,email,code\n" +
                            "Dr. John Doe,johndoe@iitm.ac.in,JD\n";
        MockMultipartFile file = new MockMultipartFile("file", "faculty.csv", "text/csv", csvContent.getBytes());

        // Act
        dataUploadService.parseAndSaveFaculty(file);

        // Assert
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Faculty>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(facultyRepository).saveAll(captor.capture());

        List<Faculty> savedFaculty = captor.getValue();
        assertEquals(1, savedFaculty.size());
        assertEquals("Dr. John Doe", savedFaculty.get(0).getName());
        assertEquals("johndoe@iitm.ac.in", savedFaculty.get(0).getEmail());
        assertEquals("JD", savedFaculty.get(0).getCode());
    }

    @Test
    void testParseAndSavePreferences_Success() throws Exception {
        // Arrange
        String csvContent = "course_id,faculty_name,choice1,choice2,choice3,choice4\n" +
                            "AS2100,Dr. Murugan,AE22B105,AE23D205,,\n";
        MockMultipartFile file = new MockMultipartFile("file", "preferences.csv", "text/csv", csvContent.getBytes());

        // Act
        dataUploadService.parseAndSavePreferences(file);

        // Assert
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PreferenceResponse>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(preferenceRepository).saveAll(captor.capture());

        List<PreferenceResponse> savedPrefs = captor.getValue();
        assertEquals(1, savedPrefs.size());
        assertEquals("AS2100", savedPrefs.get(0).getSelectCourse());
        assertEquals("Dr. Murugan", savedPrefs.get(0).getFacultyName());
        assertEquals("AE22B105", savedPrefs.get(0).getTaPreference1());
        assertEquals("AE23D205", savedPrefs.get(0).getTaPreference2());
    }
}
