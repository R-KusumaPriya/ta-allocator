# Engineering Prompts Log: TA Allocation System

This document logs the sequence of prompts used across the development lifecycle of the ta-allocator project.

---

## Phase 1: Project Setup & Domain Entities

### Prompt 1: Initial Maven Setup
I am setting up a new Spring Boot 3 project in Java 17 called ta-allocator for the IIT Madras Aerospace department. Can you generate the pom.xml for me? 

Please include dependencies for:
- Spring Web
- Spring Data JPA
- Thymeleaf
- Spring Mail
- H2 in-memory database
- OpenCSV 5.9
- spring-boot-starter-test (JUnit 5 and Mockito)

Keep it clean and don't add Lombok—I want standard explicit getters, setters, and constructors.

---

### Prompt 2: Core Course Entity
In package com.iitm.aero.taallocator.model, create a JPA entity Course.java mapped to the table "courses".

Fields needed:
- courseNo (String, primary key with @Id, e.g. "AS2010")
- courseName (String)
- instructorName (String)
- facultyEmail (String)
- semester (String)
- totalRegn (int, default to 0)
- priority (int, default to 0)
- groupId (double, default to 0.0)
- ta1 (String, roll number of assigned TA 1)
- ta2 (String, roll number of assigned TA 2)
- column1, column2, column3 (String, optional metadata placeholders)

Please add default and parameterized constructors, getters, setters, and a toString() method.

---

### Prompt 3: Student and Faculty Entities
Now let's create Student.java (table "students") and Faculty.java (table "faculty") in the same model package.

For Student:
- rollNo (String, @Id, like "AE22B001")
- name (String)
- cgpa (String)
- program (String, e.g. "B.Tech", "M.Tech", "Ph.D")
- course (String, for any pre-assigned course)
- isAvailable (boolean, default to true)

For Faculty:
- id (Long, auto-generated @Id)
- name (String)
- email (String)
- code (String)

Generate standard JPA annotations, constructors, getters, and setters for both.

---

### Prompt 4: Faculty Preference Response Entity
Create PreferenceResponse.java in com.iitm.aero.taallocator.model mapped to table "preferences" to store submissions from our Google Form where professors rank their TAs.

Fields needed:
- id (Long, auto-generated @Id)
- timestamp (String)
- emailAddress (String)
- facultyName (String)
- selectCourse (String, course code chosen from dropdown)
- taPreference1 (String, e.g. "AE22B001: Student Name" or "I do not want TAs.")
- taPreference2 (String)
- taPreference3 (String)
- taPreference4 (String)

Include standard getters, setters, and constructors.

---

### Prompt 5: Spring Data JPA Repositories
In package com.iitm.aero.taallocator.repository, create the Spring Data JPA repository interfaces for our models:

1. CourseRepository extends JpaRepository<Course, String>:
   - Add List<Course> findByGroupId(Double groupId);
2. StudentRepository extends JpaRepository<Student, String>:
   - Add List<Student> findByIsAvailableTrue();
3. FacultyRepository extends JpaRepository<Faculty, Long>:
   - Add Faculty findByNameIgnoreCase(String name);
4. PreferenceRepository extends JpaRepository<PreferenceResponse, Long>:
   - Add List<PreferenceResponse> findBySelectCourseIgnoreCase(String selectCourse);

---

## Phase 2: CSV Data Ingestion & Iterative Refinement

### Prompt 6: Basic CSV Parser for Courses
Create DataUploadService.java in com.iitm.aero.taallocator.service. 

Write a method:
public void parseAndSaveCourses(MultipartFile file) throws Exception

Requirements:
- Use OpenCSV to read the uploaded CSV
- Skip the header line
- Map each row to a Course object
- Save them all using courseRepository.saveAll()

Columns in CSV: id, name, faculty_name, faculty_email, priority, group_id.

---

### Prompt 7: Iterative Re-prompt – Dynamic Header Detection
I just tested parseAndSaveCourses with real department CSV files and it broke. One file had columns named "courseno" and "instructor" instead of "id" and "faculty_name", and the columns were in a completely different order.

Can you update the method so it reads the header row and dynamically finds the index of each column? 
- Normalize header strings by trimming and converting to lowercase
- Look for common aliases:
  - Course ID: "id", "courseno"
  - Course Name: "name", "coursename"
  - Instructor: "faculty_name", "instructor"
  - Email: "faculty_email", "email"
  - Priority: "priority"
  - Group ID: "group_id"
