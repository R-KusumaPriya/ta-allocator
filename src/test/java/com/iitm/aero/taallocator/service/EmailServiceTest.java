package com.iitm.aero.taallocator.service;

import com.iitm.aero.taallocator.model.Course;
import com.iitm.aero.taallocator.model.Faculty;
import com.iitm.aero.taallocator.model.Student;
import com.iitm.aero.taallocator.repository.CourseRepository;
import com.iitm.aero.taallocator.repository.FacultyRepository;
import com.iitm.aero.taallocator.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private FacultyRepository facultyRepository;

    @Mock
    private StudentRepository studentRepository;

    @InjectMocks
    private EmailService emailService;

    private Course course;
    private Faculty faculty;
    private Student ta1;
    private Student ta2;

    @BeforeEach
    void setUp() {
        course = new Course();
        course.setCourseNo("AS2100");
        course.setCourseName("Basic Aerospace Lab");
        course.setInstructorName("Dr. Bharath G");
        course.setGroupId(1.0);

        faculty = new Faculty();
        faculty.setName("Dr. Bharath G");
        faculty.setEmail("bharath@aerospace.iitm.ac.in");

        ta1 = new Student();
        ta1.setRollNo("AE22B001");
        ta1.setName("Alice");

        ta2 = new Student();
        ta2.setRollNo("AE22B002");
        ta2.setName("Bob");
    }

    @Test
    void testSendPreferenceEmails_Success() {
        // Arrange
        when(courseRepository.findByGroupId(1.0)).thenReturn(Collections.singletonList(course));
        when(facultyRepository.findByNameIgnoreCase("Dr. Bharath G")).thenReturn(faculty);

        // Act
        String result = emailService.sendPreferenceEmails(1.0, "Jul-Nov 2026", "Aug 10", "https://forms.gle/xyz");

        // Assert
        assertTrue(result.contains("1/1 sent"));
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        SimpleMailMessage sentMsg = captor.getValue();
        assertEquals("bharath@aerospace.iitm.ac.in", sentMsg.getTo()[0]);
        assertTrue(sentMsg.getSubject().contains("Jul-Nov 2026"));
        assertTrue(sentMsg.getText().contains("https://forms.gle/xyz"));
        assertTrue(sentMsg.getText().contains("Aug 10"));
    }

    @Test
    void testSendPreferenceEmails_NoCoursesFound() {
        when(courseRepository.findByGroupId(99.0)).thenReturn(Collections.emptyList());

        String result = emailService.sendPreferenceEmails(99.0, "Jul-Nov 2026", "Aug 10", "link");

        assertEquals("No courses found for Group ID: 99.0", result);
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void testSendPreferenceEmails_MissingFacultyEmail() {
        // Faculty not in faculty table, and course has no facultyEmail
        when(courseRepository.findByGroupId(1.0)).thenReturn(Collections.singletonList(course));
        when(facultyRepository.findByNameIgnoreCase("Dr. Bharath G")).thenReturn(null);
        when(facultyRepository.findAll()).thenReturn(Collections.emptyList());

        String result = emailService.sendPreferenceEmails(1.0, "Jul-Nov 2026", "Aug 10", "link");

        assertTrue(result.contains("0/1 sent"));
        assertTrue(result.contains("has no email in database"));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void testSendPreferenceEmails_NormalizedWhitespaceFallback() {
        // Arrange: Course has irregular spaces in name
        course.setInstructorName("Dr.   Bharath    G");
        when(courseRepository.findByGroupId(1.0)).thenReturn(Collections.singletonList(course));
        when(facultyRepository.findByNameIgnoreCase("Dr.   Bharath    G")).thenReturn(null);
        when(facultyRepository.findAll()).thenReturn(Collections.singletonList(faculty));

        // Act
        String result = emailService.sendPreferenceEmails(1.0, "Jul-Nov 2026", "Aug 10", "link");

        // Assert: Normalized matching found faculty email
        assertTrue(result.contains("1/1 sent"));
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void testSendPreferenceEmails_FallbackToCourseFacultyEmail() {
        // Arrange: Faculty not in faculty table at all, but course has facultyEmail populated
        course.setFacultyEmail("direct@aerospace.iitm.ac.in");
        when(courseRepository.findByGroupId(1.0)).thenReturn(Collections.singletonList(course));
        when(facultyRepository.findByNameIgnoreCase(any())).thenReturn(null);
        when(facultyRepository.findAll()).thenReturn(Collections.emptyList());

        // Act
        String result = emailService.sendPreferenceEmails(1.0, "Jul-Nov 2026", "Aug 10", "link");

        // Assert
        assertTrue(result.contains("1/1 sent"));
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());
        assertEquals("direct@aerospace.iitm.ac.in", captor.getValue().getTo()[0]);
    }

    @Test
    void testSendPreferenceEmails_MailSenderExceptionHandled() {
        // Arrange: Mail server throws an exception
        when(courseRepository.findByGroupId(1.0)).thenReturn(Collections.singletonList(course));
        when(facultyRepository.findByNameIgnoreCase("Dr. Bharath G")).thenReturn(faculty);
        doThrow(new MailSendException("SMTP connection refused")).when(mailSender).send(any(SimpleMailMessage.class));

        // Act: Should not crash with uncaught exception
        String result = emailService.sendPreferenceEmails(1.0, "Jul-Nov 2026", "Aug 10", "link");

        // Assert
        assertTrue(result.contains("FAILED for bharath@aerospace.iitm.ac.in"));
        assertTrue(result.contains("0/1 sent"));
    }

    @Test
    void testSendConfirmationEmails_Success() {
        // Arrange
        course.setTa1("AE22B001");
        course.setTa2("AE22B002");

        when(courseRepository.findByGroupId(1.0)).thenReturn(Collections.singletonList(course));
        when(facultyRepository.findByNameIgnoreCase("Dr. Bharath G")).thenReturn(faculty);
        when(studentRepository.findById("AE22B001")).thenReturn(Optional.of(ta1));
        when(studentRepository.findById("AE22B002")).thenReturn(Optional.of(ta2));

        // Act
        String result = emailService.sendConfirmationEmails(1.0, "Jul-Nov 2026");

        // Assert
        assertTrue(result.contains("1 sent"));
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        SimpleMailMessage sentMsg = captor.getValue();
        assertEquals("bharath@aerospace.iitm.ac.in", sentMsg.getTo()[0]);
        assertTrue(sentMsg.getSubject().contains("TA Allotment Confirmation"));
        assertTrue(sentMsg.getText().contains("AE22B001"));
        assertTrue(sentMsg.getText().contains("AE22B002"));
    }

    @Test
    void testSendConfirmationEmails_SingleTAAssigned() {
        // Arrange: Course only has ta1, ta2 is null
        course.setTa1("AE22B001");
        course.setTa2(null);

        when(courseRepository.findByGroupId(1.0)).thenReturn(Collections.singletonList(course));
        when(facultyRepository.findByNameIgnoreCase("Dr. Bharath G")).thenReturn(faculty);
        when(studentRepository.findById("AE22B001")).thenReturn(Optional.of(ta1));

        // Act
        String result = emailService.sendConfirmationEmails(1.0, "Jul-Nov 2026");

        // Assert
        assertTrue(result.contains("1 sent"));
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void testSendConfirmationEmails_SkippedWhenNoTAsAssigned() {
        // Course has no TAs assigned yet
        when(courseRepository.findByGroupId(1.0)).thenReturn(Collections.singletonList(course));
        when(facultyRepository.findByNameIgnoreCase("Dr. Bharath G")).thenReturn(faculty);

        String result = emailService.sendConfirmationEmails(1.0, "Jul-Nov 2026");

        assertTrue(result.contains("No TAs assigned yet"));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }
}
