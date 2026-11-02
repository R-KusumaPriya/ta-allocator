package com.iitm.aero.taallocator.service;

import com.iitm.aero.taallocator.dto.AllocationPreviewDto;
import com.iitm.aero.taallocator.model.Course;
import com.iitm.aero.taallocator.model.Faculty;
import com.iitm.aero.taallocator.model.PreferenceResponse;
import com.iitm.aero.taallocator.model.Student;
import com.iitm.aero.taallocator.repository.CourseRepository;
import com.iitm.aero.taallocator.repository.FacultyRepository;
import com.iitm.aero.taallocator.repository.PreferenceRepository;
import com.iitm.aero.taallocator.repository.StudentRepository;
import com.iitm.aero.taallocator.util.HungarianAlgorithm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

    @Autowired(required = false)
    private FacultyRepository facultyRepository;

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
                    boolean isRestricted = isRestrictedStudentFaculty(s.getRollNo(), course, pref);
                    if (isRestricted) {
                        weights[i][j] = 0.0; // Student must not be allocated to this faculty member
                    } else {
                        double cgpa = parseCgpa(s.getCgpa());
                        weights[i][j] = 1000.0 + (cgpa * 20.0) + priorityBonus;
                    }
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
                        boolean isRestricted = isRestrictedStudentFaculty(rollNo, course, pref);
                        if (isRestricted) {
                            weights[i][sIdx] = 0.0; // Student must not be allocated to this faculty member
                        } else {
                            // Assign weight for this student
                            weights[i][sIdx] = choiceScores[rank] + priorityBonus;
                        }
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

                // Safety guard against restricted student-faculty allocation
                boolean isRestricted = isRestrictedStudentFaculty(student.getRollNo(), course, coursePrefMap.get(course.getCourseNo().trim()));
                if (isRestricted) {
                    System.out.println("  -> [Restriction Skipped] Student " + student.getRollNo() 
                        + " cannot be allocated to instructor " + course.getInstructorName());
                    continue;
                } else {
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
        }

        // 7. Save each processed course
        for (Course course : coursesToProcess) {
            courseRepository.save(course);
        }
        System.out.println("Hungarian Allocation successfully completed for Group " + groupId);
    }

    /**
     * Constraint check: ensures a particular student is not allocated to a given faculty member from faculty.csv.
     * For example, student with roll no. AE22B058 must not be assigned to Prof. Nidish.
     *
     * @param rollNo student roll number
     * @param course the course being allocated
     * @param pref the preference response for this course
     * @return true if the student is restricted from being allocated to this faculty member, false otherwise
     */
    boolean isRestrictedStudentFaculty(String rollNo, Course course, PreferenceResponse pref) {
        if (rollNo == null) {
            return false;
        }
        String cleanRoll = rollNo.trim().toUpperCase();

        String instructor = "";
        if (course != null && course.getInstructorName() != null && !course.getInstructorName().trim().isEmpty()) {
            instructor = course.getInstructorName().trim().toLowerCase();
        } else if (pref != null && pref.getFacultyName() != null && !pref.getFacultyName().trim().isEmpty()) {
            instructor = pref.getFacultyName().trim().toLowerCase();
        }

        if (facultyRepository != null && course != null && course.getInstructorName() != null) {
            try {
                Faculty f = facultyRepository.findByNameIgnoreCase(course.getInstructorName().trim());
                if (f != null && f.getName() != null) {
                    instructor = f.getName().trim().toLowerCase();
                }
            } catch (Exception ignored) {
            }
        }

        // Variable with if-else condition to ensure student is not allocated
        boolean isRestricted;
        if (cleanRoll.equals("AE22B058") && instructor.contains("nidish")) {
            isRestricted = true;
        } else {
            isRestricted = false;
        }

        return isRestricted;
    }

    /**
     * Unassigns a TA from a specific course slot and safely restores the student's availability flag.
     *
     * @param courseNo the course number (ID) of the course
     * @param slotNumber 1 for ta1, 2 for ta2
     */
    @org.springframework.transaction.annotation.Transactional
    public void unassignTa(String courseNo, int slotNumber) {
        if (courseNo == null || courseNo.trim().isEmpty()) {
            throw new IllegalArgumentException("Course number cannot be null or empty");
        }
        Course course = courseRepository.findById(courseNo.trim())
                .orElseThrow(() -> new IllegalArgumentException("Course not found with Course No: " + courseNo));

        String removedTa = null;
        if (slotNumber == 1) {
            removedTa = course.getTa1();
            course.setTa1(null);
        } else if (slotNumber == 2) {
            removedTa = course.getTa2();
            course.setTa2(null);
        } else {
            throw new IllegalArgumentException("Invalid slot number: " + slotNumber + ". Must be 1 or 2.");
        }
        courseRepository.save(course);

        if (removedTa != null && !removedTa.trim().isEmpty() && !removedTa.equalsIgnoreCase("xxxx")) {
            String rollNo = extractRollNo(removedTa);
            if (rollNo == null || rollNo.trim().isEmpty()) {
                rollNo = removedTa.trim();
            }
            // Check if this student is still assigned to ANY other course
            boolean stillAssigned = false;
            List<Course> allCourses = courseRepository.findAll();
            for (Course c : allCourses) {
                if (c.getTa1() != null && isSameStudent(c.getTa1(), rollNo)) {
                    stillAssigned = true;
                    break;
                }
                if (c.getTa2() != null && isSameStudent(c.getTa2(), rollNo)) {
                    stillAssigned = true;
                    break;
                }
            }
            if (!stillAssigned) {
                Student s = findStudentByRollNo(rollNo);
                if (s != null) {
                    s.setAvailable(true);
                    s.setCourse(null);
                    studentRepository.save(s);
                }
            }
        }
    }

    private Student findStudentByRollNo(String rollNo) {
        if (rollNo == null || rollNo.trim().isEmpty()) return null;
        Optional<Student> sOpt = studentRepository.findById(rollNo.trim());
        if (sOpt.isPresent()) return sOpt.get();
        for (Student cand : studentRepository.findAll()) {
            if (cand.getRollNo() != null && cand.getRollNo().equalsIgnoreCase(rollNo.trim())) {
                return cand;
            }
        }
        return null;
    }

    private boolean isSameStudent(String taField, String targetRollNo) {
        if (taField == null || targetRollNo == null) return false;
        String extracted = extractRollNo(taField);
        if (extracted == null || extracted.isEmpty()) {
            extracted = taField.trim();
        }
        return extracted.equalsIgnoreCase(targetRollNo.trim());
    }

    /**
     * Manually overrides/assigns a TA to a specific course slot.
     * If a student was previously assigned to this slot, their availability is restored.
     * If newStudentRollNo is provided, that student is locked (isAvailable=false) and assigned.
     *
     * @param courseNo the course number
     * @param slotNumber 1 for ta1, 2 for ta2
     * @param newStudentRollNo the roll number of the new student (or "NONE" to clear)
     */
    @org.springframework.transaction.annotation.Transactional
    public void overrideAssignment(String courseNo, int slotNumber, String newStudentRollNo) {
        if (courseNo == null || courseNo.trim().isEmpty()) {
            throw new IllegalArgumentException("Course number cannot be null or empty");
        }
        Course course = courseRepository.findById(courseNo.trim())
                .orElseThrow(() -> new IllegalArgumentException("Course not found with Course No: " + courseNo));

        String oldTa = (slotNumber == 1) ? course.getTa1() : (slotNumber == 2 ? course.getTa2() : null);
        if (slotNumber != 1 && slotNumber != 2) {
            throw new IllegalArgumentException("Invalid slot number: " + slotNumber + ". Must be 1 or 2.");
        }

        // 1. If an old student was in this slot, check if we need to restore their availability
        if (oldTa != null && !oldTa.trim().isEmpty() && !oldTa.equalsIgnoreCase("xxxx")) {
            String oldRollNo = extractRollNo(oldTa);
            if (oldRollNo == null || oldRollNo.trim().isEmpty()) {
                oldRollNo = oldTa.trim();
            }
            boolean stillAssigned = false;
            for (Course c : courseRepository.findAll()) {
                if (c.getCourseNo().equalsIgnoreCase(course.getCourseNo())) {
                    String otherSlot = (slotNumber == 1) ? c.getTa2() : c.getTa1();
                    if (otherSlot != null && isSameStudent(otherSlot, oldRollNo)) {
                        stillAssigned = true;
                        break;
                    }
                } else {
                    if (c.getTa1() != null && isSameStudent(c.getTa1(), oldRollNo)) {
                        stillAssigned = true;
                        break;
                    }
                    if (c.getTa2() != null && isSameStudent(c.getTa2(), oldRollNo)) {
                        stillAssigned = true;
                        break;
                    }
                }
            }
            if (!stillAssigned) {
                Student oldStudent = findStudentByRollNo(oldRollNo);
                if (oldStudent != null) {
                    oldStudent.setAvailable(true);
                    oldStudent.setCourse(null);
                    studentRepository.save(oldStudent);
                }
            }
        }

        // 2. Assign new student or clear
        if (newStudentRollNo == null || newStudentRollNo.trim().isEmpty() || newStudentRollNo.equalsIgnoreCase("NONE")) {
            if (slotNumber == 1) course.setTa1(null);
            else course.setTa2(null);
        } else {
            Student newStudent = findStudentByRollNo(newStudentRollNo.trim());
            if (newStudent != null) {
                newStudent.setAvailable(false);
                newStudent.setCourse(course.getCourseNo());
                studentRepository.save(newStudent);
                String formatted = newStudent.getRollNo() + ": " + newStudent.getName() + 
                    (newStudent.getCgpa() != null ? " [" + newStudent.getCgpa() + "]" : "") + 
                    (newStudent.getProgram() != null ? " (" + newStudent.getProgram() + ")" : "");
                if (slotNumber == 1) course.setTa1(formatted);
                else course.setTa2(formatted);
            } else {
                if (slotNumber == 1) course.setTa1(newStudentRollNo.trim());
                else course.setTa2(newStudentRollNo.trim());
            }
        }
        courseRepository.save(course);
    }

    /**
     * Rolls back / undoes all TA allocations for courses in a specific group.
     * Clears assignedTa1 and assignedTa2 for every course in this group,
     * and safely restores the isAvailable=true flag for affected students (provided
     * they are not allocated to any other active course).
     *
     * @param groupId the group id to rollback
     * @return count of courses cleared
     */
    @org.springframework.transaction.annotation.Transactional
    public int rollbackGroupAllocations(double groupId) {
        List<Course> groupCourses = courseRepository.findByGroupId(groupId);
        if (groupCourses == null || groupCourses.isEmpty()) {
            return 0;
        }

        Set<String> releasedRollNos = new HashSet<>();
        for (Course course : groupCourses) {
            if (course.getTa1() != null && !course.getTa1().trim().isEmpty() && !course.getTa1().equalsIgnoreCase("xxxx")) {
                String r1 = extractRollNo(course.getTa1());
                if (r1 != null && !r1.trim().isEmpty()) releasedRollNos.add(r1.trim());
            }
            if (course.getTa2() != null && !course.getTa2().trim().isEmpty() && !course.getTa2().equalsIgnoreCase("xxxx")) {
                String r2 = extractRollNo(course.getTa2());
                if (r2 != null && !r2.trim().isEmpty()) releasedRollNos.add(r2.trim());
            }
            course.setTa1(null);
            course.setTa2(null);
            courseRepository.save(course);
        }

        List<Course> otherCourses = courseRepository.findAll().stream()
                .filter(c -> c.getGroupId() != groupId)
                .toList();

        for (String rollNo : releasedRollNos) {
            boolean assignedElsewhere = false;
            for (Course oc : otherCourses) {
                if (oc.getTa1() != null && isSameStudent(oc.getTa1(), rollNo)) {
                    assignedElsewhere = true;
                    break;
                }
                if (oc.getTa2() != null && isSameStudent(oc.getTa2(), rollNo)) {
                    assignedElsewhere = true;
                    break;
                }
            }
            if (!assignedElsewhere) {
                Student s = findStudentByRollNo(rollNo);
                if (s != null) {
                    s.setAvailable(true);
                    s.setCourse(null);
                    studentRepository.save(s);
                }
            }
        }
        return groupCourses.size();
    }

    /**
     * Dry-run simulation of Hungarian allocation for a group without persisting changes to DB.
     *
     * @param groupId the group id to preview
     * @return list of preview assignment DTOs
     */
    public List<AllocationPreviewDto> previewGroupAllocation(double groupId) {
        List<Course> courses = courseRepository.findByGroupId(groupId);
        List<AllocationPreviewDto> previewList = new ArrayList<>();
        if (courses == null || courses.isEmpty()) {
            return previewList;
        }

        List<Student> allStudents = studentRepository.findAll();
        if (allStudents == null) {
            allStudents = Collections.emptyList();
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
            return previewList;
        }

        coursesToProcess.sort(Comparator.comparingInt(Course::getPriority));

        List<CourseSlot> slots = new ArrayList<>();
        for (Course course : coursesToProcess) {
            slots.add(new CourseSlot(course, 1));
            slots.add(new CourseSlot(course, 2));
        }

        int nSlots = slots.size();
        int nStudents = availableStudents.size();

        if (nStudents == 0) {
            for (CourseSlot slot : slots) {
                previewList.add(new AllocationPreviewDto(
                    slot.course.getCourseNo(),
                    slot.course.getCourseName(),
                    slot.course.getInstructorName(),
                    slot.slotNumber,
                    "Unassigned",
                    "-",
                    "No Available Students",
                    0.0
                ));
            }
            return previewList;
        }

        double[][] weights = new double[nSlots][nStudents];
        Map<String, String> slotStudentRankMap = new HashMap<>();

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
                    boolean isRestricted = isRestrictedStudentFaculty(s.getRollNo(), course, pref);
                    if (isRestricted) {
                        weights[i][j] = 0.0;
                    } else {
                        double cgpa = parseCgpa(s.getCgpa());
                        weights[i][j] = 1000.0 + (cgpa * 20.0) + priorityBonus;
                        slotStudentRankMap.put(i + "-" + j, "No Preference (Auto)");
                    }
                }
            } else {
                double[] choiceScores = { 10000.0, 7500.0, 5000.0, 2500.0 };
                String[] choiceLabels = { "Choice 1", "Choice 2", "Choice 3", "Choice 4" };

                for (int rank = 0; rank < taPrefs.length; rank++) {
                    String pStr = taPrefs[rank];
                    if (pStr == null || pStr.trim().isEmpty() || pStr.toLowerCase().contains("no preference")) {
                        continue;
                    }
                    String rollNo = extractRollNo(pStr);
                    if (rollNo != null && rollToAvailableIdx.containsKey(rollNo)) {
                        int sIdx = rollToAvailableIdx.get(rollNo);
                        boolean isRestricted = isRestrictedStudentFaculty(rollNo, course, pref);
                        if (isRestricted) {
                            weights[i][sIdx] = 0.0;
                        } else {
                            weights[i][sIdx] = choiceScores[rank] + priorityBonus;
                            slotStudentRankMap.put(i + "-" + sIdx, choiceLabels[rank]);
                        }
                    }
                }
            }
        }

        int[] matching = HungarianAlgorithm.findMaxWeightMatching(weights);
        Set<String> assignedInRun = new HashSet<>();

        for (int i = 0; i < nSlots; i++) {
            CourseSlot slot = slots.get(i);
            Course course = slot.course;
            int sIdx = matching[i];

            if (sIdx >= 0 && sIdx < nStudents && weights[i][sIdx] > 0) {
                Student student = availableStudents.get(sIdx);
                boolean isRestricted = isRestrictedStudentFaculty(student.getRollNo(), course, coursePrefMap.get(course.getCourseNo().trim()));
                if (isRestricted || assignedInRun.contains(student.getRollNo())) {
                    previewList.add(new AllocationPreviewDto(
                        course.getCourseNo(),
                        course.getCourseName(),
                        course.getInstructorName(),
                        slot.slotNumber,
                        "Unassigned",
                        "-",
                        "Conflict / Restricted",
                        0.0
                    ));
                } else {
                    assignedInRun.add(student.getRollNo());
                    String rankLabel = slotStudentRankMap.getOrDefault(i + "-" + sIdx, "Ranked Match");
                    previewList.add(new AllocationPreviewDto(
                        course.getCourseNo(),
                        course.getCourseName(),
                        course.getInstructorName(),
                        slot.slotNumber,
                        student.getRollNo(),
                        student.getName(),
                        rankLabel,
                        weights[i][sIdx]
                    ));
                }
            } else {
                previewList.add(new AllocationPreviewDto(
                    course.getCourseNo(),
                    course.getCourseName(),
                    course.getInstructorName(),
                    slot.slotNumber,
                    "Unassigned",
                    "-",
                    "Unmatched Slot",
                    0.0
                ));
            }
        }
        return previewList;
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
        if (prefString == null || prefString.trim().isEmpty()) {
            return null;
        }
        prefString = prefString.trim();
        if (prefString.contains(":")) {
            return prefString.split(":")[0].trim().toUpperCase();
        }
        if (prefString.contains("-")) {
            return prefString.split("-")[0].trim().toUpperCase();
        }
        // Match standard IITM roll number format (e.g. AE22B003, AE25M021, AE24D004)
        Pattern pattern = Pattern.compile("([A-Za-z]{2}[0-9]{2}[A-Za-z][0-9]{3})");
        Matcher matcher = pattern.matcher(prefString);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        if (prefString.length() >= 8) {
            return prefString.substring(0, 8).trim().toUpperCase();
        }
        return prefString.toUpperCase();
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
