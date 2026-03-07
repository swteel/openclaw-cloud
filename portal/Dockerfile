FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY target/portal-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
