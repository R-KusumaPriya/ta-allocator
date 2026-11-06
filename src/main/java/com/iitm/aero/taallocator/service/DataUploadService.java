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

    public static boolean isInvalidPlaceholder(String val) {
        if (val == null) return true;
        String s = val.trim();
        if (s.isEmpty()) return true;
        String lower = s.toLowerCase();
        return lower.equals("xxxx") || lower.equals("xxx") || lower.equals("xx")
            || lower.equals("na") || lower.equals("n/a") || lower.equals("none")
            || lower.equals("-") || lower.equals("tbd") || lower.equals("tba")
            || lower.contains("no preference") || lower.contains("do not want");
    }

    public void parseAndSaveCourses(MultipartFile file) throws Exception {
        try (CSVReader csvReader = new CSVReader(new InputStreamReader(file.getInputStream()))) {
            List<String[]> records = csvReader.readAll();
            if (records.size() <= 1) return;
            
            String[] headers = records.get(0);
            int idIdx = -1, nameIdx = -1, facultyIdx = -1, facultyEmailIdx = -1, priorityIdx = -1, groupIdx = -1, ta1Idx = -1, ta2Idx = -1;
            for (int j = 0; j < headers.length; j++) {
                String h = headers[j].trim().toLowerCase();
                if (h.equals("id") || h.equals("courseno") || h.equals("course no") || h.equals("course_no") || h.equals("courseno.")) idIdx = j;
                else if (h.equals("name") || h.equals("coursename") || h.equals("course name") || h.equals("course_name")) nameIdx = j;
                else if (h.contains("faculty_email") || h.contains("instructor_email") || h.equals("email")) facultyEmailIdx = j;
                else if (h.contains("faculty_name") || h.equals("instructorname") || h.equals("instructor name") || h.equals("instructor_name") || h.equals("faculty") || h.equals("instructor")) facultyIdx = j;
                else if (h.equals("priority")) priorityIdx = j;
                else if (h.equals("group_id") || h.equals("groupid") || h.equals("group id")) groupIdx = j;
                else if (h.equals("ta 1") || h.equals("ta1") || h.equals("ta_1")) ta1Idx = j;
                else if (h.equals("ta 2") || h.equals("ta2") || h.equals("ta_2")) ta2Idx = j;
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
                if (facultyEmailIdx != -1 && record.length > facultyEmailIdx && !record[facultyEmailIdx].trim().isEmpty()) {
                    course.setFacultyEmail(record[facultyEmailIdx].trim());
                }
                
                if (record.length > priorityIdx) {
                    try { course.setPriority(Integer.parseInt(record[priorityIdx].trim())); } 
                    catch (Exception e) { course.setPriority(0); }
                }
                
                if (record.length > groupIdx) {
                    try { course.setGroupId(Double.parseDouble(record[groupIdx].trim())); } 
                    catch (Exception e) { course.setGroupId(0); }
                }

                if (ta1Idx != -1 && record.length > ta1Idx && !record[ta1Idx].trim().isEmpty()) {
                    String val = record[ta1Idx].trim();
                    if (!isInvalidPlaceholder(val)) {
                        course.setAssignedTa1(val);
                    }
                }
                if (ta2Idx != -1 && record.length > ta2Idx && !record[ta2Idx].trim().isEmpty()) {
                    String val = record[ta2Idx].trim();
                    if (!isInvalidPlaceholder(val)) {
                        course.setAssignedTa2(val);
                    }
                }

                courses.add(course);
            }
            courseRepository.deleteAll();
            courseRepository.saveAll(courses);

            for (Course c : courses) {
                markStudentAssignedFromCourse(c.getAssignedTa1(), c.getCourseNo());
                markStudentAssignedFromCourse(c.getAssignedTa2(), c.getCourseNo());
            }
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
                else if (h.equals("name") || h.equals("student name")) nameIdx = j;
                else if (h.equals("gpa") || h.equals("cgpa") || h.contains("cgpa") || h.contains("gpa")) gpaIdx = j;
                else if (h.equals("program")) programIdx = j;
                else if (h.equals("available")) availIdx = j;
                else if (h.equals("course")) courseIdx = j;
            }
            if (idIdx == -1) idIdx = 0;
            if (nameIdx == -1) nameIdx = 1;
            if (gpaIdx == -1) gpaIdx = 3;
            if (programIdx == -1 && headers.length > 4 && headers[4].trim().equalsIgnoreCase("program")) programIdx = 4;
            if (availIdx == -1 && headers.length > 6 && headers[6].trim().equalsIgnoreCase("available")) availIdx = 6;

            List<Student> students = new ArrayList<>();
            for (int i = 1; i < records.size(); i++) {
                String[] record = records.get(i);
                if (record.length <= idIdx || record[idIdx].trim().isEmpty()) continue;
                
                Student student = new Student();
                student.setRollNo(record[idIdx].trim());
                if (record.length > nameIdx) student.setName(record[nameIdx].trim());
                if (gpaIdx != -1 && record.length > gpaIdx) student.setCgpa(record[gpaIdx].trim());
                
                if (programIdx != -1 && record.length > programIdx && !record[programIdx].trim().isEmpty()) {
                    student.setProgram(record[programIdx].trim());
                } else {
                    // Automatically infer program from standard IITM roll number prefix
                    String r = student.getRollNo().toUpperCase();
                    if (r.contains("B")) student.setProgram("BTech");
                    else if (r.contains("D")) student.setProgram("PhD");
                    else if (r.contains("M")) student.setProgram("MTech");
                    else if (r.contains("S")) student.setProgram("MS");
                    else student.setProgram("BTech");
                }
                
                if (availIdx != -1 && record.length > availIdx && !record[availIdx].trim().isEmpty()) {
                    student.setAvailable(Boolean.parseBoolean(record[availIdx].trim().toLowerCase()));
                } else {
                    student.setAvailable(true); // Default
                }
                
                if (courseIdx != -1 && record.length > courseIdx) {
                    String c = record[courseIdx].trim();
                    if (!isInvalidPlaceholder(c)) {
                        student.setCourse(c);
                        student.setAvailable(false);
                    } else {
                        student.setCourse("");
                    }
                }
                
                students.add(student);
            }

            List<Course> existingCourses = courseRepository.findAll();
            if (existingCourses != null && !existingCourses.isEmpty()) {
                java.util.Map<String, String> assignedMap = new java.util.HashMap<>();
                for (Course c : existingCourses) {
                    if (c != null) {
                        addAssignedMapping(assignedMap, c.getAssignedTa1(), c.getCourseNo());
                        addAssignedMapping(assignedMap, c.getAssignedTa2(), c.getCourseNo());
                    }
                }
                for (Student s : students) {
                    if (s.getRollNo() != null) {
                        String assignedCourse = assignedMap.get(s.getRollNo().trim().toUpperCase());
                        if (assignedCourse != null) {
                            s.setAvailable(false);
                            s.setCourse(assignedCourse);
                        }
                    }
                }
            }

            studentRepository.deleteAll();
            studentRepository.saveAll(students);
        }
    }

    public void parseAndSaveFaculty(MultipartFile file) throws Exception {
        try (CSVReader csvReader = new CSVReader(new InputStreamReader(file.getInputStream()))) {
            List<String[]> records = csvReader.readAll();
            if (records.size() <= 1) return;

            String[] headers = records.get(0);
            int nameIdx = -1, emailIdx = -1, codeIdx = -1;
            for (int j = 0; j < headers.length; j++) {
                String h = headers[j].trim().toLowerCase();
                if (h.contains("instructor") || h.contains("name")) nameIdx = j;
                else if (h.contains("email")) emailIdx = j;
                else if (h.contains("code")) codeIdx = j;
            }
            if (nameIdx == -1) nameIdx = 0;
            if (emailIdx == -1) emailIdx = 1;
            if (codeIdx == -1 && headers.length > 2) codeIdx = 2;

            List<Faculty> faculties = new ArrayList<>();
            for (int i = 1; i < records.size(); i++) {
                String[] record = records.get(i);
                if (record.length <= nameIdx || record[nameIdx].trim().isEmpty()) continue;
                Faculty faculty = new Faculty();
                faculty.setName(record[nameIdx].trim());
                
                String email = (record.length > emailIdx) ? record[emailIdx].trim() : "";
                if (!email.isEmpty() && !email.contains("@")) {
                    email = email + "@iitm.ac.in";
                }
                faculty.setEmail(email);
                
                if (codeIdx != -1 && record.length > codeIdx) {
                    faculty.setCode(record[codeIdx].trim());
                }
                faculties.add(faculty);
            }
            facultyRepository.deleteAll();
            facultyRepository.saveAll(faculties);
        }
    }

    public void parseAndSavePreferences(MultipartFile file) throws Exception {
        try (CSVReader csvReader = new CSVReader(new InputStreamReader(file.getInputStream()))) {
            List<String[]> records = csvReader.readAll();
            if (records.size() <= 1) return;
            
            String[] headers = records.get(0);
            int courseIdx = -1, facultyIdx = -1, c1Idx = -1, c2Idx = -1, c3Idx = -1, c4Idx = -1, emailIdx = -1, timeIdx = -1;
            for (int j = 0; j < headers.length; j++) {
                String h = headers[j].trim().toLowerCase();
                if (h.equals("course_id") || h.equals("select course") || h.equals("selectcourse") || h.contains("course")) courseIdx = j;
                else if (h.equals("faculty_name") || h.equals("faculty name") || h.contains("faculty")) facultyIdx = j;
                else if (h.equals("choice1") || h.contains("preference 1") || h.contains("choice 1")) c1Idx = j;
                else if (h.equals("choice2") || h.contains("preference 2") || h.contains("choice 2")) c2Idx = j;
                else if (h.equals("choice3") || h.contains("preference 3") || h.contains("choice 3")) c3Idx = j;
                else if (h.equals("choice4") || h.contains("preference 4") || h.contains("choice 4")) c4Idx = j;
                else if (h.contains("email")) emailIdx = j;
                else if (h.contains("time")) timeIdx = j;
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
                if (emailIdx != -1 && record.length > emailIdx) pref.setEmailAddress(record[emailIdx].trim());
                if (timeIdx != -1 && record.length > timeIdx) pref.setTimestamp(record[timeIdx].trim());
                if (record.length > c1Idx) pref.setTaPreference1(record[c1Idx].trim());
                if (record.length > c2Idx) pref.setTaPreference2(record[c2Idx].trim());
                if (record.length > c3Idx) pref.setTaPreference3(record[c3Idx].trim());
                if (record.length > c4Idx) pref.setTaPreference4(record[c4Idx].trim());
                
                preferences.add(pref);
            }
            preferenceRepository.saveAll(preferences);
        }
    }

    public void exportAllocationsCsv(Double groupId, java.io.Writer writer) throws Exception {
        List<Course> courses;
        if (groupId != null) {
            courses = courseRepository.findByGroupId(groupId);
        } else {
            courses = courseRepository.findAll();
        }

        try (com.opencsv.CSVWriter csvWriter = new com.opencsv.CSVWriter(writer)) {
            String[] header = {
                "Course No", "Course Name", "Instructor Name", "Faculty Email",
                "Priority", "Group ID", "TA 1 Roll", "TA 1 Name", "TA 2 Roll", "TA 2 Name"
            };
            csvWriter.writeNext(header);

            for (Course course : courses) {
                String ta1Roll = (course.getTa1() != null && !course.getTa1().trim().isEmpty() && !isInvalidPlaceholder(course.getTa1()))
                        ? course.getTa1().trim() : "-";
                String ta1Name = resolveStudentName(course.getTa1());

                String ta2Roll = (course.getTa2() != null && !course.getTa2().trim().isEmpty() && !isInvalidPlaceholder(course.getTa2()))
                        ? course.getTa2().trim() : "-";
                String ta2Name = resolveStudentName(course.getTa2());

                String[] row = {
                    course.getCourseNo() != null ? course.getCourseNo() : "",
                    course.getCourseName() != null ? course.getCourseName() : "",
                    course.getInstructorName() != null ? course.getInstructorName() : "",
                    course.getFacultyEmail() != null ? course.getFacultyEmail() : "",
                    String.valueOf(course.getPriority()),
                    String.valueOf(course.getGroupId()),
                    ta1Roll,
                    ta1Name,
                    ta2Roll,
                    ta2Name
                };
                csvWriter.writeNext(row);
            }
        }
    }

    private String resolveStudentName(String rollNo) {
        if (rollNo == null || rollNo.trim().isEmpty() || isInvalidPlaceholder(rollNo)) {
            return "-";
        }
        return studentRepository.findById(rollNo.trim())
                .map(Student::getName)
                .orElse("-");
    }

    private void markStudentAssignedFromCourse(String assignedVal, String courseNo) {
        if (assignedVal == null || assignedVal.trim().isEmpty() || isInvalidPlaceholder(assignedVal)) {
            return;
        }
        String roll = assignedVal.trim();
        if (roll.contains(":")) {
            roll = roll.split(":", 2)[0].trim();
        }
        if (isInvalidPlaceholder(roll)) {
            return;
        }
        studentRepository.findByRollNoIgnoreCase(roll).ifPresent(s -> {
            s.setAvailable(false);
            s.setCourse(courseNo);
            studentRepository.save(s);
        });
    }

    private void addAssignedMapping(java.util.Map<String, String> assignedMap, String assignedVal, String courseNo) {
        if (assignedVal == null || assignedVal.trim().isEmpty() || isInvalidPlaceholder(assignedVal)) {
            return;
        }
        String roll = assignedVal.trim();
        if (roll.contains(":")) {
            roll = roll.split(":", 2)[0].trim();
        }
        if (!isInvalidPlaceholder(roll)) {
            assignedMap.put(roll.toUpperCase(), courseNo != null ? courseNo : "");
        }
    }
}
