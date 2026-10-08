# Complete Setup & Operational Guide

This guide walks you through every configuration step required to run the **TA Allocator** system, including configuring Gmail SMTP for automated emails, setting up the Google Forms API, and running a complete test cycle.

---

## Pre-Flight Checklist

Before you start, make sure you have:
- [x] Java 17+ installed (`java -version`).
- [x] A Google Account with 2-Step Verification enabled.
- [x] Access to Google Cloud Console ([https://console.cloud.google.com/](https://console.cloud.google.com/)).
- [x] The `ta-allocator` project directory open in your editor or terminal.

---

## 1. Setting Up Gmail SMTP (Automated Email Dispatch)

The application uses Spring Boot's `JavaMailSender` to send emails from your official or personal Gmail address. For security reasons, Google does not permit computer applications to use your primary account password. Instead, you must generate a dedicated **16-character App Password**.

### Step-by-Step Instructions:
1. Open your Google Account Security Dashboard: [https://myaccount.google.com/security](https://myaccount.google.com/security)
2. Under **"How you sign in to Google"**, verify that **2-Step Verification** is turned **ON**. (App Passwords are not accessible if 2FA is off).
3. In the top search bar inside Google Account, type **"App passwords"** and click on it.
4. Give your new App Password an identifier (e.g., `TA Allocator Server`).
5. Click **Create**.
6. Google will display a 16-character passcode in a yellow box (e.g., `avpb uwbr nmis ibrf`).
7. Open `src/main/resources/application.properties` in this project.
8. Enter your email and the 16-character password (spaces can be omitted or kept):
   ```properties
   spring.mail.host=smtp.gmail.com
   spring.mail.port=587
   spring.mail.username=your-official-email@smail.iitm.ac.in
   spring.mail.password=avpbuwbrnmisibrf
   spring.mail.properties.mail.smtp.auth=true
   spring.mail.properties.mail.smtp.starttls.enable=true
   ```
9. Save the file.

> **Note on Safety:** Never commit your real App Password to a public GitHub repository. Add `application.properties` to your `.gitignore` or use environment variables in production.

---

## 2. Creating the Google Form

The application communicates directly with your Google Form to update dropdown lists of courses and eligible students.

### Step-by-Step Instructions:
1. Navigate to Google Forms: [https://forms.google.com/](https://forms.google.com/) and create a **Blank Form**.
2. Set the Title to: **TA Preferences**.
3. Create the following specific questions:
   - Question 1: Type **Dropdown**, Title: `Select Course`
   - Question 2: Type **Dropdown**, Title: `TA Preference 1`
   - Question 3: Type **Dropdown**, Title: `TA Preference 2`
   - Question 4: Type **Dropdown**, Title: `TA Preference 3`
   - Question 5: Type **Dropdown**, Title: `TA Preference 4`
4. **Leave the options empty!** You do not need to type student roll numbers or courses manually. The Java application will automatically populate them via the API.
5. In the top-right corner, click **Send** $\rightarrow$ switch to the Link icon $\rightarrow$ copy the **Form Link**. (You will paste this into the web app when sending emails).

### How to Find Your Google Form ID:
Open your Google Form in your browser while in edit mode. Examine the address bar URL:
```
https://docs.google.com/forms/d/100pi2kPO4jmZDu9ZGrs8GJwQmTgnOMbTM_qPS9Aaz3s/edit
```
The **Form ID** is the alphanumeric sequence between `/d/` and `/edit`:
```
100pi2kPO4jmZDu9ZGrs8GJwQmTgnOMbTM_qPS9Aaz3s
```
Keep this ID handy. You will paste it into the **Google Form ID** box on the Group Manager page.

---

## 3. Configuring `credentials.json` (Google Cloud Service Account)

To grant the Java application programmatic permission to edit your Google Form:

### Step-by-Step Instructions:
1. Go to the **Google Cloud Console**: [https://console.cloud.google.com/](https://console.cloud.google.com/)
2. Create a new Google Cloud Project (e.g., `TA Allocator Project`).
3. In the search bar at the top, search for **Google Forms API** and click **Enable**.
4. In the left navigation menu, go to **APIs & Services** $\rightarrow$ **Credentials**.
5. Click **+ CREATE CREDENTIALS** at the top and select **Service account**.
6. Set the Service account name to `form-updater` and click **Create and Continue**, then click **Done**.
7. Locate your newly created service account in the table and **copy its email address**. It will look like:
   ```
   form-updater@ta-allocator-xxxxxx.iam.gserviceaccount.com
   ```
8. Click on the Service Account name $\rightarrow$ Navigate to the **Keys** tab $\rightarrow$ Click **Add Key** $\rightarrow$ **Create new key**.
9. Select **JSON** as the key type and click **Create**. A `.json` key file will download to your computer.
10. Rename that downloaded file to:
    ```
    credentials.json
    ```
11. Move `credentials.json` into the project directory at:
    ```
    src/main/resources/credentials.json
    ```

### CRITICAL FINAL STEP (Granting Edit Access):
Open your Google Form in your browser:
1. Click the three vertical dots (`...`) in the top right $\rightarrow$ select **Add collaborators** (or click Share).
2. Paste the **Service Account email** (`form-updater@ta-allocator-xxxxxx.iam.gserviceaccount.com`).
3. Ensure its permission role is set to **Editor**.
4. Uncheck "Notify people" (since it's a robot account) and click **Send / Share**.
> If you omit this step, the Java application will receive an HTTP `403 Forbidden` error when trying to update the form.

---

## 4. End-to-End Testing with Sample Data

The project includes four pre-built test CSV files located in the root directory:
- `courses_test.csv`
- `students_test.csv`
- `faculty_test.csv`
- `preferences_test.csv`

### Testing Procedure:
1. **Launch the server:**
   ```bash
   .\mvnw.cmd spring-boot:run
   ```
2. **Open the browser:** Navigate to `http://localhost:8080/`.
3. **Upload the test files:**
   - Click "Choose File" for **Courses** $\rightarrow$ select `courses_test.csv` $\rightarrow$ click Upload.
   - Click "Choose File" for **Students** $\rightarrow$ select `students_test.csv` $\rightarrow$ click Upload.
   - Click "Choose File" for **Faculty** $\rightarrow$ select `faculty_test.csv` $\rightarrow$ click Upload.
   - Click "Choose File" for **Form Preferences** $\rightarrow$ select `preferences_test.csv` $\rightarrow$ click Upload.
4. **Inspect the Data Overview card:**
   - Confirm that the total course count, student count, and available student count match the test data.
   - Under **Manage Groups**, select a group (e.g., **Manage Group 0** or **Manage Group 1**).
5. **Execute the 4-Step Pipeline:**
   - **Step 1:** Enter your `Google Form ID` $\rightarrow$ click **Update Google Form**. Check your online form; its dropdowns will now show the course codes and student options!
   - **Step 2:** Enter the Term (e.g., `Jul-Nov 2026`), a Deadline, and the Form Link $\rightarrow$ click **Send Emails**.
   - **Step 3:** Click **Run Allocation Algorithm**. Observe that the courses table updates with assigned TAs (`ta1` and `ta2`).
   - **Step 4:** Enter the Term $\rightarrow$ click **Send Confirmations**. Confirmation emails will be sent out for all courses with allotted TAs.

---

## 5. Inspecting the H2 In-Memory Database

While the application is running, you can inspect the raw database tables at any time:
1. Open [http://localhost:8080/h2-console](http://localhost:8080/h2-console) in your browser.
2. Ensure the connection settings are:
   - **JDBC URL:** `jdbc:h2:mem:tadb`
   - **User Name:** `sa`
   - **Password:** *(leave blank)*
3. Click **Connect**.
4. You can query tables directly using SQL:
   ```sql
   SELECT * FROM COURSES;
   SELECT * FROM STUDENTS WHERE IS_AVAILABLE = FALSE;
   SELECT * FROM FACULTY;
   SELECT * FROM PREFERENCES;
   ```

---

## 6. Troubleshooting Common Issues

| Issue / Symptom | Root Cause | Solution |
| :--- | :--- | :--- |
| `AuthenticationFailedException: 535-5.7.8` | Regular Gmail password used instead of an App Password. | Generate a 16-character App Password from Google Security settings and update `spring.mail.password`. |
| `GoogleJsonResponseException: 403 Forbidden` | The Google Form has not been shared with the service account. | Open the form in Google Forms $\rightarrow$ Add Collaborators $\rightarrow$ add `client_email` from `credentials.json` as Editor. |
| `"No matching questions found in Google Form"` | Google Form questions have titles different from expected. | Ensure your questions are titled exactly `Select Course`, `TA Preference 1`, `TA Preference 2`, `TA Preference 3`, `TA Preference 4`. |
| Roll number extraction errors in logs | Student dropdown format doesn't match `RollNo: Name` pattern. | Keep standard formatting when selecting preferences: roll number followed by a colon or 8-character roll format. |
