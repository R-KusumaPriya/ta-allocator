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

---

## Phase 9: Operational Cockpit & Human-in-the-Loop Controls

### Prompt 27: Student-Faculty Allocation Constraint (Hungarian Viva Modification)
In AllocationService.java, we need to enforce a departmental constraint during Hungarian matching: certain students should not be allocated to specific professors (e.g., student AE22B058 must never be assigned to Prof. Nidish).

1. Add a helper method isRestrictedStudentFaculty(String rollNo, Course course, PreferenceResponse pref) that returns true if rollNo is "AE22B058" (case-insensitive) and the instructor name contains "nidish".
2. In the weight matrix formulation inside allocateGroup(), if isRestrictedStudentFaculty returns true for a student-course pair, zero out their weight (weights[i][j] = 0.0) so Kuhn-Munkres never matches them.
3. Add a defensive post-matching guard during match assignment to skip the assignment if restricted.
4. Add JUnit 5 tests in AllocationServiceTest verifying that AE22B058 is never assigned to Prof. Nidish under explicit preference or auto-allocation.

---

### Prompt 28: Interactive TA Unassignment and Availability Reset (Feature 2)
You are a senior Java 17 and Spring Boot 3 engineer working on the ta-allocator codebase.

1. CONTEXT & FILE LOCATIONS:
- Target files:
  * Service: src/main/java/com/iitm/aero/taallocator/service/AllocationService.java
  * Controller: src/main/java/com/iitm/aero/taallocator/controller/AdminController.java
  * Template: src/main/resources/templates/group-manager.html
  * Tests: AllocationServiceTest.java
- Currently, when TAs are allocated, Course.ta1 / ta2 are stored as strings (e.g., "AE22B001: John Doe"), and Student.isAvailable is toggled to false. To remove a TA, admins currently have to edit raw CSVs or SQL tables manually.

2. TASK / NEW REQUIREMENT:
- Implement interactive unassignment of a TA from the group manager UI:
  1. In AllocationService.java, add a method:
     @Transactional
     public void unassignTa(String courseNo, int slotNumber)
  2. In AdminController.java, add a POST endpoint:
     @PostMapping("/group/unassign")
     taking @RequestParam("id") double groupId, @RequestParam("courseNo") String courseNo, and @RequestParam("slot") int slot.
  3. In group-manager.html, in the course allocation table, next to assigned TA 1 and TA 2, render a small red "✕" button that posts to this endpoint with a confirmation prompt.

3. HARD CONSTRAINTS:
- Pure Java 17 and Spring Boot 3 only. Do not add any new external libraries or Maven dependencies.
- No Lombok; use existing standard getters and setters.
- Maintain backward compatibility with all existing JUnit tests.

4. DEFENSIVE INVARIANTS & EDGE CASES:
- Check that the course exists; throw IllegalArgumentException if not found.
- If slotNumber is 1, clear course.ta1; if 2, clear course.ta2.
- Extract the student's roll number from the assigned string.
- Reset availability safely: check if this student roll number is assigned to any other course in the database. If not assigned anywhere else, find the Student entity and set isAvailable(true).
- Annotate unassignTa with @Transactional to ensure atomic database commit.

5. OUTPUT FORMAT & VERIFICATION:
- Provide the Java code for AllocationService.java and AdminController.java, and the updated snippet for group-manager.html.
- Provide a JUnit 5 test in AllocationServiceTest.java verifying that unassigning a TA clears the course slot and restores student availability.

---

### Prompt 29: Allocation Preview & Dry-Run Simulation Mode (Feature 6)
You are a senior Java 17 and Spring Boot 3 engineer working on the ta-allocator codebase.

1. CONTEXT & FILE LOCATIONS:
- Target files:
  * DTO (New): src/main/java/com/iitm/aero/taallocator/dto/AllocationPreviewDto.java
  * Service: src/main/java/com/iitm/aero/taallocator/service/AllocationService.java
  * Controller: src/main/java/com/iitm/aero/taallocator/controller/AdminController.java
  * Template: src/main/resources/templates/group-manager.html
  * Tests: AllocationServiceTest.java
