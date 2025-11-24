FROM eclipse-temurin:25-jdk

WORKDIR /app

COPY out/artifacts/Spotify_Tracker_jar/Spotify-Tracker-prod.jar ./Spotify-Tracker-prod.jar

EXPOSE 8888

ENTRYPOINT ["java", "-jar", "Spotify-Tracker-prod.jar"]
