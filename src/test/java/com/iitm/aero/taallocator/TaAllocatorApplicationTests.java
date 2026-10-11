package com.iitm.aero.taallocator;

import com.iitm.aero.taallocator.controller.AdminController;
import com.iitm.aero.taallocator.model.Course;
import com.iitm.aero.taallocator.model.Faculty;
import com.iitm.aero.taallocator.model.PreferenceResponse;
import com.iitm.aero.taallocator.model.Student;
import com.iitm.aero.taallocator.repository.CourseRepository;
import com.iitm.aero.taallocator.repository.FacultyRepository;
import com.iitm.aero.taallocator.repository.PreferenceRepository;
import com.iitm.aero.taallocator.repository.StudentRepository;
import com.iitm.aero.taallocator.service.AllocationService;
import com.iitm.aero.taallocator.service.DataUploadService;
import com.iitm.aero.taallocator.service.EmailService;
import com.iitm.aero.taallocator.service.GoogleFormsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TaAllocatorApplicationTests {

	@Autowired
	private AdminController adminController;

	@Autowired
	private AllocationService allocationService;

	@Autowired
	private DataUploadService dataUploadService;

	@Autowired
	private EmailService emailService;

	@Autowired
	private GoogleFormsService googleFormsService;

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private StudentRepository studentRepository;

	@Autowired
	private FacultyRepository facultyRepository;

	@Autowired
	private PreferenceRepository preferenceRepository;

	@AfterEach
	void tearDown() {
		// Clean up any test entities created during integration tests
		courseRepository.deleteById("AS9999");
		studentRepository.deleteById("AE99B001");
		preferenceRepository.findAll().stream()
				.filter(p -> "AS9999".equalsIgnoreCase(p.getSelectCourse()))
				.forEach(preferenceRepository::delete);
		facultyRepository.findAll().stream()
				.filter(f -> "testfaculty@iitm.ac.in".equalsIgnoreCase(f.getEmail()))
				.forEach(facultyRepository::delete);
	}

	@Test
	void contextLoadsAndAllBeansAreInjected() {
		// Verify that the Spring Boot Application Context loads all critical beans
		assertNotNull(adminController, "AdminController should be injected into Spring Context");
		assertNotNull(allocationService, "AllocationService should be injected into Spring Context");
		assertNotNull(dataUploadService, "DataUploadService should be injected into Spring Context");
		assertNotNull(emailService, "EmailService should be injected into Spring Context");
		assertNotNull(googleFormsService, "GoogleFormsService should be injected into Spring Context");

		// Verify that all JPA Data Repositories are properly initialized
		assertNotNull(courseRepository, "CourseRepository should be initialized");
		assertNotNull(studentRepository, "StudentRepository should be initialized");
		assertNotNull(facultyRepository, "FacultyRepository should be initialized");
		assertNotNull(preferenceRepository, "PreferenceRepository should be initialized");
	}

	@Test
	void testEndToEndDatabaseIntegrationAndAllocationFlow() {
		// 1. Arrange: Persist real test records into the in-memory H2 database
		Course course = new Course();
		course.setCourseNo("AS9999");
		course.setCourseName("Experimental Aerodynamics");
		course.setInstructorName("Dr. Integration Tester");
		course.setGroupId(99.0);
		courseRepository.save(course);

		Student student = new Student();
		student.setRollNo("AE99B001");
		student.setName("Integration Student");
		student.setAvailable(true);
		student.setCgpa("9.5");
		student.setProgram("MTech");
		studentRepository.save(student);

		PreferenceResponse pref = new PreferenceResponse();
		pref.setSelectCourse("AS9999");
		pref.setFacultyName("Dr. Integration Tester");
		pref.setTaPreference1("AE99B001: Integration Student");
		preferenceRepository.save(pref);

		Faculty faculty = new Faculty();
		faculty.setName("Dr. Integration Tester");
		faculty.setEmail("testfaculty@iitm.ac.in");
		facultyRepository.save(faculty);

		// 2. Act: Trigger live allocation service across the database
		allocationService.allocateGroup(99.0);

		// 3. Assert: Query H2 database to verify state transitions
		Optional<Course> updatedCourseOpt = courseRepository.findById("AS9999");
		assertTrue(updatedCourseOpt.isPresent(), "Course should exist in database");
		Course updatedCourse = updatedCourseOpt.get();
		assertEquals("AE99B001", updatedCourse.getTa1(), "Student should be allocated to TA1");

		Optional<Student> updatedStudentOpt = studentRepository.findById("AE99B001");
		assertTrue(updatedStudentOpt.isPresent(), "Student should exist in database");
		Student updatedStudent = updatedStudentOpt.get();
		assertFalse(updatedStudent.isAvailable(), "Student should now be marked unavailable");
		assertEquals("AS9999", updatedStudent.getCourse(), "Student's assigned course should match");
	}
}
