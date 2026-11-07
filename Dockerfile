# Multi-stage build for Aerospace TA Allocator
# Stage 1: Build the artifact
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

# Copy Maven wrapper and POM first for layer caching
COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw

# Copy source code and test fixtures
COPY src src
COPY courses_test.csv faculty_test.csv preferences_test.csv students_test.csv ./

# Package standalone executable JAR
RUN ./mvnw clean package -DskipTests

# Stage 2: Minimal JRE runtime
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Ensure non-root or standard working environment
RUN mkdir -p /app/data

# Copy packaged JAR and sample CSV data files
COPY --from=build /workspace/target/ta-allocator-0.0.1-SNAPSHOT.jar app.jar
COPY courses_test.csv faculty_test.csv preferences_test.csv students_test.csv ./

# Expose Spring Boot web port
EXPOSE 8080

# Run without baking any API credentials or secret tokens
ENTRYPOINT ["java", "-jar", "app.jar"]
