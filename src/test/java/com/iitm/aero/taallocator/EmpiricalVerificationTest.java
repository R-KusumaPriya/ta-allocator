package com.iitm.aero.taallocator;

import com.iitm.aero.taallocator.model.Course;
import com.iitm.aero.taallocator.model.Student;
import com.iitm.aero.taallocator.repository.CourseRepository;
import com.iitm.aero.taallocator.repository.FacultyRepository;
import com.iitm.aero.taallocator.repository.PreferenceRepository;
import com.iitm.aero.taallocator.repository.StudentRepository;
import com.iitm.aero.taallocator.service.AllocationService;
import com.iitm.aero.taallocator.service.DataUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class EmpiricalVerificationTest {

    @Autowired
    private DataUploadService dataUploadService;

    @Autowired
    private AllocationService allocationService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private FacultyRepository facultyRepository;

    @Autowired
    private PreferenceRepository preferenceRepository;

    @Test
    void runEmpiricalLiveExecutionOnTestFiles() throws Exception {
        System.out.println("================================================================================");
        System.out.println("  STARTING EMPIRICAL LIVE EXECUTION RUN ON ACTUAL CSV TEST FIXTURES");
        System.out.println("================================================================================");

        // 1. Ingest actual CSV files
        byte[] coursesBytes = Files.readAllBytes(Paths.get("courses_test.csv"));
        byte[] studentsBytes = Files.readAllBytes(Paths.get("students_test.csv"));
        byte[] prefsBytes = Files.readAllBytes(Paths.get("preferences_test.csv"));
        byte[] facultyBytes = Files.readAllBytes(Paths.get("faculty_test.csv"));

        dataUploadService.parseAndSaveCourses(new MockMultipartFile("courses", "courses_test.csv", "text/csv", coursesBytes));
        dataUploadService.parseAndSaveStudents(new MockMultipartFile("students", "students_test.csv", "text/csv", studentsBytes));
        dataUploadService.parseAndSavePreferences(new MockMultipartFile("preferences", "preferences_test.csv", "text/csv", prefsBytes));
        dataUploadService.parseAndSaveFaculty(new MockMultipartFile("faculty", "faculty_test.csv", "text/csv", facultyBytes));

        long courseCount = courseRepository.count();
        long studentCount = studentRepository.count();
        long prefCount = preferenceRepository.count();
        long facultyCount = facultyRepository.count();

        System.out.println("Loaded database records from CSV files:");
        System.out.println("  Courses count:    " + courseCount);
        System.out.println("  Students count:   " + studentCount);
        System.out.println("  Preferences count:" + prefCount);
        System.out.println("  Faculty count:    " + facultyCount);

        // 2. Execute Allocation for Group 0.0
        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.println(">>> EXECUTING LIVE ALLOCATION FOR GROUP 0.0 <<<");
        System.out.println("--------------------------------------------------------------------------------");
        allocationService.allocateGroup(0.0);

        List<Course> group0Courses = courseRepository.findByGroupId(0.0);
        System.out.println("\nDatabase state after Group 0.0 allocation:");
        for (Course c : group0Courses) {
            System.out.println("  Course: " + c.getCourseNo() + " | Name: " + c.getCourseName() 
                + " | Instructor: " + c.getInstructorName() + " | Priority: " + c.getPriority()
                + " | TA1: " + c.getTa1() + " | TA2: " + c.getTa2());
        }

        // Verify that AS6000 got AE25M021 and AE25M028
        Course as6000 = courseRepository.findById("AS6000").orElse(null);
        assertNotNull(as6000);
        assertEquals("AE25M021", as6000.getTa1());
        assertEquals("AE25M028", as6000.getTa2());

        // Verify that AS5100 got AE22B109 and AE22B110
        Course as5100 = courseRepository.findById("AS5100").orElse(null);
        assertNotNull(as5100);
        assertEquals("AE22B109", as5100.getTa1());
        assertEquals("AE22B110", as5100.getTa2());

        // Verify that AS5100* got AE24D004 and AE22B102
        Course as5100star = courseRepository.findById("AS5100*").orElse(null);
        assertNotNull(as5100star);
        assertEquals("AE24D004", as5100star.getTa1());
        assertEquals("AE22B102", as5100star.getTa2());

        // 3. Execute Allocation for Group 1.0
        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.println(">>> EXECUTING LIVE ALLOCATION FOR GROUP 1.0 <<<");
        System.out.println("--------------------------------------------------------------------------------");
        allocationService.allocateGroup(1.0);

        List<Course> group1Courses = courseRepository.findByGroupId(1.0);
        System.out.println("\nDatabase state after Group 1.0 allocation:");
        for (Course c : group1Courses) {
            System.out.println("  Course: " + c.getCourseNo() + " | Name: " + c.getCourseName() 
                + " | Instructor: " + c.getInstructorName() + " | Priority: " + c.getPriority()
                + " | TA1: " + c.getTa1() + " | TA2: " + c.getTa2());
        }

        System.out.println("================================================================================");
        System.out.println("  EMPIRICAL LIVE EXECUTION COMPLETED SUCCESSFULLY");
        System.out.println("================================================================================");
    }
}
