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
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN useradd --system --no-create-home --shell /usr/sbin/nologin vouchers
COPY --from=build /workspace/target/emp-recrut-vouchers-*.jar app.jar
USER vouchers

EXPOSE 8080
# The JRE image has no curl/wget; bash's /dev/tcp is enough for a local health probe.
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 \
        && printf "GET /actuator/health HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 \
        && grep -q "\"status\":\"UP\"" <&3'

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
