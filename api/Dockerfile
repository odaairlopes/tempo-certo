FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/api-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
ENV JAVA_TOOL_OPTIONS=-Duser.timezone=America/Sao_Paulo
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
