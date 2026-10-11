package com.iitm.aero.taallocator.service;

import com.iitm.aero.taallocator.model.Course;
import com.iitm.aero.taallocator.model.PreferenceResponse;
import com.iitm.aero.taallocator.model.Student;
import com.iitm.aero.taallocator.repository.CourseRepository;
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
