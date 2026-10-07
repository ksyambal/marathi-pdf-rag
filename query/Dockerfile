# AgentCore Runtime runs Graviton only. Build with:
#   docker buildx build --platform linux/arm64 -f query/Dockerfile -t marathi-pdf-rag-query .
FROM --platform=linux/arm64 eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY query/build/libs/query-service.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
