package com.iitm.aero.taallocator.service;

import com.iitm.aero.taallocator.dto.AllocationPreviewDto;
import com.iitm.aero.taallocator.model.Course;
import com.iitm.aero.taallocator.model.PreferenceResponse;
import com.iitm.aero.taallocator.model.Student;
import com.iitm.aero.taallocator.repository.CourseRepository;
import com.iitm.aero.taallocator.repository.PreferenceRepository;
import com.iitm.aero.taallocator.repository.StudentRepository;
import com.iitm.aero.taallocator.util.HungarianAlgorithm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service that allocates Teaching Assistants to courses using Maximum Weight Bipartite Matching
 * (the Hungarian / Kuhn-Munkres algorithm).
 * 
 * Optimizes total preference satisfaction across all professors, honors course priorities,
 * and ensures no double-booking while adhering to departmental constraints.
 */
@Service
public class AllocationService {

    @Autowired
    private CourseRepository courseRepository;
    
    @Autowired
    private StudentRepository studentRepository;
    
    @Autowired
    private PreferenceRepository preferenceRepository;

    public void allocateGroup(double groupId) {
        List<Course> courses = courseRepository.findByGroupId(groupId);
        System.out.println("Allocating for Group ID: " + groupId + ". Found " + courses.size() + " courses.");

        if (courses == null || courses.isEmpty()) {
            return;
        }

        // 1. Fetch all students from the database
        List<Student> allStudents = studentRepository.findAll();
        if (allStudents == null) {
            allStudents = Collections.emptyList();
        }

        // Map roll numbers (case-insensitive) to Student entity
        Map<String, Student> rollToStudent = new HashMap<>();
        for (Student s : allStudents) {
            if (s.getRollNo() != null) {
                rollToStudent.put(s.getRollNo().trim().toUpperCase(), s);
            }
        }

        // Filter currently available students
        List<Student> availableStudents = allStudents.stream()
            .filter(Student::isAvailable)
            .toList();

        Map<String, Integer> rollToAvailableIdx = new HashMap<>();
        for (int j = 0; j < availableStudents.size(); j++) {
            String roll = availableStudents.get(j).getRollNo();
            if (roll != null) {
                rollToAvailableIdx.put(roll.trim().toUpperCase(), j);
            }
        }

        // 2. Fetch all preferences and identify courses eligible for allocation
        List<PreferenceResponse> allPrefs = preferenceRepository.findAll();
        if (allPrefs == null) {
            allPrefs = Collections.emptyList();
        }

        List<Course> coursesToProcess = new ArrayList<>();
        Map<String, PreferenceResponse> coursePrefMap = new HashMap<>();

        for (Course course : courses) {
            String cNo = (course.getCourseNo() != null) ? course.getCourseNo().trim() : "";
            
            // Find latest preference response for this course (case-insensitive & trimmed)
            List<PreferenceResponse> prefs = allPrefs.stream()
                .filter(p -> p.getSelectCourse() != null && p.getSelectCourse().trim().equalsIgnoreCase(cNo))
                .toList();

            if (prefs.isEmpty()) {
                System.out.println("  -> No preferences found for course: " + cNo);
                continue; // Skip course if no preferences submitted
            }

            PreferenceResponse pref = prefs.get(prefs.size() - 1);
            
            // Check if professor explicitly said "I do not want TAs"
            if (pref.getTaPreference1() != null && pref.getTaPreference1().toLowerCase().contains("do not want tas")) {
                System.out.println("  -> Professor for " + cNo + " explicitly opted out of TAs.");
                continue; // Do not save or assign
            }

            coursePrefMap.put(cNo, pref);
            coursesToProcess.add(course);
        }

        if (coursesToProcess.isEmpty()) {
            System.out.println("No eligible courses in Group " + groupId + " require TA allocation.");
            return;
        }

        // Sort eligible courses by priority (lower number = higher priority)
        coursesToProcess.sort(Comparator.comparingInt(Course::getPriority));

        // 3. Create 2 TA slots per course
        List<CourseSlot> slots = new ArrayList<>();
        for (Course course : coursesToProcess) {
            slots.add(new CourseSlot(course, 1));
            slots.add(new CourseSlot(course, 2));
        }

        int nSlots = slots.size();
        int nStudents = availableStudents.size();

        if (nStudents == 0) {
            System.out.println("No available students found in pool to allocate.");
            // Still save courses that were processed
            for (Course course : coursesToProcess) {
                courseRepository.save(course);
            }
            return;
        }

        // 4. Construct Weight Matrix: weights[slotIdx][studentIdx]
        double[][] weights = new double[nSlots][nStudents];

        for (int i = 0; i < nSlots; i++) {
            CourseSlot slot = slots.get(i);
            Course course = slot.course;
            String cNo = course.getCourseNo().trim();
            PreferenceResponse pref = coursePrefMap.get(cNo);

            // Priority bonus: higher department priority gives higher preference weight in tie-breakers
            double priorityBonus = Math.max(0, 100 - course.getPriority()) * 10.0;

            String[] taPrefs = {
                pref.getTaPreference1(),
                pref.getTaPreference2(),
                pref.getTaPreference3(),
                pref.getTaPreference4()
            };

            // Check if professor explicitly requested "No preference. You choose for me."
            boolean isAllNoPreference = true;
            for (String pStr : taPrefs) {
                if (pStr != null && !pStr.trim().isEmpty() && !pStr.toLowerCase().contains("no preference")) {
                    isAllNoPreference = false;
                    break;
                }
            }

            if (isAllNoPreference && taPrefs[0] != null && taPrefs[0].toLowerCase().contains("no preference")) {
                // Any available student can be assigned with base score + CGPA
                for (int j = 0; j < nStudents; j++) {
                    Student s = availableStudents.get(j);
                    double cgpa = parseCgpa(s.getCgpa());
                    weights[i][j] = 1000.0 + (cgpa * 20.0) + priorityBonus;
                }
            } else {
                // Explicit ranked preference scores:
                // Choice 1: 10,000 + priority bonus
                // Choice 2: 7,500 + priority bonus
                // Choice 3: 5,000 + priority bonus
                // Choice 4: 2,500 + priority bonus
                double[] choiceScores = { 10000.0, 7500.0, 5000.0, 2500.0 };

                for (int rank = 0; rank < taPrefs.length; rank++) {
                    String pStr = taPrefs[rank];
                    if (pStr == null || pStr.trim().isEmpty() || pStr.toLowerCase().contains("no preference")) {
                        continue;
                    }
                    String rollNo = extractRollNo(pStr);
                    if (rollNo != null && rollToAvailableIdx.containsKey(rollNo)) {
                        int sIdx = rollToAvailableIdx.get(rollNo);
                        // Assign weight for this student
                        weights[i][sIdx] = choiceScores[rank] + priorityBonus;
                    }
                }
            }
        }

        // 5. Run the Hungarian Algorithm
        System.out.println("Running Hungarian Maximum Weight Bipartite Matching for " 
            + nSlots + " slots across " + nStudents + " available students...");
        int[] matching = HungarianAlgorithm.findMaxWeightMatching(weights);

        // 6. Apply matches to courses and students
        Set<String> assignedStudentsInThisRun = new HashSet<>();

        for (int i = 0; i < nSlots; i++) {
            int studentIdx = matching[i];
            if (studentIdx >= 0 && studentIdx < nStudents && weights[i][studentIdx] > 0) {
                Student student = availableStudents.get(studentIdx);
                CourseSlot slot = slots.get(i);
                Course course = slot.course;

                // Safety guard against double assignment
                if (assignedStudentsInThisRun.contains(student.getRollNo())) {
                    continue;
                }
                assignedStudentsInThisRun.add(student.getRollNo());

                if (slot.slotNumber == 1) {
                    course.setTa1(student.getRollNo());
                } else if (slot.slotNumber == 2) {
                    course.setTa2(student.getRollNo());
                }

                student.setAvailable(false);
                student.setCourse(course.getCourseNo());
                studentRepository.save(student);

                System.out.println("  -> [Hungarian Optimal] Course " + course.getCourseNo() 
                    + " (TA " + slot.slotNumber + ") assigned to " + student.getRollNo() 
                    + " (" + student.getName() + ") with weight " + weights[i][studentIdx]);
            }
        }

        // 7. Save each processed course
        for (Course course : coursesToProcess) {
            courseRepository.save(course);
        }
        System.out.println("Hungarian Allocation successfully completed for Group " + groupId);
    }

