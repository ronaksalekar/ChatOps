# ===== Multi-stage build =====
# Stage 1: Build the JAR
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sh ./mvnw dependency:go-offline -q
COPY src/ src/
RUN sh ./mvnw -q package -DskipTests

# Stage 2: Lean runtime image
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Create non-root user for security
RUN addgroup -S chatops && adduser -S chatops -G chatops

COPY --from=builder /app/target/*.jar app.jar
RUN chown chatops:chatops app.jar

USER chatops
EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
