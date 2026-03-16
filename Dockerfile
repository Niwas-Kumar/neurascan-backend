# ─── Build ───────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# ─── Runtime ─────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy the built JAR
COPY --from=build /app/target/learning-detection-2.0.0.jar app.jar

# Create uploads directory
RUN mkdir -p /app/uploads

EXPOSE 8080

# Use exec form for proper signal handling
ENTRYPOINT ["java", "-jar", "app.jar"]