- Currently, clicking "Run Allocation" immediately commits matches to the database. Administrators want to preview what the Hungarian Algorithm proposes (along with scores and match ranks) before committing changes.

2. TASK / NEW REQUIREMENT:
- Implement an in-memory "Preview / Dry Run" feature:
  1. Create a lightweight DTO: AllocationPreviewDto.java with fields:
     courseNo, courseName, instructorName, slotNumber, matchedRollNo, matchedStudentName, preferenceRank (Choice 1/2/3/4/Unranked), matchScore. Explicit getters and setters.
  2. In AllocationService.java, implement:
     public List<AllocationPreviewDto> previewGroupAllocation(double groupId)
     This builds the exact cost matrix and runs Kuhn-Munkres, but does NOT call courseRepository.save() or mutate student.isAvailable in the DB.
  3. In AdminController.java, add:
     @GetMapping("/group/preview")
     which calls previewGroupAllocation(groupId), places "previewResults" into the Model, and re-renders group-manager.html.
  4. In group-manager.html:
     Render a "Simulated Allocation Preview" table showing the projected pairings with a "Confirm & Commit Allocation" button that triggers the actual persistent allocation.

3. HARD CONSTRAINTS:
- Database state (Course and Student tables) must remain completely unmodified when running preview mode.
- Pure Java 17; no Lombok.
- Handle unassigned slots or restricted candidates cleanly without throwing ArrayIndexOutOfBoundsException.

4. DEFENSIVE INVARIANTS & EDGE CASES:
- Ensure the preview reads from the current snapshot of available students (isAvailable == true).
- If no preferences exist or a slot cannot be filled, mark slot as "Unassigned" with score 0.0.

5. OUTPUT FORMAT & VERIFICATION:
- Output AllocationPreviewDto.java, previewGroupAllocation in AllocationService.java, the controller endpoint, and the HTML table in group-manager.html.
- Provide a JUnit test asserting that previewGroupAllocation returns the expected matches while studentRepository.save() is never called and availability remains untouched.

---

### Prompt 30: Manual TA Assignment Override and Swap Capability (Feature 3)
You are a senior Java 17 and Spring Boot 3 engineer working on the ta-allocator codebase.

1. CONTEXT & FILE LOCATIONS:
- Target files:
  * Service: src/main/java/com/iitm/aero/taallocator/service/AllocationService.java
  * Controller: src/main/java/com/iitm/aero/taallocator/controller/AdminController.java
  * Template: src/main/resources/templates/group-manager.html
  * Tests: AllocationServiceTest.java
- Department admins need human-in-the-loop control to manually swap or reassign a TA to a course slot after reviewing automated Hungarian results.

2. TASK / NEW REQUIREMENT:
- Implement a manual TA override/assignment feature:
  1. In AllocationService.java, implement:
     @Transactional
     public void overrideAssignment(String courseNo, int slotNumber, String newStudentRollNo)
     Logic:
     - If the target slot already had a student, restore their Student.isAvailable = true (if not assigned elsewhere).
     - If newStudentRollNo is non-blank and not "NONE", retrieve new student, set isAvailable = false, and assign them formatted as "ROLLNO: Name [CGPA] (Program)" to course.ta1 or ta2.
     - If newStudentRollNo is "NONE" or empty, clear the slot to null.
  2. In AdminController.java:
     - Pass studentRepository.findByIsAvailableTrue() as "availableStudents" to the Model in all group manager GET endpoints.
     - Add endpoint @PostMapping("/group/override-ta") accepting id, courseNo, slot, and newRollNo.
  3. In group-manager.html:
     Add an inline expandable dropdown (<details>) next to each slot allowing selection of any available student to override the slot.

3. HARD CONSTRAINTS:
- Standard Spring Data JPA and Java 17 only.
- Preserve transactional consistency with @Transactional.

4. DEFENSIVE INVARIANTS & EDGE CASES:
- Prevent duplicate assignment conflicts.
- Safely extract roll numbers and handle cases where a student was already assigned elsewhere.

5. OUTPUT FORMAT & VERIFICATION:
- Provide methods in AllocationService.java, AdminController.java, and group-manager.html.
- Add a JUnit test in AllocationServiceTest verifying that overriding a slot frees the previous student and locks the new student.

