FROM eclipse-temurin:25

RUN apt-get update && apt-get install -y wget && \
    wget https://downloads.apache.org/maven/maven-3/3.9.1/binaries/apache-maven-3.9.1-bin.tar.gz && \
    tar -xvzf apache-maven-3.9.1-bin.tar.gz -C /opt && \
    ln -s /opt/apache-maven-3.9.1 /opt/maven && \
    ln -s /opt/maven/bin/mvn /usr/bin/mvn && \
    rm apache-maven-3.9.1-bin.tar.gz && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

ENV MAVEN_HOME=/opt/maven
ENV PATH=$MAVEN_HOME/bin:$PATH

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM openjdk:25-jdk

WORKDIR /app

COPY --from=0 /app/target/*.jar ./app.jar

CMD ["java", "-jar", "app.jar"]