- If a header isn't found by name, fall back to default indexes (0, 1, 2, 3, 4, 5)

---

### Prompt 8: Iterative Re-prompt – Missing Cells & Bad Numbers
We hit two more edge cases in the CSVs:
1. Some rows don't have trailing cells (for example, no group_id is given at the end), which throws an ArrayIndexOutOfBoundsException.
2. One course had "N/A" for its priority, which crashed with a NumberFormatException.

Please update the code to:
- Check row.length before accessing an index
- Use a safe try-parse block that defaults bad numbers to 0 or 0.0 instead of crashing
- Trim all string fields
- Skip completely empty lines

---

### Prompt 9: Parsers for Students, Faculty, and Preferences
Now let's add the remaining parser methods in DataUploadService.java:

1. public void parseAndSaveStudents(MultipartFile file):
   - Columns: id, name, email, cgpa, program, course, and available
   - Availability rule: Student is available if available is "TRUE" and course is empty or "xxxx"
2. public void parseAndSaveFaculty(MultipartFile file):
   - Columns: name, email, and code
3. public void parseAndSavePreferences(MultipartFile file):
   - Columns: course_id, faculty_name, choice1, choice2, choice3, and choice4

Please use the exact same dynamic header detection and empty-row skipping logic we just built.

---

### Prompt 10: JUnit 5 & Mockito Tests for CSV Ingestion
Write a unit test class DataUploadServiceTest.java in src/test. Use Mockito to mock all four repositories. Using MockMultipartFile, write tests for:

- testParseAndSaveCourses_DynamicHeaders: verify that varied headers like "courseno" and "instructor" map properly
- testParseAndSaveCourses_MalformedNumericFieldsDefaultToZero: non-numeric priority defaults to 0 safely
- testParseAndSaveCourses_EmptyOrHeaderOnlyCSV: ensure header-only files don't save anything
- testParseAndSaveStudents_AvailabilityLogic: check that course="xxxx" stays available, while course="AS2100" is marked unavailable
- testParseAndSaveStudents_EmptyRowsSkipped: check that blank rows are ignored
- testParseAndSaveFaculty_Success and testParseAndSavePreferences_Success

---

## Phase 3: Google Forms Integration & Live Sync

### Prompt 11: Google Forms Service Skeleton
Create GoogleFormsService.java in com.iitm.aero.taallocator.service. I want to use the official Google Forms API v1 to automatically update course options on a live Google Form. 

Write a helper method private Forms getFormsService() that loads our Google Service Account credentials from the classpath file credentials.json. Just create the authentication client for now so we can make sure it compiles.

---

### Prompt 12: Dynamic Dropdown Synchronization
Now let's write public String updateForm(double groupId, String formId) in GoogleFormsService.java:

Requirements:
- Pull all courses from CourseRepository, get distinct course numbers, and sort them alphabetically
- Fetch all available students (findByIsAvailableTrue()), format them as "ROLLNO: Name [CGPA] (Program)", sort them, and add "No preference. You choose for me." as the first option
- Find the questions in the form titled "Select Course" and "TA Preference X", and use BatchUpdateFormRequest to update their dropdown choices
- Return a summary string like "Google Form successfully updated with X courses and Y available TAs" so we can show it on the dashboard

---

## Phase 4: Core Allocation Algorithm (Greedy Baseline)

### Prompt 13: Greedy Allocation Heuristic (v1)
Create AllocationService.java in com.iitm.aero.taallocator.service. Write public void allocateGroup(double groupId):

Logic:
1. Fetch all courses for this groupId from CourseRepository.
2. For each course, look up its PreferenceResponse from PreferenceRepository.
3. Check choices 1 through 4 in order. For each choice, if that student is found and student.isAvailable() == true:
   - Assign them to course.setTa1() (or course.setTa2() if ta1 is already taken)
   - Set student.setAvailable(false), student.setCourse(course.getCourseNo()), and save the student
   - Stop checking once the course has 2 TAs
4. Save the updated course.

---

### Prompt 14: Opt-Out Sentinel Token & Tolerant Roll Parsing
In AllocationService.java, we need to handle two edge cases from our department:

