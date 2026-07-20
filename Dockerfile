FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --version

COPY src ./src
RUN ./gradlew clean bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre
WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring spring
USER spring

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java","-XX:+UseContainerSupport","-jar","/app/app.jar"]
