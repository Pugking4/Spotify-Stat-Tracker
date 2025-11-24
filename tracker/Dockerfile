# First stage: build the project with Maven
FROM maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /app

COPY . .

CMD java -classpath src/java/main Main
CMD java -classpath src/java main.Main

RUN mvn clean package


# Second stage: copy the built jar and run the app
FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build /app/target/spotify-tracker-1.0.0.jar ./app.jar
RUN mkdir -p ./resources
COPY --from=build /app/target/resources/keystore.jks ./resources/keystore.jks


CMD ["java", "-jar", "app.jar"]
