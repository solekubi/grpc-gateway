# Gradle #
FROM gradle:jdk11 AS build
WORKDIR /app
COPY --chown=gradle:gradle . .
RUN gradle bootJar -x test

FROM openjdk:11-jre-slim
WORKDIR /app
COPY --from=build /app/build/libs/gateway.jar .
EXPOSE 80
ENTRYPOINT ["java","-DPORT=80","-Djava.security.egd=file:/dev/./urandom","-jar","/app/gateway.jar"]