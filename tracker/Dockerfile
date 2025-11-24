# Build stage using Eclipse Temurin 25 JDK with Maven (adjust if using Gradle or other)
FROM eclipse-temurin:25-jdk AS build

WORKDIR /app

# Copy your project files
COPY pom.xml .
COPY src ./src

# Build the project (skip tests for faster build, remove if you want tests)
RUN ./mvnw clean package -DskipTests

# Runtime stage with smaller image just to run the jar
FROM eclipse-temurin:25-jdk

WORKDIR /app

# Copy the built jar from the build stage
COPY --from=build /app/target/Spotify-Tracker-prod.jar ./Spotify-Tracker-prod.jar

EXPOSE 8888

ENTRYPOINT ["java", "-jar", "Spotify-Tracker-prod.jar"]
