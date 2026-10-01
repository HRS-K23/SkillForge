# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package && cp target/skillforge-*.jar app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --create-home --uid 1001 app && mkdir /content && chown app /content
USER app
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
ENV CONTENT_ROOT=/content
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]