FROM openjdk:21-slim
COPY target/task-service-*.jar /app/task-service.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/task-service.jar"]