    private double parseCgpa(String cgpaStr) {
        if (cgpaStr == null || cgpaStr.trim().isEmpty()) {
            return 7.0;
        }
        try {
            return Double.parseDouble(cgpaStr.trim());
        } catch (NumberFormatException e) {
            return 7.0;
        }
    }

    private String extractRollNo(String prefString) {
        if (prefString == null || prefString.trim().isEmpty() || DataUploadService.isInvalidPlaceholder(prefString)) {
            return null;
        }
        prefString = prefString.trim();
        if (prefString.contains(":")) {
            String candidate = prefString.split(":")[0].trim().toUpperCase();
            if (!DataUploadService.isInvalidPlaceholder(candidate)) {
                return candidate;
            }
        }
        if (prefString.contains("-")) {
            String candidate = prefString.split("-")[0].trim().toUpperCase();
            if (!DataUploadService.isInvalidPlaceholder(candidate)) {
                return candidate;
            }
        }
        // Match standard IITM roll number format (e.g. AE22B003, AE25M021, AE24D004)
        Pattern pattern = Pattern.compile("([A-Za-z]{2}[0-9]{2}[A-Za-z][0-9]{3})");
        Matcher matcher = pattern.matcher(prefString);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        return null;
    }

    @Transactional
    public void unassignTa(Long courseId, int slotNumber) {
        if (courseId == null) {
            throw new IllegalArgumentException("Course ID cannot be null");
        }
        Course course = findCourseByIdOrNo(courseId);
        if (course == null) {
            throw new IllegalArgumentException("Course not found with ID: " + courseId);
        }
        performUnassign(course, slotNumber);
    }

