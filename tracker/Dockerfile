# Build stage: use Maven with Java 25 to compile your code
FROM maven:3.9.1-eclipse-temurin-25 AS build

WORKDIR /app

# Copy pom.xml from tracker directory and source folder
COPY tracker/pom.xml ./pom.xml
COPY tracker/src ./src

RUN mvn clean package -DskipTests

# Runtime stage: smaller image running just the jar
FROM eclipse-temurin:25-jdk

WORKDIR /app

# Copy the jar built by Maven from the build stage
COPY --from=build /app/target/*.jar ./Spotify-Tracker-prod.jar

EXPOSE 8888

ENTRYPOINT ["java", "-jar", "Spotify-Tracker-prod.jar"]