---

### Prompt 31: Allocation Rollback / Undo Capability per Group (Feature 5)
You are a senior Java 17 and Spring Boot 3 engineer working on the ta-allocator codebase.

1. CONTEXT & FILE LOCATIONS:
- Target files:
  * Service: src/main/java/com/iitm/aero/taallocator/service/AllocationService.java
  * Controller: src/main/java/com/iitm/aero/taallocator/controller/AdminController.java
  * Template: src/main/resources/templates/group-manager.html
  * Tests: AllocationServiceTest.java
- Currently, running an allocation for a group commits course.ta1 and ta2 and flips student availability flags. There is no automated way to rollback this group's allocations if results need to be re-run.

2. TASK / NEW REQUIREMENT:
- Implement an Undo/Rollback feature for a specific group:
  1. In AllocationService.java, add:
     @Transactional
     public int rollbackGroupAllocations(double groupId)
     which clears all TA assignments made for courses belonging to that specific groupId and restores availability flags for the affected students.
  2. In AdminController.java, add:
     @PostMapping("/group/rollback")
     which invokes rollbackGroupAllocations(groupId) and redirects to /group?id={groupId} with a flash message: "Allocations for Group X successfully rolled back and students released."
  3. In group-manager.html, add a secondary button: "Undo / Rollback Allocations" with a confirmation dialog.

3. HARD CONSTRAINTS:
- Do not affect courses or students assigned in other groups (e.g., rolling back Group 1 must NOT touch Group 0 assignments).
- Do not delete preference submissions or course records—only wipe ta1, ta2, and restore availability.

4. DEFENSIVE INVARIANTS & EDGE CASES:
- Safely extract roll numbers from ta1 and ta2. Handle null or malformed strings without throwing exceptions.
- Before marking a student isAvailable = true, verify that the student is not assigned to any other course outside this group.

5. OUTPUT FORMAT & VERIFICATION:
- Provide the service method, controller endpoint, and HTML button snippet.
- Add a JUnit test in AllocationServiceTest.java verifying that rollbackGroupAllocations clears courses and sets students back to available.

---

### Prompt 32: Email Template Customization from Dashboard (Feature 8)
You are a senior Java 17 and Spring Boot 3 engineer working on the ta-allocator codebase.

1. CONTEXT & FILE LOCATIONS:
- Target files:
  * Service: src/main/java/com/iitm/aero/taallocator/service/EmailService.java
  * Controller: src/main/java/com/iitm/aero/taallocator/controller/AdminController.java
  * Template: src/main/resources/templates/group-manager.html
  * Tests: EmailServiceTest.java
- Currently, email subjects, deadline text, and body messages in EmailService.java are hardcoded strings. The coordinator needs to customize deadline dates and special instructions directly from the dashboard before dispatching emails.

2. TASK / NEW REQUIREMENT:
- Add customizable email parameters to the email dispatch workflow:
  1. In EmailService.java, add overloaded methods:
     public String sendPreferenceEmails(double groupId, String termString, String deadline, String formLink, String customNote)
     public String sendConfirmationEmails(double groupId, String termString, String customInstructions)
     Keep existing 4-arg and 2-arg versions intact as backward-compatible wrappers.
     Dynamically inject customNote and customInstructions into the email bodies when non-empty.
  2. In AdminController.java:
     Update @PostMapping("/group/send-preference-emails") and @PostMapping("/group/send-confirmation-emails") to accept optional @RequestParam strings (customNote, customInstructions) with fallbacks.
  3. In group-manager.html:
     Add optional input fields for "Custom Note" in Step 2 and "Special Instructions" in Step 5.

3. HARD CONSTRAINTS:
- Do not break existing method calls; keep backward-compatible overloaded methods so existing tests continue passing.
- Maintain existing whitespace normalization and faculty email resolution logic.

4. DEFENSIVE INVARIANTS & EDGE CASES:
- If customNote or customInstructions are null or blank, omit the extra section cleanly.
- Handle SMTP errors gracefully without crashing the web session.