    @Transactional
    public void unassignTa(String courseNo, int slotNumber) {
        if (courseNo == null || courseNo.trim().isEmpty()) {
            throw new IllegalArgumentException("Course number cannot be blank");
        }
        Course course = courseRepository.findById(courseNo.trim())
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseNo));
        performUnassign(course, slotNumber);
    }

    private Course findCourseByIdOrNo(Long courseId) {
        // 1. Direct lookup by string representation of the courseId
        Optional<Course> courseOpt = courseRepository.findById(String.valueOf(courseId));
        if (courseOpt.isPresent()) {
            return courseOpt.get();
        }

        // 2. Lookup across all courses matching getId() or numeric portion of courseNo
        List<Course> allCourses = courseRepository.findAll();
        if (allCourses != null) {
            for (Course c : allCourses) {
                if (c.getId() != null && c.getId().equals(courseId)) {
                    return c;
                }
            }
            for (Course c : allCourses) {
                if (c.getCourseNo() != null) {
                    String digits = c.getCourseNo().replaceAll("\\D+", "");
                    if (!digits.isEmpty()) {
                        try {
                            if (Long.parseLong(digits) == courseId) {
                                return c;
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }
        return null;
    }

    private void performUnassign(Course course, int slotNumber) {
        String assignedTa;
        if (slotNumber == 1) {
            assignedTa = course.getAssignedTa1();
        } else if (slotNumber == 2) {
            assignedTa = course.getAssignedTa2();
        } else {
            throw new IllegalArgumentException("Invalid slot number: " + slotNumber + ". Must be 1 or 2.");
        }

        // If the slot is already null/blank, do nothing.
        if (assignedTa == null || assignedTa.trim().isEmpty()) {
            return;
        }

        // Parse the student's roll number from the assigned string (the text preceding the first colon ":")
        String rollNo = assignedTa.trim();
        if (rollNo.contains(":")) {
            rollNo = rollNo.split(":", 2)[0].trim();
        }

        // Clear slot
        if (slotNumber == 1) {
            course.setAssignedTa1(null);
        } else {
            course.setAssignedTa2(null);
        }
        courseRepository.save(course);

        // Reset availability safely: check if this student roll number is assigned to any other course in the database
        List<Course> allCourses = courseRepository.findAll();
        boolean assignedElsewhere = false;
        if (allCourses != null) {
            for (Course c : allCourses) {
                if (isStudentAssignedToCourse(c, rollNo)) {
                    assignedElsewhere = true;
                    break;
                }
            }
        }

        // If not assigned anywhere else, find Student via studentRepository.findByRollNoIgnoreCase() and set isAvailable(true)
        if (!assignedElsewhere) {
            Optional<Student> studentOpt = studentRepository.findByRollNoIgnoreCase(rollNo);
            if (studentOpt.isPresent()) {
                Student student = studentOpt.get();
                student.setAvailable(true);
                if (student.getCourse() != null && student.getCourse().equalsIgnoreCase(course.getCourseNo())) {
                    student.setCourse(null);
                }
                studentRepository.save(student);
            }
        }
    }

    private boolean isStudentAssignedToCourse(Course c, String rollNo) {
        return isRollNoMatch(c.getAssignedTa1(), rollNo) || isRollNoMatch(c.getAssignedTa2(), rollNo);
    }

    private boolean isRollNoMatch(String assignedString, String targetRollNo) {
        if (assignedString == null || targetRollNo == null) {
            return false;
        }
        if (DataUploadService.isInvalidPlaceholder(assignedString) || DataUploadService.isInvalidPlaceholder(targetRollNo)) {
            return false;
        }
        String roll = assignedString.trim();
        if (roll.contains(":")) {
            roll = roll.split(":", 2)[0].trim();
        }
        return roll.equalsIgnoreCase(targetRollNo.trim());
    }

    public List<AllocationPreviewDto> previewGroupAllocation(double groupId) {
        List<Course> courses = courseRepository.findByGroupId(groupId);
        if (courses == null || courses.isEmpty()) {
            return Collections.emptyList();
        }

        List<Student> allStudents = studentRepository.findAll();
        if (allStudents == null) {
            allStudents = Collections.emptyList();
        }

        Map<String, Student> rollToStudent = new HashMap<>();
        for (Student s : allStudents) {
            if (s.getRollNo() != null) {
                rollToStudent.put(s.getRollNo().trim().toUpperCase(), s);
            }
        }

        List<Student> availableStudents = allStudents.stream()
                .filter(Student::isAvailable)
                .toList();

        Map<String, Integer> rollToAvailableIdx = new HashMap<>();
        for (int j = 0; j < availableStudents.size(); j++) {
            String roll = availableStudents.get(j).getRollNo();
            if (roll != null) {
                rollToAvailableIdx.put(roll.trim().toUpperCase(), j);
            }
        }

        List<PreferenceResponse> allPrefs = preferenceRepository.findAll();
        if (allPrefs == null) {
            allPrefs = Collections.emptyList();
        }

        List<Course> coursesToProcess = new ArrayList<>();
        Map<String, PreferenceResponse> coursePrefMap = new HashMap<>();

        for (Course course : courses) {
            String cNo = (course.getCourseNo() != null) ? course.getCourseNo().trim() : "";
            List<PreferenceResponse> prefs = allPrefs.stream()
                    .filter(p -> p.getSelectCourse() != null && p.getSelectCourse().trim().equalsIgnoreCase(cNo))
                    .toList();

            if (prefs.isEmpty()) {
                continue;
            }

            PreferenceResponse pref = prefs.get(prefs.size() - 1);
            if (pref.getTaPreference1() != null && pref.getTaPreference1().toLowerCase().contains("do not want tas")) {
                continue;
            }

            coursePrefMap.put(cNo, pref);
            coursesToProcess.add(course);
        }

        if (coursesToProcess.isEmpty()) {
            return Collections.emptyList();
        }

        coursesToProcess.sort(Comparator.comparingInt(Course::getPriority));

        List<CourseSlot> slots = new ArrayList<>();
        for (Course course : coursesToProcess) {
            slots.add(new CourseSlot(course, 1));
            slots.add(new CourseSlot(course, 2));
        }

        int nSlots = slots.size();
        int nStudents = availableStudents.size();

        List<AllocationPreviewDto> previewList = new ArrayList<>();

        if (nStudents == 0) {
            for (CourseSlot slot : slots) {
                previewList.add(new AllocationPreviewDto(
                        slot.course.getCourseNo(),
                        slot.course.getCourseName(),
                        slot.course.getInstructorName(),
                        slot.slotNumber,
                        "Unassigned",
                        "-",
                        "Unranked",
                        0.0
                ));
            }
            return previewList;
        }

        double[][] weights = new double[nSlots][nStudents];
        int[][] rankMatrix = new int[nSlots][nStudents];

        for (int i = 0; i < nSlots; i++) {
            CourseSlot slot = slots.get(i);
            Course course = slot.course;
            String cNo = course.getCourseNo().trim();
            PreferenceResponse pref = coursePrefMap.get(cNo);

            double priorityBonus = Math.max(0, 100 - course.getPriority()) * 10.0;

            String[] taPrefs = {
                    pref.getTaPreference1(),
                    pref.getTaPreference2(),
                    pref.getTaPreference3(),
                    pref.getTaPreference4()
            };

            boolean isAllNoPreference = true;
            for (String pStr : taPrefs) {
                if (pStr != null && !pStr.trim().isEmpty() && !pStr.toLowerCase().contains("no preference")) {
                    isAllNoPreference = false;
                    break;
                }
            }

            if (isAllNoPreference && taPrefs[0] != null && taPrefs[0].toLowerCase().contains("no preference")) {
                for (int j = 0; j < nStudents; j++) {
                    Student s = availableStudents.get(j);
                    double cgpa = parseCgpa(s.getCgpa());
                    weights[i][j] = 1000.0 + (cgpa * 20.0) + priorityBonus;
                    rankMatrix[i][j] = 0;
                }
            } else {
                double[] choiceScores = { 10000.0, 7500.0, 5000.0, 2500.0 };
                for (int rank = 0; rank < taPrefs.length; rank++) {
                    String pStr = taPrefs[rank];
                    if (pStr == null || pStr.trim().isEmpty() || pStr.toLowerCase().contains("no preference")) {
                        continue;
                    }
                    String rollNo = extractRollNo(pStr);
                    if (rollNo != null && rollToAvailableIdx.containsKey(rollNo)) {
                        int sIdx = rollToAvailableIdx.get(rollNo);
                        weights[i][sIdx] = choiceScores[rank] + priorityBonus;
                        rankMatrix[i][sIdx] = rank + 1;
                    }
                }
            }
        }

        int[] matching = HungarianAlgorithm.findMaxWeightMatching(weights);
        Set<String> assignedStudentsInThisRun = new HashSet<>();

        for (int i = 0; i < nSlots; i++) {
            CourseSlot slot = slots.get(i);
            Course course = slot.course;
            int studentIdx = matching[i];

            if (studentIdx >= 0 && studentIdx < nStudents && weights[i][studentIdx] > 0) {
                Student student = availableStudents.get(studentIdx);
                if (!assignedStudentsInThisRun.contains(student.getRollNo())) {
                    assignedStudentsInThisRun.add(student.getRollNo());

                    int rankNum = rankMatrix[i][studentIdx];
                    String rankStr = (rankNum >= 1 && rankNum <= 4) ? "Choice " + rankNum : "Unranked";

                    previewList.add(new AllocationPreviewDto(
                            course.getCourseNo(),
                            course.getCourseName(),
                            course.getInstructorName(),
                            slot.slotNumber,
                            student.getRollNo(),
                            student.getName(),
                            rankStr,
                            weights[i][studentIdx]
                    ));
                    continue;
                }
            }

            previewList.add(new AllocationPreviewDto(
                    course.getCourseNo(),
                    course.getCourseName(),
                    course.getInstructorName(),
                    slot.slotNumber,
                    "Unassigned",
                    "-",
                    "Unranked",
                    0.0
            ));
        }

        return previewList;
    }

    @Transactional
    public void overrideAssignment(Long courseId, int slotNumber, String newStudentRollNo) {
        if (courseId == null) {
            throw new IllegalArgumentException("Course ID cannot be null");
        }
        Course course = findCourseByIdOrNo(courseId);
        if (course == null) {
            throw new IllegalArgumentException("Course not found with ID: " + courseId);
        }
        performOverride(course, slotNumber, newStudentRollNo);
    }

    @Transactional
    public void overrideAssignment(String courseNo, int slotNumber, String newStudentRollNo) {
        if (courseNo == null || courseNo.trim().isEmpty()) {
            throw new IllegalArgumentException("Course number cannot be blank");
        }
        Course course = courseRepository.findById(courseNo.trim())
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseNo));
        performOverride(course, slotNumber, newStudentRollNo);
    }

    private void performOverride(Course course, int slotNumber, String newStudentRollNo) {
        if (slotNumber != 1 && slotNumber != 2) {
            throw new IllegalArgumentException("Invalid slot number: " + slotNumber + ". Must be 1 or 2.");
        }

        // 1. If slot already had an assigned TA, set their Student.isAvailable back to true if not assigned elsewhere
        String priorAssigned = (slotNumber == 1) ? course.getAssignedTa1() : course.getAssignedTa2();
        if (priorAssigned != null && !DataUploadService.isInvalidPlaceholder(priorAssigned)) {
            String priorRoll = priorAssigned.trim();
            if (priorRoll.contains(":")) {
                priorRoll = priorRoll.split(":", 2)[0].trim();
            }

            // Clear current slot in memory first
            if (slotNumber == 1) {
                course.setAssignedTa1(null);
            } else {
                course.setAssignedTa2(null);
            }

            // Check if prior student is assigned to any other course
            List<Course> allCourses = courseRepository.findAll();
            boolean assignedElsewhere = false;
            if (allCourses != null) {
                for (Course c : allCourses) {
                    if (isStudentAssignedToCourse(c, priorRoll)) {
                        assignedElsewhere = true;
                        break;
                    }
                }
            }

            if (!assignedElsewhere) {
                Optional<Student> priorStudentOpt = studentRepository.findByRollNoIgnoreCase(priorRoll);
                if (priorStudentOpt.isPresent()) {
                    Student priorStudent = priorStudentOpt.get();
                    priorStudent.setAvailable(true);
                    if (priorStudent.getCourse() != null && priorStudent.getCourse().equalsIgnoreCase(course.getCourseNo())) {
                        priorStudent.setCourse(null);
                    }
                    studentRepository.save(priorStudent);
                }
            }
        }

        // 2. If newStudentRollNo is non-blank and not "NONE", assign new student
        if (newStudentRollNo != null && !newStudentRollNo.trim().isEmpty() && !newStudentRollNo.trim().equalsIgnoreCase("NONE")) {
            String cleanRoll = newStudentRollNo.trim();
            if (cleanRoll.contains(":")) {
                cleanRoll = cleanRoll.split(":", 2)[0].trim();
            }

            Optional<Student> newStudentOpt = studentRepository.findByRollNoIgnoreCase(cleanRoll);
            if (newStudentOpt.isEmpty()) {
                throw new IllegalArgumentException("Student not found with roll number: " + cleanRoll);
            }

            Student newStudent = newStudentOpt.get();
            newStudent.setAvailable(false);
            newStudent.setCourse(course.getCourseNo());
            studentRepository.save(newStudent);

            // Free new student from any other course if previously assigned elsewhere
            List<Course> allCourses = courseRepository.findAll();
            if (allCourses != null) {
                for (Course otherCourse : allCourses) {
                    if (otherCourse == null) continue;
                    boolean modified = false;
                    String targetCourseNo = course.getCourseNo();
                    boolean isSameCourse = (targetCourseNo != null && targetCourseNo.equalsIgnoreCase(otherCourse.getCourseNo()))
                            || (course.getId() != null && course.getId().equals(otherCourse.getId()));

                    if (isRollNoMatch(otherCourse.getAssignedTa1(), cleanRoll)) {
                        if (!isSameCourse || slotNumber != 1) {
                            otherCourse.setAssignedTa1(null);
                            modified = true;
                        }
                    }
                    if (isRollNoMatch(otherCourse.getAssignedTa2(), cleanRoll)) {
                        if (!isSameCourse || slotNumber != 2) {
                            otherCourse.setAssignedTa2(null);
                            modified = true;
                        }
                    }
                    if (modified && !isSameCourse) {
                        courseRepository.save(otherCourse);
                    }
                }
            }

            String name = newStudent.getName() != null ? newStudent.getName() : "";
            String cgpa = newStudent.getCgpa() != null ? newStudent.getCgpa() : "N/A";
            String prog = newStudent.getProgram() != null ? newStudent.getProgram() : "N/A";
            String formattedAssignment = newStudent.getRollNo() + ": " + name + " [" + cgpa + "] (" + prog + ")";

            if (slotNumber == 1) {
                course.setAssignedTa1(formattedAssignment);
            } else {
                course.setAssignedTa2(formattedAssignment);
            }
        } else {
            // "NONE" or empty clears the slot to null
            if (slotNumber == 1) {
                course.setAssignedTa1(null);
            } else {
                course.setAssignedTa2(null);
            }
        }

        courseRepository.save(course);
    }

    @Transactional
    public int rollbackGroupAllocations(double groupId) {
        List<Course> coursesInGroup = courseRepository.findByGroupId(groupId);
        if (coursesInGroup == null || coursesInGroup.isEmpty()) {
            return 0;
        }

        List<Course> allCourses = courseRepository.findAll();
        List<Course> coursesOutsideGroup = (allCourses != null)
                ? allCourses.stream().filter(c -> c.getGroupId() != groupId).toList()
                : Collections.emptyList();

        Set<String> releasedRollNumbers = new HashSet<>();

        for (Course course : coursesInGroup) {
            String ta1 = course.getAssignedTa1();
            if (ta1 != null && !DataUploadService.isInvalidPlaceholder(ta1)) {
                String rawRoll1 = ta1.trim();
                final String roll1 = rawRoll1.contains(":") ? rawRoll1.split(":", 2)[0].trim() : rawRoll1;
                boolean assignedOutside = coursesOutsideGroup.stream().anyMatch(oc -> isStudentAssignedToCourse(oc, roll1));
                if (!assignedOutside) {
                    releasedRollNumbers.add(roll1.toUpperCase());
                }
            }

            String ta2 = course.getAssignedTa2();
            if (ta2 != null && !DataUploadService.isInvalidPlaceholder(ta2)) {
                String rawRoll2 = ta2.trim();
                final String roll2 = rawRoll2.contains(":") ? rawRoll2.split(":", 2)[0].trim() : rawRoll2;
                boolean assignedOutside = coursesOutsideGroup.stream().anyMatch(oc -> isStudentAssignedToCourse(oc, roll2));
                if (!assignedOutside) {
                    releasedRollNumbers.add(roll2.toUpperCase());
                }
            }

            course.setAssignedTa1(null);
            course.setAssignedTa2(null);
            courseRepository.save(course);
        }

        for (String roll : releasedRollNumbers) {
            Optional<Student> studentOpt = studentRepository.findByRollNoIgnoreCase(roll);
            if (studentOpt.isPresent()) {
                Student s = studentOpt.get();
                s.setAvailable(true);
                s.setCourse(null);
                studentRepository.save(s);
            }
        }

        return releasedRollNumbers.size();
    }

    private static class CourseSlot {
        Course course;
        int slotNumber; // 1 or 2

        CourseSlot(Course course, int slotNumber) {
            this.course = course;
            this.slotNumber = slotNumber;
        }
    }
}
