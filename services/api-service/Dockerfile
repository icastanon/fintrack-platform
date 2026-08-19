FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
COPY event-contracts/pom.xml event-contracts/pom.xml
COPY services/api-service/pom.xml services/api-service/pom.xml
COPY services/worker-service/pom.xml services/worker-service/pom.xml

RUN mvn -B -pl services/api-service -am dependency:go-offline

COPY event-contracts/src event-contracts/src
COPY services/api-service/src services/api-service/src

RUN mvn -B -pl services/api-service -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S fintrack && adduser -S fintrack -G fintrack

WORKDIR /app

COPY --from=build --chown=fintrack:fintrack \
    /workspace/services/api-service/target/api-service-*.jar app.jar

USER fintrack

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]