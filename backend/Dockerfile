# ---- Build stage: compile and package the WAR with the Maven wrapper -------------------------------------------
FROM eclipse-temurin:25.0.4.1_1-jdk-noble AS build
WORKDIR /build

# Resolve plugins and dependencies in their own layer, so that source changes do not download them again.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -ntp -DskipTests package

COPY src src
RUN ./mvnw -B -ntp -DskipTests package

# ---- Runtime stage: Payara Server Community 7 (Jakarta EE 11 Web Profile) on JDK 25 ---------------------------
FROM payara/server-web:7.2026.9-jdk25

# All timestamps are handled in UTC, whatever the host is set to.
ENV JVM_ARGS="-Duser.timezone=UTC"

# Every archive in $DEPLOY_DIR is deployed at boot; the WAR declares "/" as its context root.
COPY --from=build --chown=payara:payara /build/target/birdy.war ${DEPLOY_DIR}/birdy.war

# The base image already runs as the unprivileged "payara" user. Only the HTTP listener is meant to be published.
EXPOSE 8080

# The image ships neither curl nor wget, so the probe speaks HTTP through bash's /dev/tcp.
HEALTHCHECK --interval=10s --timeout=5s --start-period=90s --retries=6 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /api/health HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 && head -n 1 <&3 | grep -q " 200"'
