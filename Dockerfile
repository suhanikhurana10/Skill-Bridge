# ---------- Stage 1: build the jar with Maven ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# copy pom first so the dependency download is cached until pom.xml changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------- Stage 2: small runtime image ----------
FROM eclipse-temurin:17-jre
LABEL maintainer="KaayakaSetu Team" \
      description="KaayakaSetu Blue-Collar Workforce Platform (Java / Spring Boot)"

# DB_URL / DB_USERNAME / DB_PASSWORD / JWT_SECRET are supplied at run time (see docker-compose.yml)
ENV HOST=0.0.0.0 \
    PORT=8000

WORKDIR /app

# non-root user, same idea as the Python Dockerfile
RUN groupadd -g 1000 appgroup && useradd -u 1000 -g appgroup -s /bin/bash -m appuser \
    && chown -R appuser:appgroup /app

COPY --from=build --chown=appuser:appgroup /build/target/kaayakasetu-1.0.0.jar app.jar

USER appuser
EXPOSE 8000

HEALTHCHECK --interval=20s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -f http://127.0.0.1:${PORT:-8000}/health || exit 1

CMD ["java", "-jar", "app.jar"]