1. Some professors explicitly write "I do not want TAs" as their first choice. If taPreference1 contains "do not want tas" (case-insensitive), log it and immediately continue to the next course without assigning anyone.
2. In Google Form submissions, the student preference string is usually "AE22B001: John Doe", but sometimes someone types just "AE22B001". Write a helper extractRollNo(String prefString) that splits on ":" if present, or takes the first 8 characters, and trims whitespace. Also ensure all course and roll number comparisons are case-insensitive.

---

### Prompt 15: Unit Tests for Allocation Logic
Write AllocationServiceTest.java in src/test using JUnit 5 and Mockito. Mock CourseRepository, StudentRepository, and PreferenceRepository. 

Write tests for:
- testAllocateGroup_SuccessfulAllocation: assigns 2 TAs and marks students unavailable
- testAllocateGroup_DoNotWantTAs: course with "do not want TAs" skips allocation
- testAllocateGroup_StudentUnavailable: if choice 1 is already taken by another course, it correctly falls back to choice 2
- testAllocateGroup_RawRollNumberWithoutColon: parses an 8-character roll number without a colon
- testAllocateGroup_CaseInsensitiveCourseMatching: matches "  as5570 " to "AS5570"

---

## Phase 5: Debugging & Hungarian Combinatorial Optimization

### Prompt 16: "Explain First" Debugging Protocol
One of my unit tests in AllocationServiceTest failed with this output:

Wanted but not invoked: courseRepository.save(Course@733aa9d8) 
at AllocationServiceTest.testAllocateGroup_DoNotWantTAs (Line 97). 
Interactions with mock: courseRepository.findByGroupId(5.0d)

In my code, when "do not want tas" is matched, I have a continue; statement right before courseRepository.save(course);.

Don't write any new code yet. First explain why this failed, rank the top 2 solutions, and tell me which one is the cleaner software engineering design.

---

### Prompt 17: Hungarian Bipartite Matching Utility Class
The greedy allocation algorithm can get stuck in unfair local optima when two popular courses compete for the same top student. I want to add an optimal bipartite matching utility HungarianAlgorithm.java in com.iitm.aero.taallocator.util implementing the Kuhn-Munkres algorithm.

Requirements:
- Pure Java 17—do not use any external graph libraries
- Signature: public static int[] execute(double[][] costMatrix)
- Input is a rectangular matrix (course slots x candidate students). If rows != cols, pad with 0s to make it square
- We want to maximize total preference weight, so convert to min-cost by subtracting each weight from the matrix's maximum value
- Worst-case time complexity must be O(V^3)

Also write HungarianAlgorithmTest.java with a 3x3 test case and an asymmetric 4x2 matrix test.

---

### Prompt 18: Refactoring AllocationService with Exact Weight Formulation
Now let's upgrade AllocationService.allocateGroup() to use HungarianAlgorithm.java for global optimization.

Construct the bipartite weight matrix where rows are course slots (2 slots per course) and columns are available students in the group:
- Faculty Choice 1 weight: 10000.0
- Faculty Choice 2 weight: 7500.0
- Faculty Choice 3 weight: 5000.0
- Faculty Choice 4 weight: 2500.0
- Any valid unselected student weight: 100.0
- Add a tie-breaker: (student.getCgpa() * 10.0) so higher CGPA breaks ties
- If a professor selected "do not want tas", omit that course's slots from the matrix completely

Run the Hungarian algorithm, assign the optimal students to ta1 and ta2, mark them unavailable, and save.

---

## Phase 6: Automated Notifications & Email Dispatch

### Prompt 19: Two-Tier Email Dispatch Service
Create EmailService.java in com.iitm.aero.taallocator.service using JavaMailSender. We need two separate email operations:

1. public String sendPreferenceEmails(double groupId, String term, String deadline, String formLink): sends an email to every faculty teaching in this group with the Google Form link and the submission deadline.
2. public String sendConfirmationEmails(double groupId, String term): sends each faculty their final TA allotments with subject "[courseNo] [term] TA Allotment Confirmation" and lists TA 1 and TA 2.
3. To protect student privacy while testing, keep CC emails disabled.
4. Return a summary string counting how many emails were sent and how many failed.

---

### Prompt 20: Refactoring – Faculty Email Fuzzy Whitespace Resolution
In EmailService.java, some courses don't have a facultyEmail in their record because the schedule file didn't include it. Also, some instructor names have accidental spaces (like "Prof. John  Doe ").

