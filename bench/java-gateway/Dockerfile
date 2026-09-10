# 对比实验用 Spring Cloud Gateway
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package \
 && cp target/java-gateway-*.jar /build/app.jar

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /build/app.jar /app/app.jar
ENV SERVER_PORT=8088
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
