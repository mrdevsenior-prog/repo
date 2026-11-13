FROM node:24.15.0-bookworm-slim AS frontend
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:25-jdk AS backend
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle/ gradle/
COPY config/ config/
COPY src/main/ src/main/
COPY --from=frontend /workspace/frontend/dist/audit-query/browser frontend/dist/audit-query/browser/
RUN chmod +x gradlew && ./gradlew bootJar -PskipFrontend --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=backend /workspace/build/libs/audit-query-monolith-1.0.0.jar app.jar
ENV SERVER_ADDRESS=0.0.0.0
EXPOSE 8080
USER 10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
