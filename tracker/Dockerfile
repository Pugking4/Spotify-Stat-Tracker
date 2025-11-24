FROM maven:3.9.11-eclipse-temurin-25

WORKDIR /app

COPY --from=0 /app/target/*.jar ./app.jar

CMD ["java", "-jar", "app.jar"]
