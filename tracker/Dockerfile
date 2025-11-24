# First stage: build the project with Maven
FROM maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /app

COPY . .
RUN mvn clean package


# Second stage: copy the built jar and run the app
FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build /app/target/spotify-tracker-1.0.0.jar ./app.jar

CMD ["java", "-jar", "app.jar"]