5. OUTPUT FORMAT & VERIFICATION:
- Provide updated methods in EmailService.java, AdminController.java, and form inputs in group-manager.html.
- Add JUnit tests in EmailServiceTest.java verifying that custom notes and instructions appear inside dispatched email messages.

---

## Phase 8: Comprehensive Unit Testing & Algorithmic Verification

### Prompt 33: Comprehensive Controller Edge-Case Unit Tests for AdminController
You are a senior Java 17 and Spring Boot 3 engineer working on the ta-allocator codebase.

1. CONTEXT & FILE LOCATIONS:
- Target Test File:
  * Controller Test: src/test/java/com/iitm/aero/taallocator/controller/AdminControllerTest.java
- Source File:
  * Controller: src/main/java/com/iitm/aero/taallocator/controller/AdminController.java
- Technologies: JUnit 5, Mockito, MockMvc (standalone setup), pure Java 17.

2. TASK / NEW REQUIREMENT:
Expand the unit test coverage for AdminControllerTest to cover all previously untested endpoints and edge cases:
- testResetAllData_Success(): Mocks repositories, invokes POST /admin/reset, verifies deleteAll() called across preference, course, student, and faculty repositories, redirects to "/", and verifies flash message.
- testResetAllData_ExceptionHandled(): Simulates repository exception during /admin/reset, verifies redirect to "/" with flash error.
- testUploadStudents_Success() & testUploadStudents_Exception(): MultiPart POST /upload/students.
- testUploadFaculty_Success() & testUploadFaculty_Exception(): MultiPart POST /upload/faculty.
- testUploadPreferences_Success() & testUploadPreferences_Exception(): MultiPart POST /upload/preferences.
- testPreviewAllocation(): GET /group/preview?id=1.0, asserts model attributes ("groupId", "courses", "previewResults", "availableStudents"), returns view "group-manager".
- testOverrideTa_Success() & testOverrideTa_Exception(): POST /group/override-ta with parameters (id, courseNo, slot, newRollNo).
- testRollbackGroupAllocations_Success() & testRollbackGroupAllocations_Exception(): POST /group/rollback with parameter (id).
- testUnassignTa_Success() & testUnassignTa_Exception(): POST /group/unassign with parameters (id, courseNo, slot).
- testSendPreferenceEmails_WithCustomNote(): POST /group/send-preference-emails passing customNote.
- testSendConfirmationEmails_WithCustomInstructions(): POST /group/send-confirmation-emails passing customInstructions.

3. HARD CONSTRAINTS:
- Keep the existing MockMvc standalone setup (MockMvcBuilders.standaloneSetup(...)).
- Do not add Spring context slices like @WebMvcTest. Keep tests fast, isolated, and mocking dependencies.
- Pure Java 17 and Mockito; do not introduce Lombok or external libraries.

4. DEFENSIVE INVARIANTS & EDGE CASES:
- Verify appropriate flash attributes ("message" vs "error") and redirection URLs across both success and failure execution paths.

5. OUTPUT FORMAT & VERIFICATION:
- Provide the complete updated AdminControllerTest.java file.
- Ensure all tests pass cleanly with .\mvnw.cmd test -Dtest=AdminControllerTest.

---

### Prompt 34: Service Exception & Edge-Case Unit Tests for AllocationService
You are a senior Java 17 and Spring Boot 3 engineer working on the ta-allocator codebase.

1. CONTEXT & FILE LOCATIONS:
- Target Test File:
  * src/test/java/com/iitm/aero/taallocator/service/AllocationServiceTest.java
- Source File:
  * src/main/java/com/iitm/aero/taallocator/service/AllocationService.java
- Technologies: JUnit 5, Mockito, pure Java 17.

2. TASK / NEW REQUIREMENT:
Add thorough edge-case and boundary tests to AllocationServiceTest.java:
1. unassignTa Edge Cases:
   - testUnassignTa_Slot2_Success(): Verify slot 2 student is cleared from course, marked available=true, course field cleared, and both course and student repositories saved.
   - testUnassignTa_SlotAlreadyEmpty_DoesNothingGracefully(): Course has ta1 = null; unassigning slot 1 does not throw NullPointerException and completes safely.
   - testUnassignTa_CourseNotFound_ThrowsIllegalArgumentException(): Verify assertThrows(IllegalArgumentException.class) when course does not exist.
