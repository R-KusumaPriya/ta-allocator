package com.iitm.aero.taallocator.service;

import com.iitm.aero.taallocator.model.Course;
import com.iitm.aero.taallocator.model.Faculty;
import com.iitm.aero.taallocator.model.PreferenceResponse;
import com.iitm.aero.taallocator.model.Student;
import com.iitm.aero.taallocator.repository.CourseRepository;
import com.iitm.aero.taallocator.repository.FacultyRepository;
import com.iitm.aero.taallocator.repository.PreferenceRepository;
import com.iitm.aero.taallocator.repository.StudentRepository;
import com.opencsv.CSVReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Service
public class DataUploadService {

    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private FacultyRepository facultyRepository;
    @Autowired
    private PreferenceRepository preferenceRepository;

    public void parseAndSaveCourses(MultipartFile file) throws Exception {
        try (CSVReader csvReader = new CSVReader(new InputStreamReader(file.getInputStream()))) {
            List<String[]> records = csvReader.readAll();
            if (records.size() <= 1) return;
            
            String[] headers = records.get(0);
            int idIdx = -1, nameIdx = -1, facultyIdx = -1, priorityIdx = -1, groupIdx = -1;
            for (int j = 0; j < headers.length; j++) {
                String h = headers[j].trim().toLowerCase();
                if (h.equals("id") || h.equals("courseno") || h.equals("course no")) idIdx = j;
                else if (h.equals("name") || h.equals("coursename") || h.equals("course name")) nameIdx = j;
                else if (h.contains("faculty_name") || h.equals("instructorname") || h.equals("instructor name")) facultyIdx = j;
                else if (h.equals("priority")) priorityIdx = j;
                else if (h.equals("group_id") || h.equals("groupid") || h.equals("group id")) groupIdx = j;
            }
            // Fallbacks if headers missing
            if (idIdx == -1) idIdx = 0;
            if (nameIdx == -1) nameIdx = 1;
            if (facultyIdx == -1) facultyIdx = 2;
            if (priorityIdx == -1) priorityIdx = 4;
            if (groupIdx == -1) groupIdx = 5;

            List<Course> courses = new ArrayList<>();
            for (int i = 1; i < records.size(); i++) {
                String[] record = records.get(i);
                if (record.length <= idIdx || record[idIdx].trim().isEmpty()) continue;
                
                Course course = new Course();
                course.setCourseNo(record[idIdx].trim());
                if (record.length > nameIdx) course.setCourseName(record[nameIdx].trim());
                if (record.length > facultyIdx) course.setInstructorName(record[facultyIdx].trim());
                
                if (record.length > priorityIdx) {
                    try { course.setPriority(Integer.parseInt(record[priorityIdx].trim())); } 
                    catch (Exception e) { course.setPriority(0); }
                }
                
                if (record.length > groupIdx) {
                    try { course.setGroupId(Double.parseDouble(record[groupIdx].trim())); } 
                    catch (Exception e) { course.setGroupId(0); }
                }
                courses.add(course);
            }
            courseRepository.saveAll(courses);
        }
    }

    public void parseAndSaveStudents(MultipartFile file) throws Exception {
        try (CSVReader csvReader = new CSVReader(new InputStreamReader(file.getInputStream()))) {
            List<String[]> records = csvReader.readAll();
            if (records.size() <= 1) return;
            
            String[] headers = records.get(0);
            int idIdx = -1, nameIdx = -1, gpaIdx = -1, programIdx = -1, availIdx = -1, courseIdx = -1;
            for (int j = 0; j < headers.length; j++) {
                String h = headers[j].trim().toLowerCase();
                if (h.equals("id") || h.contains("roll")) idIdx = j;
                else if (h.equals("name")) nameIdx = j;
                else if (h.equals("gpa") || h.equals("cgpa")) gpaIdx = j;
                else if (h.equals("program")) programIdx = j;
                else if (h.equals("available")) availIdx = j;
                else if (h.equals("course")) courseIdx = j;
            }
            if (idIdx == -1) idIdx = 0;
            if (nameIdx == -1) nameIdx = 1;
            if (gpaIdx == -1) gpaIdx = 3;
            if (programIdx == -1) programIdx = 4;
            if (availIdx == -1) availIdx = 6;

            List<Student> students = new ArrayList<>();
            for (int i = 1; i < records.size(); i++) {
                String[] record = records.get(i);
                if (record.length <= idIdx || record[idIdx].trim().isEmpty()) continue;
                
                Student student = new Student();
                student.setRollNo(record[idIdx].trim());
                if (record.length > nameIdx) student.setName(record[nameIdx].trim());
                if (gpaIdx != -1 && record.length > gpaIdx) student.setCgpa(record[gpaIdx].trim());
                if (programIdx != -1 && record.length > programIdx) student.setProgram(record[programIdx].trim());
                
                if (availIdx != -1 && record.length > availIdx) {
                    student.setAvailable(Boolean.parseBoolean(record[availIdx].trim().toLowerCase()));
                } else {
                    student.setAvailable(true); // Default
                }
                
                if (courseIdx != -1 && record.length > courseIdx) {
                    student.setCourse(record[courseIdx].trim());
                    if (student.getCourse() != null && !student.getCourse().trim().isEmpty() && !student.getCourse().equalsIgnoreCase("xxxx")) {
                        student.setAvailable(false);
                    }
                }
                
                students.add(student);
            }
            studentRepository.saveAll(students);
        }
    }