Can you extract a helper private String resolveFacultyEmail(Course course)?
- First search facultyRepository.findByNameIgnoreCase()
- If not found, compare with all faculty by normalizing whitespace (replaceAll("\\s+", " ").toLowerCase())
- If still not found, fall back to course.getFacultyEmail()

Replace all direct email lookups with this helper.

---

## Phase 7: Web Admin Dashboard (Dual-Template Thymeleaf MVC)

### Prompt 21: AdminController Web Endpoints
Create AdminController.java in com.iitm.aero.taallocator.controller with these routes:
- GET /: loads total course count, student count, available student count, and a list of distinct groupIds, returning the "dashboard" view
- POST /upload/courses, /upload/students, /upload/faculty, /upload/preferences: file upload routes that call DataUploadService and redirect to "/" with flash messages
- GET /group?id={groupId}: loads courses for that group and returns "group-manager"
- POST /group/update-form: calls googleFormsService.updateForm(groupId, formId) and redirects
- POST /group/send-preference-emails: calls emailService.sendPreferenceEmails and redirects
- POST /group/allocate: calls allocationService.allocateGroup(groupId) and redirects
- POST /group/send-confirmation-emails: calls emailService.sendConfirmationEmails and redirects

---

### Prompt 22: Dual-Template Web UI (Dashboard & Group Manager)
In src/main/resources/templates/, create two clean Thymeleaf HTML pages:

1. dashboard.html:
   - Stats cards at the top (Total Courses, Total Students, Available Students)
   - Upload forms for all 4 CSV types
   - Cards for each Course Group with links to /group?id={groupId}
2. group-manager.html:
   - The control cockpit for a specific group
   - Action buttons for "Update Google Form", "Send Preference Invites", "Run Allocation", and "Send Confirmations"
   - Table of courses showing assigned TAs and color-coded status badges (Green = 2 TAs, Yellow = 1 TA, Gray = Pending)

Use a clean IIT Madras deep-blue theme (#1a5276) with vanilla CSS. Do not use external heavy frameworks.

---

### Prompt 23: CSV Export of Allocation Results
Faculty coordinators want to be able to download the final allocations as an Excel/CSV file from the web interface.

1. In DataUploadService.java, add:
   public void exportAllocationsCsv(Double groupId, Writer writer) throws Exception
   Writes rows with: Course ID, Course Name, Instructor, Instructor Email, TA 1, and TA 2.
2. In AdminController.java, add:
   GET /export/results?groupId={groupId}
   Sets Content-Type: text/csv and Content-Disposition: attachment; filename=ta_allocations_group_{id}.csv.

---

### Prompt 24: System Data Reset Endpoint
When testing or demoing to professors, we need a way to clear all tables and start fresh without restarting the Spring Boot server. 

Add an endpoint POST /admin/reset in AdminController.java that deletes all records from preferenceRepository, courseRepository, studentRepository, and facultyRepository, and redirects back to "/" with a flash message: "All data and allocations have been reset successfully."

---

## Phase 8: Hardening, Edge-Case Testing & Final Documentation

### Prompt 25: MockMvc Web Controller Tests
Create AdminControllerTest.java in src/test using @WebMvcTest(AdminController.class). Mock all services and repositories. 

Write tests verifying:
- testGetIndex_Returns200AndModelAttrs: asserts status 200, view name "dashboard", and model attributes
- testUploadCourses_Success: sends a MockMultipartFile, asserts a 302 redirect to "/" and flash message
- testRunAllocation_Success: calls POST /group/allocate?id=5.0 and verifies allocationService.allocateGroup(5.0) is invoked
- testResetAllData_Success: calls POST /admin/reset and verifies deleteAll() is called on all repositories

---

### Prompt 26: Git Hygiene, Secret Defense & UTF-16 .gitignore Remediation
GitHub Push Protection rejected my git push because it detected Google Cloud private keys in src/main/resources/credentials.json. When I checked .gitignore, PowerShell's Add-Content had saved it in UTF-16LE, so Git couldn't read the ignore rules.

1. Rewrite .gitignore in UTF-8 to properly ignore credentials.json, src/main/resources/credentials.json, and *.class.
2. Give me the exact Git commands to safely rewind my unpushed commit (git reset --soft origin/main), remove credentials.json from Git's cache without deleting it from my PC (git rm --cached), and commit safely.