2. overrideAssignment Edge Cases:
   - testOverrideAssignment_CourseNotFound_ThrowsException(): Unknown course throws IllegalArgumentException.
   - testOverrideAssignment_StudentNotFound_ThrowsException(): Unknown newStudentRollNo throws IllegalArgumentException.
   - testOverrideAssignment_SlotAlreadyEmpty_AssignsNewStudentDirectly(): Slot 1 is null; successfully assigns new student and sets availability to false.
   - testOverrideAssignment_StudentAlreadyAssignedElsewhere_FreesFromOldCourse(): If new student was assigned to Course B Slot 1, overriding them into Course A Slot 1 clears Course B Slot 1.
3. rollbackGroupAllocations Edge Cases:
   - testRollbackGroupAllocations_EmptyGroup_ReturnsZero(): Group has 0 courses, returns 0 and does not error.
4. allocateGroup Edge Cases:
   - testAllocateGroup_EmptyCourseList_ReturnsEarly(): findByGroupId returns empty list; exits safely without throwing exception.
   - testAllocateGroup_EmptyAvailableStudents_SavesUnassignedCourses(): Courses exist but availableStudents is empty; exits safely and saves courses with unassigned TAs.
   - testAllocateGroup_AllNoPreference_AllocatesBasedOnCgpa(): Preference explicitly contains "No preference. You choose for me."; verify students are scored using base + CGPA weighting.

3. HARD CONSTRAINTS:
- Preserve all existing tests in AllocationServiceTest.java without breaking changes.
- Pure Java 17 with Mockito mocks; no database access required.

4. DEFENSIVE INVARIANTS & EDGE CASES:
- Verify that student availability flags and course TA slots stay strictly in sync during overrides and unassignments.

5. OUTPUT FORMAT & VERIFICATION:
- Provide the updated AllocationServiceTest.java.
- Verify that all tests pass with .\mvnw.cmd test -Dtest=AllocationServiceTest.

---

### Prompt 35: Boundary & Ingestion Unit Tests for DataUploadService
You are a senior Java 17 and Spring Boot 3 engineer working on the ta-allocator codebase.

1. CONTEXT & FILE LOCATIONS:
- Target Test File:
  * src/test/java/com/iitm/aero/taallocator/service/DataUploadServiceTest.java
- Source File:
  * src/main/java/com/iitm/aero/taallocator/service/DataUploadService.java
- Technologies: JUnit 5, Mockito, OpenCSV, pure Java 17.

2. TASK / NEW REQUIREMENT:
Add missing defensive and boundary unit tests to DataUploadServiceTest.java:
1. Faculty Upload Ingestion:
   - testParseAndSaveFaculty_EmptyOrHeaderOnlyCSV(): CSV with header only does not save records.
   - testParseAndSaveFaculty_MalformedRowsSkipped(): Rows with < 2 columns or empty names are skipped safely.
2. Preferences Upload Ingestion:
   - testParseAndSavePreferences_EmptyOrHeaderOnlyCSV(): CSV with header only does not save records.
   - testParseAndSavePreferences_AlternativeHeaderNames(): CSV using alternative header variations ("select course", "preference 1", "preference 2") maps correctly to PreferenceResponse fields.
3. Export CSV Edge Cases:
   - testExportAllocationsCsv_EmptyCourses_WritesHeadersOnly(): When no courses exist, CSV output contains only the header row without errors.
   - testExportAllocationsCsv_UnassignedTAs_OutputsHyphen(): Course with null ta1 and ta2 outputs "-" for TA fields in the generated CSV.
4. Comprehensive Professor Format Verification:
   - testParseAndSaveProfessorFormat_AllFiles(): Validates end-to-end parsing consistency across professor-formatted input sheets.

3. HARD CONSTRAINTS:
- Do not modify DataUploadService.java unless a genuine bug or unhandled NullPointerException is discovered.
- Keep tests isolated using Mockito mocks.

