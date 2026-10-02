# TA Allocator

A Java Spring Boot application to automate the Teaching Assistant (TA) allocation process.

## Prerequisites
- Java 17 or higher
- Maven (or use the included `mvnw` wrapper)

## How to Run

1. Open a terminal in the `ta-allocator` directory.
2. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```
   (On Windows: `.\mvnw.cmd spring-boot:run`)
3. Open your web browser and navigate to `http://localhost:8080/`.

## Configuration
To enable actual email sending, edit the `src/main/resources/application.properties` file and add your SMTP credentials (e.g., Gmail and an App Password).
```properties
spring.mail.username=your-email@gmail.com
spring.mail.password=your-app-password
```

## Workflow

1. **Upload Data:** Use the dashboard to upload the Courses, Students, Faculty, and Form Preferences CSV files.
2. **Manage Group:** Click on a Group ID to enter the group manager.
3. **Step 1:** Update the Google Form (Note: This is mocked and logs to the console).
4. **Step 2:** Send preference collection emails to faculty.
5. **Step 3:** Run the TA allocation algorithm based on the uploaded Form Preferences. TAs that are assigned will be marked as unavailable.
6. **Step 4:** Send confirmation emails to faculty and the newly assigned TAs.
7. Repeat for the next Group ID!
