# Aerospace TA Allocator

An automated web application built with **Java 17, Spring Boot 3, and Thymeleaf** designed to streamline and automate the Teaching Assistant (TA) allocation process for the Department of Aerospace Engineering, IIT Madras.

The platform eliminates manual spreadsheets, error-prone email chains, and double-booking by providing an intelligent, constraint-aware allocation engine powered by the **Hungarian (Kuhn-Munkres) Maximum Weight Bipartite Matching Algorithm**. It features automated Google Forms synchronization, interactive management controls (override, unassign, rollback, preview), and automated SMTP email dispatch.

---

## Table of Contents
1. [Key Features & Capabilities](#key-features--capabilities)
2. [Complete Setup Guide for a New Laptop](#complete-setup-guide-for-a-new-laptop)
   - [Phase 1: Check & Install Prerequisites](#phase-1-check--install-prerequisites)
   - [Phase 2: Clone the Repository](#phase-2-clone-the-repository)
   - [Phase 3: Run the Application](#phase-3-run-the-application)
   - [Phase 4: Open the Web Dashboard & First Test Run](#phase-4-open-the-web-dashboard--first-test-run)
   - [Phase 5: Stopping the Application](#phase-5-stopping-the-application)
3. [Step-by-Step External Integrations Setup](#step-by-step-external-integrations-setup)
   - [Email Integration (SMTP via Gmail or IITM Smail)](#email-integration-smtp-via-gmail-or-iitm-smail)
   - [Google Forms API Integration (credentials.json)](#google-forms-api-integration-credentialsjson)
   - [H2 Database Web Console](#h2-database-web-console)
4. [System Architecture & Workflow](#system-architecture--workflow)
5. [CSV Data Formats](#csv-data-formats)
6. [Troubleshooting & FAQs](#troubleshooting--faqs)

---

## Key Features & Capabilities

- **Globally Optimal TA Matching (Hungarian Algorithm):**
  Uses the Kuhn-Munkres algorithm ($O(N^2 \cdot M)$) to maximize overall preference satisfaction across all courses and professors simultaneously, preventing suboptimal greedy allocations.
- **Allocation Preview (Dry Run Mode):**
  Inspect proposed TA assignments, preference ranks (Choice 1–4), and algorithmic scores in an in-memory preview table before committing any changes to the database.
- **Interactive TA Management:**
  - **Single-Click Unassignment (`✕`):** Frees an individual TA slot and instantly marks the student available again.
  - **Manual Assignment Override:** Reassign any student directly to Slot 1 or Slot 2 via an inline dropdown form.
  - **Per-Group Rollback / Undo:** Undo an entire group's allocation round in one click, clearing course slots and releasing all assigned students.
- **Automated Google Forms Synchronization & Ingestion:**
  Automatically updates live Google Form dropdown choices with department courses and unassigned student roll numbers via Google Forms API (v1), and seamlessly ingests response CSV exports.
- **Customizable Automated Email Dispatch:**
  Sends preference collection requests and allotment confirmations via Gmail/Smail SMTP, supporting custom coordinator notes and special instructions injected dynamically into message bodies.
- **Departmental Constraints & Safety Checks:**
  Enforces a strict 2 TAs/course cap, respects `"I do not want TAs"` opt-outs, tracks cross-group availability so students aren't double-booked, and honors faculty-student matching constraints (e.g. `AE22B058` restricted from Prof. Nidish).
- **Persistent Embedded Database & CSV Reporting:**
  File-based H2 database (`./data/tadb.mv.db`) ensures all data survives application restarts. Generate one-click CSV reports with resolved student roll numbers and names.
- **One-Click Master Database Reset:**
  Dedicated administrative reset button on the dashboard to wipe database tables and start a fresh allocation cycle without manually deleting files.

---

## Complete Setup Guide for a New Laptop

Follow these steps to set up and run this application on any Windows or macOS laptop from scratch.

### Phase 1: Check & Install Prerequisites

The project requires only two tools on the host computer: **Git** and **Java 17**. You do **not** need to install Maven or an external database server (MySQL/PostgreSQL), as both the Maven Wrapper and embedded H2 database are included.

#### 1. Check Git
Open **Command Prompt** (on Windows: press `Win + R`, type `cmd`, press Enter) or **Terminal** (on macOS):
```bash
git --version
```
- If a version number appears (e.g., `git version 2.x.x`), Git is ready.
- **If missing or not recognized:** Download and install from [git-scm.com/downloads](https://git-scm.com/downloads) (keep all default installer options).

#### 2. Check Java (Requires JDK 17 or higher)
In the same terminal window, run:
```bash
java -version
```
- Ensure the version starts with **17** (or 21).
- **If missing or an older version (e.g., 8 or 11):**
  - Download **Eclipse Temurin JDK 17** from [adoptium.net](https://adoptium.net/temurin/releases/?version=17).
  - Select your operating system (Windows x64 `.msi` or macOS `.pkg`).
  - During Windows installation, ensure **"Set JAVA_HOME variable"** and **"Add to PATH"** are checked.
  - Restart your terminal and re-verify with `java -version`.

---

### Phase 2: Clone the Repository

1. Open **Command Prompt** or **PowerShell** (Windows) / **Terminal** (macOS).
2. Navigate to your desired workspace (e.g., your Desktop):
   ```bash
   cd Desktop
   ```
3. Clone the repository from GitHub:
   ```bash
   git clone https://github.com/R-KusumaPriya/ta-allocator.git
   ```
4. Enter the project folder:
   ```bash
   cd ta-allocator
   ```

---

### Phase 3: Run the Application

The project uses the bundled Maven Wrapper, which automatically handles all dependencies and compilation.

- **On Windows (PowerShell or Command Prompt):**
  ```powershell
  .\mvnw.cmd spring-boot:run
  ```
  *(In standard `cmd.exe`, you can simply run `mvnw spring-boot:run`)*

- **On macOS / Linux:**
  ```bash
  chmod +x mvnw
  ./mvnw spring-boot:run
  ```

#### What happens next:
1. On the very first run, Maven will download all required libraries into your local cache (takes 1–2 minutes).
2. The Spring Boot banner will appear in your console:
   ```text
     .   ____          _            __ _ _
    /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
   ( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
    \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
     '  |____| .__|_| |_|_| |_\__, | / / / /
    =========|_|==============|___/=/_/_/_/
    :: Spring Boot ::                (v4.1.1)

   ...
   Started TaAllocatorApplication in 4.523 seconds (process running for 6.12)
   ```
3. **Leave this terminal window open.** As long as this window is running, the local web server is live.

---

### Phase 4: Open the Web Dashboard & First Test Run

1. Open your web browser (Chrome, Edge, Firefox, or Safari).
2. Navigate to:
   ```text
   http://localhost:8080
   ```
3. **Populate initial data using the bundled test CSV files** located in the root of the project:
   - On the dashboard, click **"Upload Courses CSV"** $\rightarrow$ choose `courses_test.csv` $\rightarrow$ Submit.
   - Click **"Upload Students CSV"** $\rightarrow$ choose `students_test.csv` $\rightarrow$ Submit.
   - Click **"Upload Faculty CSV"** $\rightarrow$ choose `faculty_test.csv` $\rightarrow$ Submit.
   - Click **"Upload Preferences CSV"** $\rightarrow$ choose `preferences_test.csv` $\rightarrow$ Submit.
4. **Test the Allocation Engine:**
   - Click **"Manage Group 0.0"** under Course Groups.
   - Click **"Allocation Preview (Dry Run)"**: Inspect the proposed allotments in the preview card without modifying the database.
   - Click **"Run Allocation Algorithm"**: The Hungarian algorithm matches the TAs optimally, assigns them to courses, and marks students unavailable.
   - Try the **Unassign (`✕`)** button to free a slot, or the **Manual Override** form to swap students.
   - Click **"Export Allocations (CSV)"** to download the clean allotment report.

---

### Phase 5: Stopping the Application

To shut down the server when finished:
- Switch to the terminal window where the app is running.
- Press **`Ctrl + C`**.
- If prompted `Terminate batch job (Y/N)?`, type **`Y`** and press Enter.

---

## Step-by-Step External Integrations Setup

While the core allocation engine and in-app forms run completely offline without external credentials, you can configure real email dispatch and Google Forms synchronization following the steps below.

---

### Email Integration (SMTP via Gmail or IITM Smail)

To dispatch preference collection emails and allotment confirmations to faculty inboxes, Spring Boot connects to Gmail's SMTP relay.

> [!IMPORTANT]
> Because Google accounts have 2-Factor Authentication enabled, you cannot use your standard login password. You must generate a free 16-character **App Password**.

#### 1. Generate an App Password:
1. Log into your Google Account at [myaccount.google.com](https://myaccount.google.com/) (use your personal Gmail or your `@smail.iitm.ac.in` account).
2. Select **Security** from the left navigation panel.
3. Under *"How you sign in to Google"*, ensure **2-Step Verification** is turned **ON**.
4. Click on **2-Step Verification**, scroll to the bottom, and click **App passwords** (or search `"App passwords"` in the top search bar).
5. In the **App name** field, enter: `TA Allocator`
6. Click **Create**.
7. Google will display a 16-character code (e.g., `abcd efgh ijkl mnop`). Copy this code without spaces.

#### 2. Configure `application.properties`:
Open `src/main/resources/application.properties` in your text editor and update the email properties:
```properties
# Gmail / IITM Smail SMTP Configuration
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your_email@smail.iitm.ac.in
spring.mail.password=abcdefghijklmnop
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
```
Save the file and restart the application.

---

### Google Forms API Integration (`credentials.json`)

To synchronize active courses and available student roll numbers directly into a live Google Form dropdown:

#### 1. Enable Google Forms API in Google Cloud:
1. Visit the [Google Cloud Console](https://console.cloud.google.com/).
2. Create a new project (e.g., `TA-Allocator`).
3. In the top search bar, search for **Google Forms API** and click **Enable**.

#### 2. Create a Service Account:
1. Go to **APIs & Services** $\rightarrow$ **Credentials**.
2. Click **+ Create Credentials** $\rightarrow$ **Service account**.
3. Name it `ta-allocator-bot` and click **Done**.
4. Note the generated service account email (e.g., `ta-allocator-bot@ta-allocator-xxxx.iam.gserviceaccount.com`).

#### 3. Download the JSON Key:
1. Click on the newly created Service Account $\rightarrow$ navigate to the **Keys** tab.
2. Click **Add Key** $\rightarrow$ **Create new key** $\rightarrow$ select **JSON** $\rightarrow$ click **Create**.
3. A JSON file will download to your computer.
4. Rename this file to:
   ```text
   credentials.json
   ```
5. Place it inside the project directory at:
   ```text
   src/main/resources/credentials.json
   ```

#### 4. Grant Editor Access to the Google Form:
*(Crucial: skipping this causes a 403 Forbidden error).*
1. Open your target Google Form in your web browser.
2. Ensure the form contains questions titled:
   - **Select Course** (Dropdown)
   - **TA Preference 1**, **TA Preference 2**, **TA Preference 3**, **TA Preference 4** (Dropdowns)
3. Click the three dots menu (`⋮`) in the top-right $\rightarrow$ **Add collaborators**.
4. Paste the service account email (`ta-allocator-bot@...iam.gserviceaccount.com`).
5. Ensure the role is set to **Editor**, uncheck "Notify", and click **Share**.

#### 5. Sync from the Group Manager:
1. Copy the **Form ID** from your Google Form URL:
   `https://docs.google.com/forms/d/`**`1FAIpQLSc_ExampleFormId123456789`**`/edit`
2. In the TA Allocator UI (`/group?id=0.0`), paste this ID into **Step 1: Sync Google Form Options** and click **Update Google Form Options**.

---

### H2 Database Web Console

To inspect, query, or verify database tables directly via SQL:
1. Go to: [http://localhost:8080/h2-console](http://localhost:8080/h2-console)
2. Enter the connection settings:
   - **JDBC URL:** `jdbc:h2:file:./data/tadb`
   - **User Name:** `sa`
   - **Password:** *(leave empty)*
3. Click **Connect** to query tables (`COURSE`, `STUDENT`, `FACULTY`, `PREFERENCE_RESPONSE`).

---

## System Architecture & Workflow

```
                                [ Admin Web Browser ]
                                          |
                           (HTTP GET / POST via Thymeleaf)
                                          |
                               [ AdminController.java ]
                                          |
       +--------------------+-------------+--------------------+
       |                    |                                  |
[ DataUploadService ] [ AllocationService ]            [ EmailService ]
       |                    |                                  |
   (OpenCSV)      (Hungarian Algorithm)                 (JavaMailSender /
       |                    |                              Gmail SMTP)
       +-----------+--------+                                  |
                   |                                           v
          [ Spring Data JPA ]                       [ Faculty Email Inboxes ]
                   |
        [ Embedded H2 Database ]
          (./data/tadb.mv.db)
                   |
         [ GoogleFormsService ]
                   |
         (Google Forms API v1)
                   |
                   v
          [ Live Google Form ]
```

---

## CSV Data Formats

The application provides dynamic, case-insensitive header detection:

| File Type | Required Fields | Accepted Header Variations |
| :--- | :--- | :--- |
| **Courses** | Course No, Course Name, Instructor Name, Priority, Group ID | `id`, `courseno`, `course no`, `name`, `coursename`, `faculty_name`, `instructor`, `faculty_email`, `priority`, `group_id` |
| **Students** | Roll Number, Student Name, CGPA, Program, Availability | `id`, `roll`, `name`, `gpa`, `cgpa`, `program`, `available`, `course` |
| **Faculty** | Faculty Name, Email Address, Faculty Code | Col 0: `Name`, Col 1: `Email`, Col 2: `Code` |
| **Preferences** | Course ID, Faculty Name, Choices 1–4 | `course_id`, `select course`, `faculty_name`, `choice1`, `preference 1`, `choice2`, `choice3`, `choice4` |

Sample data files are provided in the repository root: `courses_test.csv`, `students_test.csv`, `faculty_test.csv`, and `preferences_test.csv`.

---

## Troubleshooting & FAQs

### 1. "Port 8080 is already in use"
Another local service is using port 8080.
- **Fix:** Open `src/main/resources/application.properties`, add `server.port=8081` at the top, and save. Access the site at `http://localhost:8081`.

### 2. "Execution of scripts is disabled on this system" (Windows PowerShell)
PowerShell's default security policy restricts script execution.
- **Fix:** Run this command in PowerShell:
  ```powershell
  Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
  ```
  Or run using standard Windows Command Prompt (`cmd.exe`) where this restriction does not apply.

### 3. "Permission denied: ./mvnw" (macOS / Linux)
The Maven Wrapper script needs executable permissions.
- **Fix:** Run `chmod +x mvnw` in your terminal.

### 4. Google Forms Returns "403 Forbidden"
The Service Account bot does not have permissions to modify your form.
- **Fix:** Open your Google Form $\rightarrow$ Click `⋮` (top-right) $\rightarrow$ **Add collaborators** $\rightarrow$ Add your service account email as an **Editor**.

### 5. Email Sending Fails with "AuthenticationFailedException"
Standard Gmail passwords are not accepted.
- **Fix:** Generate a 16-character **App Password** from your Google Account Security settings and paste it into `spring.mail.password` in `application.properties` without spaces.

### 6. Resetting the Application to a Clean Slate
- Click the **"Reset All Data"** button on the dashboard to clear all database entities.
- Alternatively, stop the server (`Ctrl + C`) and delete the local `./data` folder on disk.
