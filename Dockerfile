# syntax=docker/dockerfile:1

# --- build ---------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Dependencies first: this layer is cached as long as pom.xml does not change.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
# Tests run in CI (they need Docker for Testcontainers), not inside the image build.
RUN ./mvnw -B -q package -DskipTests

# --- runtime -------------------------------------------------------------
# Alpine (musl) keeps the runtime image small; only this stage ends up in the final image.
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S vouchers && adduser -S -G vouchers -H -s /sbin/nologin vouchers
COPY --from=build /workspace/target/emp-recrut-vouchers-*.jar app.jar
USER vouchers

EXPOSE 8080
# BusyBox wget ships with Alpine, so no extra package is needed for the health probe.
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
