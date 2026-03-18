FROM maven:3.9.11-eclipse-temurin-25 AS builder
WORKDIR /app

COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

COPY src/ src/
RUN mvn -q -DskipTests clean package

FROM eclipse-temurin:25-jdk AS runtime
WORKDIR /app

COPY --from=builder /app/target/demo-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8090

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