4. DEFENSIVE INVARIANTS & EDGE CASES:
- Ensure StringWriter captures the exact CSV structure verified through line-by-line assertions.

5. OUTPUT FORMAT & VERIFICATION:
- Provide the updated DataUploadServiceTest.java.
- Verify that all tests pass with .\mvnw.cmd test -Dtest=DataUploadServiceTest.

---

### Prompt 36: Edge-Case & Algorithmic Unit Tests for HungarianAlgorithm
You are an algorithm specialist and Java 17 engineer working on the ta-allocator codebase.

1. CONTEXT & FILE LOCATIONS:
- Target Test File:
  * src/test/java/com/iitm/aero/taallocator/util/HungarianAlgorithmTest.java
- Source File:
  * src/main/java/com/iitm/aero/taallocator/util/HungarianAlgorithm.java
- Technologies: JUnit 5, pure Java 17.

2. TASK / NEW REQUIREMENT:
Expand HungarianAlgorithmTest.java with boundary and degenerate matrix tests:
1. testEmptyMatrix():
   - double[][] weights = new double[0][0];
   - Assert returns int[0].
2. testZeroStudentsAvailable():
   - double[][] weights = new double[3][0];
   - Assert returns int[3] with all values set to -1.
3. testAllZeroWeights_NoMatchPossible():
   - double[][] weights = { { 0.0, 0.0 }, { 0.0, 0.0 } };
   - Assert returns [-1, -1] because no positive satisfaction edge exists.
4. testMoreStudentsThanSlots():
   - 2 slots, 4 students with varying preference weights.
   - Assert optimal matching selects the top two students with the maximum sum.
5. testSingleSlotAndStudent():
   - double[][] weights = { { 50.0 } };
   - Assert match[0] == 0.

3. HARD CONSTRAINTS:
- Pure Java 17; no external graph or math dependencies.

4. DEFENSIVE INVARIANTS:
- The algorithm must never throw ArrayIndexOutOfBoundsException or infinite loops on empty, degenerate, or non-square matrices.

5. OUTPUT FORMAT & VERIFICATION:
- Provide the updated HungarianAlgorithmTest.java.
- Verify with .\mvnw.cmd test -Dtest=HungarianAlgorithmTest.

---

### Prompt 37: Two-Way State Synchronization for CSV Ingestion in DataUploadService
You are a senior Java 17 and Spring Data JPA developer working on the ta-allocator project.

1. CONTEXT & PROBLEM:
- Ingestion Source: src/main/java/com/iitm/aero/taallocator/service/DataUploadService.java
- Tests: src/test/java/com/iitm/aero/taallocator/service/DataUploadServiceTest.java
- When coordinators upload a courses.csv that already contains assigned TA roll numbers in TA 1 or TA 2, the corresponding Student records in the database must be automatically synchronized to set isAvailable = false and course = courseNo.
- Conversely, when coordinators upload or reload students.csv after courses are already loaded with assignments, those students should not be erroneously reset to isAvailable = true if they are already assigned to a course slot.

2. REQUIREMENTS:
- In parseAndSaveCourses(MultipartFile):
  * After persisting courses, scan each course's assignedTa1 and assignedTa2.
  * Extract roll number (handling "ROLL: Name" format and invalid placeholders) and update Student.setAvailable(false) and Student.setCourse(courseNo).
- In parseAndSaveStudents(MultipartFile):
  * Before saving students, inspect all existing courses in courseRepository.findAll().
  * If a student's roll number is found assigned to an active course slot, set Student.setAvailable(false) and Student.setCourse(assignedCourse).
- Add comprehensive unit tests in DataUploadServiceTest.java verifying two-way synchronization during course and student uploads.

3. DEFENSIVE INVARIANTS:
- Handle null, blank, and invalid placeholders ("-", "N/A", "null") safely without throwing NullPointerExceptions.
- Case-insensitive roll number matching with uppercase normalization.

4. OUTPUT FORMAT & VERIFICATION:
- Provide the updated DataUploadService.java and DataUploadServiceTest.java.
- Verify that all tests pass with .\mvnw.cmd test -Dtest=DataUploadServiceTest.
