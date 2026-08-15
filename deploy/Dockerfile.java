# 构建上下文：仓库根目录
# docker compose -f deploy/docker-compose.yml build user-service
ARG SERVICE=user-service

FROM maven:3.9.9-eclipse-temurin-17 AS build
ARG SERVICE
WORKDIR /build
COPY services/ /build/
RUN mvn -pl "${SERVICE}" -am package -DskipTests -q \
    && cp "/build/${SERVICE}/target/${SERVICE}-0.0.1-SNAPSHOT.jar" /build/app.jar

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /build/app.jar /app/app.jar
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
