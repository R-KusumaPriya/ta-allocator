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

import java.util.Collections;
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

    @Test
    void testExportAllocationsCsv_AllCourses() throws Exception {
        // Arrange
        Course course = new Course();
        course.setCourseNo("AS2100");
        course.setCourseName("Basic Lab");
        course.setInstructorName("Dr. Senthil");
        course.setFacultyEmail("senthil@iitm.ac.in");
        course.setPriority(20);
        course.setGroupId(1.0);
        course.setTa1("AE22B001");
        course.setTa2("AE22B002");

        Student s1 = new Student();
        s1.setRollNo("AE22B001");
        s1.setName("Alice");

        Student s2 = new Student();
        s2.setRollNo("AE22B002");
        s2.setName("Bob");

        when(courseRepository.findAll()).thenReturn(java.util.Collections.singletonList(course));
        when(studentRepository.findById("AE22B001")).thenReturn(java.util.Optional.of(s1));
        when(studentRepository.findById("AE22B002")).thenReturn(java.util.Optional.of(s2));

        java.io.StringWriter stringWriter = new java.io.StringWriter();

        // Act
        dataUploadService.exportAllocationsCsv(null, stringWriter);

        // Assert
        String csvOutput = stringWriter.toString();
        assertTrue(csvOutput.contains("Course No"));
        assertTrue(csvOutput.contains("AS2100"));
        assertTrue(csvOutput.contains("Alice"));
        assertTrue(csvOutput.contains("Bob"));
    }

    @Test
    void testExportAllocationsCsv_SpecificGroup() throws Exception {
        // Arrange
        Course course = new Course();
        course.setCourseNo("AS6000");
        course.setGroupId(0.0);

        when(courseRepository.findByGroupId(0.0)).thenReturn(java.util.Collections.singletonList(course));

        java.io.StringWriter stringWriter = new java.io.StringWriter();

        // Act
        dataUploadService.exportAllocationsCsv(0.0, stringWriter);

        // Assert
        String csvOutput = stringWriter.toString();
        assertTrue(csvOutput.contains("AS6000"));
        verify(courseRepository, times(1)).findByGroupId(0.0);
    }

    @Test
    void testParseAndSaveProfessorFormat_AllFiles() throws Exception {
        // 1. Test Courses in professor format
        String coursesCsv = "CourseNo,Semester,Priority,CourseName,InstructorName,# TotalRegn,Group ID,TA 1,TA 2,Column 1\n" +
                            "AS6000,15,5,Basic Concepts,BHARATH GOVINDARAJAN,0,0,AE25M021,AE25M028,\n";
        dataUploadService.parseAndSaveCourses(new MockMultipartFile("file", "courses.csv", "text/csv", coursesCsv.getBytes()));
        
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Course>> cCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(courseRepository, atLeastOnce()).saveAll(cCaptor.capture());
        Course c = cCaptor.getValue().get(0);
        assertEquals("AS6000", c.getCourseNo());
        assertEquals(0.0, c.getGroupId());
        assertEquals("BHARATH GOVINDARAJAN", c.getInstructorName());
        assertEquals("AE25M021", c.getAssignedTa1());
        assertEquals("AE25M028", c.getAssignedTa2());

        // 2. Test Students in professor format
        String studentsCsv = "Roll No.,Name,# CGPA,Course,Column 1\n" +
                             "AE22B003,PURNA ANANTHKRISHNAN,9.81,AS2100+,\n" +
                             "AE22B008,U SHRIRAM,9.17,xxxx,AS2100+\n";
        dataUploadService.parseAndSaveStudents(new MockMultipartFile("file", "students.csv", "text/csv", studentsCsv.getBytes()));
        
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Student>> sCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(studentRepository, atLeastOnce()).saveAll(sCaptor.capture());
        List<Student> students = sCaptor.getValue();
        assertEquals(2, students.size());
        assertEquals("AE22B003", students.get(0).getRollNo());
        assertEquals("9.81", students.get(0).getCgpa());
        assertEquals("BTech", students.get(0).getProgram()); // auto-inferred
        assertFalse(students.get(0).isAvailable()); // assigned to AS2100+
        assertTrue(students.get(1).isAvailable()); // xxxx -> available

        // 3. Test Faculty in professor format (email handles auto-appended with domain)
        String facultyCsv = "InstructorName,email,Code\n" +
                            "AMIT KUMAR,amitk,AmK\n";
        dataUploadService.parseAndSaveFaculty(new MockMultipartFile("file", "faculty.csv", "text/csv", facultyCsv.getBytes()));
        
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Faculty>> fCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(facultyRepository, atLeastOnce()).saveAll(fCaptor.capture());
        Faculty f = fCaptor.getValue().get(0);
        assertEquals("AMIT KUMAR", f.getName());
        assertEquals("amitk@iitm.ac.in", f.getEmail()); // auto-appended @iitm.ac.in
        assertEquals("AmK", f.getCode());

        // 4. Test Preferences in professor format
        String prefCsv = "Timestamp,Email Address,Faculty Name,Select Course,TA Preference 1,TA Preference 2,TA Preference 3,TA Preference 4\n" +
                         "8/2/2026 16:10:15,aswathys@smail.iitm.ac.in,ASWATHY SURENDRAN,AS6320,AE24D006: V K ARAVIND,AE24D017: PRAVEENKUMAR S,AE24D201: SAGAR MAYUR,AE25D402: MANISHA P\n";
        dataUploadService.parseAndSavePreferences(new MockMultipartFile("file", "pref.csv", "text/csv", prefCsv.getBytes()));
        
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PreferenceResponse>> pCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(preferenceRepository, atLeastOnce()).saveAll(pCaptor.capture());
        PreferenceResponse p = pCaptor.getValue().get(0);
        assertEquals("AS6320", p.getSelectCourse());
        assertEquals("ASWATHY SURENDRAN", p.getFacultyName());
        assertEquals("AE24D006: V K ARAVIND", p.getTaPreference1());
    }

    @Test
    void testParseAndSaveFaculty_EmptyOrHeaderOnlyCSV() throws Exception {
        String csvContent = "InstructorName,email,Code\n";
        MockMultipartFile file = new MockMultipartFile("file", "faculty.csv", "text/csv", csvContent.getBytes());

        dataUploadService.parseAndSaveFaculty(file);

        verify(facultyRepository, never()).saveAll(any());
        verify(facultyRepository, never()).deleteAll();
    }

    @Test
    void testParseAndSaveFaculty_MalformedRowsSkipped() throws Exception {
        String csvContent = "InstructorName,email,Code\n" +
                            "Dr. Valid Faculty,valid@iitm.ac.in,VF\n" +
                            ",emptyname@iitm.ac.in,EN\n" +
                            "   \n" +
                            ",,\n";
        MockMultipartFile file = new MockMultipartFile("file", "faculty.csv", "text/csv", csvContent.getBytes());

        dataUploadService.parseAndSaveFaculty(file);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Faculty>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(facultyRepository).saveAll(captor.capture());

        List<Faculty> saved = captor.getValue();
        assertEquals(1, saved.size(), "Only rows with non-empty names should be parsed");
        assertEquals("Dr. Valid Faculty", saved.get(0).getName());
        assertEquals("valid@iitm.ac.in", saved.get(0).getEmail());
        assertEquals("VF", saved.get(0).getCode());
    }

    @Test
    void testParseAndSavePreferences_EmptyOrHeaderOnlyCSV() throws Exception {
        String csvContent = "course_id,faculty_name,choice1,choice2,choice3,choice4\n";
        MockMultipartFile file = new MockMultipartFile("file", "preferences.csv", "text/csv", csvContent.getBytes());

        dataUploadService.parseAndSavePreferences(file);

        verify(preferenceRepository, never()).saveAll(any());
    }

    @Test
    void testParseAndSavePreferences_AlternativeHeaderNames() throws Exception {
        String csvContent = "Select Course,Faculty Name,Preference 1,Preference 2\n" +
                            "AS5100,Prof. Murthy,AE22B109,AE22B110\n";
        MockMultipartFile file = new MockMultipartFile("file", "preferences.csv", "text/csv", csvContent.getBytes());

        dataUploadService.parseAndSavePreferences(file);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PreferenceResponse>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(preferenceRepository).saveAll(captor.capture());

        List<PreferenceResponse> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertEquals("AS5100", saved.get(0).getSelectCourse());
        assertEquals("Prof. Murthy", saved.get(0).getFacultyName());
        assertEquals("AE22B109", saved.get(0).getTaPreference1());
        assertEquals("AE22B110", saved.get(0).getTaPreference2());
    }

    @Test
    void testExportAllocationsCsv_EmptyCourses_WritesHeadersOnly() throws Exception {
        when(courseRepository.findAll()).thenReturn(Collections.emptyList());
        java.io.StringWriter stringWriter = new java.io.StringWriter();

        dataUploadService.exportAllocationsCsv(null, stringWriter);

        String csvOutput = stringWriter.toString().trim();
        String[] lines = csvOutput.split("\\r?\\n");
        assertEquals(1, lines.length, "Output must contain only the header line");
        assertEquals("\"Course No\",\"Course Name\",\"Instructor Name\",\"Faculty Email\",\"Priority\",\"Group ID\",\"TA 1 Roll\",\"TA 1 Name\",\"TA 2 Roll\",\"TA 2 Name\"", lines[0]);
    }

    @Test
    void testExportAllocationsCsv_UnassignedTAs_OutputsHyphen() throws Exception {
        Course course = new Course();
        course.setCourseNo("AS5570");
        course.setCourseName("Flight Mechanics");
        course.setInstructorName("Dr. Rao");
        course.setFacultyEmail("rao@iitm.ac.in");
        course.setPriority(1);
        course.setGroupId(1.0);
        course.setTa1(null);
        course.setTa2(null);

        when(courseRepository.findAll()).thenReturn(Collections.singletonList(course));
        java.io.StringWriter stringWriter = new java.io.StringWriter();

        dataUploadService.exportAllocationsCsv(null, stringWriter);

        String csvOutput = stringWriter.toString().trim();
        String[] lines = csvOutput.split("\\r?\\n");
        assertEquals(2, lines.length, "Output should contain header and 1 course row");
        assertEquals("\"AS5570\",\"Flight Mechanics\",\"Dr. Rao\",\"rao@iitm.ac.in\",\"1\",\"1.0\",\"-\",\"-\",\"-\",\"-\"", lines[1]);
    }
}