    public void parseAndSaveFaculty(MultipartFile file) throws Exception {
        try (CSVReader csvReader = new CSVReader(new InputStreamReader(file.getInputStream()))) {
            List<String[]> records = csvReader.readAll();
            List<Faculty> faculties = new ArrayList<>();
            for (int i = 1; i < records.size(); i++) {
                String[] record = records.get(i);
                if (record.length < 2 || record[0].trim().isEmpty()) continue;
                Faculty faculty = new Faculty();
                faculty.setName(record[0].trim());
                faculty.setEmail(record[1].trim());
                if (record.length > 2) faculty.setCode(record[2].trim());
                faculties.add(faculty);
            }
            facultyRepository.saveAll(faculties);
        }
    }

    public void parseAndSavePreferences(MultipartFile file) throws Exception {
        try (CSVReader csvReader = new CSVReader(new InputStreamReader(file.getInputStream()))) {
            List<String[]> records = csvReader.readAll();
            if (records.size() <= 1) return;
            
            String[] headers = records.get(0);
            int courseIdx = -1, facultyIdx = -1, c1Idx = -1, c2Idx = -1, c3Idx = -1, c4Idx = -1;
            for (int j = 0; j < headers.length; j++) {
                String h = headers[j].trim().toLowerCase();
                if (h.equals("course_id") || h.equals("select course") || h.equals("selectcourse")) courseIdx = j;
                else if (h.equals("faculty_name") || h.equals("faculty name")) facultyIdx = j;
                else if (h.equals("choice1") || h.contains("preference 1")) c1Idx = j;
                else if (h.equals("choice2") || h.contains("preference 2")) c2Idx = j;
                else if (h.equals("choice3") || h.contains("preference 3")) c3Idx = j;
                else if (h.equals("choice4") || h.contains("preference 4")) c4Idx = j;
            }
            if (courseIdx == -1) courseIdx = 0;
            if (facultyIdx == -1) facultyIdx = 1;
            if (c1Idx == -1) c1Idx = 2;
            if (c2Idx == -1) c2Idx = 3;
            if (c3Idx == -1) c3Idx = 4;
            if (c4Idx == -1) c4Idx = 5;

            List<PreferenceResponse> preferences = new ArrayList<>();
            for (int i = 1; i < records.size(); i++) {
                String[] record = records.get(i);
                if (record.length <= courseIdx || record[courseIdx].trim().isEmpty()) continue;
                
                PreferenceResponse pref = new PreferenceResponse();
                pref.setSelectCourse(record[courseIdx].trim());
                if (record.length > facultyIdx) pref.setFacultyName(record[facultyIdx].trim());
                if (record.length > c1Idx) pref.setTaPreference1(record[c1Idx].trim());
                if (record.length > c2Idx) pref.setTaPreference2(record[c2Idx].trim());
                if (record.length > c3Idx) pref.setTaPreference3(record[c3Idx].trim());
                if (record.length > c4Idx) pref.setTaPreference4(record[c4Idx].trim());
                
                preferences.add(pref);
            }
            preferenceRepository.saveAll(preferences);
        }
    }
}
