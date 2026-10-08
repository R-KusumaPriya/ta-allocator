# TA Allocator

An automated web application built with **Java 17 and Spring Boot** designed to streamline and automate the Teaching Assistant (TA) allocation process for the Department of Aerospace Engineering, IIT Madras.

The system replaces manual spreadsheets and email chains with a unified web interface: ingesting CSV data, synchronizing with the Google Forms API, collecting professor preferences, executing constraint-based matching algorithms, and dispatching confirmation emails via SMTP.

---

## Table of Contents
1. [Key Features](#key-features)
2. [Prerequisites](#prerequisites)
3. [Quick Start](#quick-start)
4. [System Architecture & Flow](#system-architecture--flow)
5. [End-to-End Workflow Guide](#end-to-end-workflow-guide)
6. [CSV Data Formats](#csv-data-formats)
7. [Configuration Details](#configuration-details)
8. [Troubleshooting & Common Issues](#troubleshooting--common-issues)

---

## Key Features

- **Automated Google Forms Synchronization:** Integrates with the Google Forms API (v1) using a Google Cloud Service Account (`credentials.json`) to automatically populate course lists and active, unassigned student roll numbers into dropdown choices.
- **Smart Constraint-Based TA Matching:** Matches professor preferences in priority order, enforces a maximum cap of 2 TAs per course, dynamically locks assigned students (`isAvailable = false`) to prevent double-allocations, and honors `"I do not want TAs"` requests.
- **Automated Email Dispatch:** Integrates with Gmail SMTP via Spring's `JavaMailSender` to send personalized preference collection requests and final allotment confirmations to faculty members.
- **Robust CSV Ingestion:** Uses OpenCSV with dynamic, case-insensitive header mapping that gracefully accepts varying column header names (e.g., `id`, `courseno`, `course no`).
- **Group/Batch Processing:** Supports dividing allocations into Course Groups (e.g., Core courses in Group 0, Lab courses in Group 1) to enable staggered allocation rounds.
- **Embedded Database & Admin Console:** Runs on an in-memory H2 database with Spring Data JPA and an accessible web console for runtime inspection.

---

## Prerequisites

- **Java Development Kit (JDK):** Version 17 or higher (`java -version`)
- **Maven:** Bundled via `./mvnw` / `mvnw.cmd` wrapper (no separate Maven install required)
- **Google Cloud Service Account:** With Google Forms API enabled and `credentials.json` placed in `src/main/resources/`
- **Gmail Account with 2FA:** Configured with a 16-character App Password for SMTP email sending

---

## Quick Start

1. **Clone or open the project folder in terminal:**
   ```bash
   cd ta-allocator
   ```

2. **Configure Credentials (Optional for local testing without emails/forms):**
   - Ensure `src/main/resources/application.properties` has your SMTP details if sending real emails.
   - Ensure `src/main/resources/credentials.json` is present if syncing live Google Forms.

3. **Start the application:**
   - **On Windows (PowerShell / Command Prompt):**
     ```powershell
     .\mvnw.cmd spring-boot:run
     ```
   - **On Linux / macOS:**
     ```bash
     ./mvnw spring-boot:run
     ```

4. **Access the Web Dashboard:**
   - Open your browser at: [http://localhost:8080/](http://localhost:8080/)
   - H2 Database Web Console: [http://localhost:8080/h2-console](http://localhost:8080/h2-console) (JDBC URL: `jdbc:h2:mem:tadb`, Username: `sa`, Password: empty)

---

## System Architecture & Flow

```
                      [ Admin Web Browser ]
                                |
                   (HTTP GET / POST via Thymeleaf)
                                |
                     [ AdminController.java ]
                                |
       +------------------------+------------------------+
       |                        |                        |
[ DataUploadService ]   [ AllocationService ]    [ EmailService ]
       |                        |                        |
  (OpenCSV)            (Matching Algorithm)       (JavaMailSender /
       |                        |                 Gmail SMTP)
       +-----------+------------+                        |
                   |                              [ Faculty Inboxes ]
          [ Spring Data JPA ]
                   |
           [ H2 In-Memory DB ]
                   |
         [ GoogleFormsService ]
                   |
         (Google Forms API v1)
                   |
           [ Live Google Form ]
```

---

## End-to-End Workflow Guide

### Step 0: Upload Master CSVs (Dashboard)
Navigate to [http://localhost:8080/](http://localhost:8080/) and upload the four primary data files:
1. **Courses CSV:** Master list of department courses, priorities, group IDs, and faculty names.
2. **Students CSV:** Master list of eligible students with CGPA, academic program, and current availability status.
3. **Faculty CSV:** Faculty directory with names and official email addresses.
4. **Form Preferences CSV:** Faculty preference responses (downloaded from Google Forms responses).

### Step 1: Update Google Form
1. Click on a **Group ID** (e.g., *Manage Group 0*).
2. Enter the **Google Form ID** (the unique string in the editing URL of your Google Form).
3. Click **Update Google Form**.
   - The application connects to Google Forms API.
   - Injects the department course list into the `"Select Course"` question.
   - Injects all currently unassigned students into `"TA Preference 1"`, `"TA Preference 2"`, etc.

### Step 2: Send Preference Collection Emails
1. Fill in the **Term** (e.g., `Jul-Nov 2026`), **Deadline** (e.g., `5:00 PM on Friday, Aug 8`), and the public **Google Form Link**.
2. Click **Send Emails**.
   - Dispatches personalized emails to every professor teaching a course in this Group ID, asking them to submit 4 preferences.

### Step 3: Run the TA Allocation Algorithm
1. Ensure the latest **Form Preferences CSV** is uploaded if new responses came in.
2. Click **Run Allocation Algorithm**.
   - Processes each course in the group.
   - Honors professor choices in priority order (Choice 1 $\rightarrow$ 4).
   - Skips courses where the professor selected `"I do not want TAs."`
   - Allocates up to 2 TAs per course.
   - Marks each allocated student as unavailable (`isAvailable = false`) so they cannot be selected by subsequent courses.

### Step 4: Send Final Confirmation Emails
1. Enter the academic **Term**.
2. Click **Send Confirmations**.
   - Sends an allotment confirmation email to each professor with the names and roll numbers of their allotted TAs.
   - Directs TAs to set up a kickoff meeting within 2 working days.

---

## CSV Data Formats

The application includes robust header detection. The table below lists the recognized column headers:

| File Type | Required Information | Accepted Column Header Variations |
| :--- | :--- | :--- |
| **Courses** | Course ID, Course Name, Instructor Name, Instructor Email, Priority, Group ID | `id`, `courseno`, `course no`, `name`, `coursename`, `faculty_name`, `instructor`, `faculty_email`, `email`, `priority`, `group_id` |
| **Students** | Roll Number, Student Name, CGPA, Degree Program, Availability, Current Course | `id`, `roll`, `name`, `gpa`, `cgpa`, `program`, `available`, `course` |
| **Faculty** | Faculty Name, Email Address, Faculty Code | Column 0: Name, Column 1: Email, Column 2: Code |
| **Preferences** | Course ID, Faculty Name, Choice 1, Choice 2, Choice 3, Choice 4 | `course_id`, `select course`, `faculty_name`, `choice1`, `preference 1`, `choice2`, `choice3`, `choice4` |

*Sample test files with representative dummy data are included in the project root: `courses_test.csv`, `students_test.csv`, `faculty_test.csv`, and `preferences_test.csv`.*

---

## Configuration Details

All runtime configurations reside in `src/main/resources/application.properties`:

```properties
# Application & In-Memory Database
spring.application.name=ta-allocator
spring.datasource.url=jdbc:h2:mem:tadb
spring.datasource.username=sa
spring.datasource.password=
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console

# Gmail SMTP Configuration
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your-account@smail.iitm.ac.in
spring.mail.password=your-16-char-app-password
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true

# File Upload Thresholds
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

For detailed instructions on obtaining a Gmail App Password and Google Cloud Service Account credentials, see [`setup_guide.md`](file:///c:/Users/radhi/.gemini/antigravity/scratch/ta-allocator/setup_guide.md).

---

## Troubleshooting & Common Issues

- **Google Form update returns 403 Forbidden:**
  Ensure the Service Account email (from `credentials.json`, e.g., `form-updater@ta-allocator.iam.gserviceaccount.com`) has been explicitly added as an **Editor** in your Google Form's collaborator/share settings.
- **Email sending fails with AuthenticationFailedException:**
  Standard Gmail passwords will not work. You must enable 2-Step Verification and generate a dedicated 16-character **App Password** from your Google Account settings. Remove any spaces when pasting it into `application.properties`.
- **Database data resets upon application restart:**
  The system uses H2 in-memory storage (`jdbc:h2:mem:tadb`), which intentionally initializes fresh on each launch. To preserve data across restarts, re-upload the master CSVs or configure a persistent file-based URL in `application.properties`.
