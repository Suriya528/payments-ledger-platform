# ---- Stage 1: Build & Package with Maven ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Copy Maven wrapper & POM to leverage Docker layer caching
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B || true

# Copy source code and package application
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# ---- Stage 2: Minimal Slim JRE Runtime ----
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Run as non-root user for container security
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/target/*.jar app.jar
USER app

EXPOSE 8080
ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
