# ---------- build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Dependencies resolve in their own layer so a source-only change does not re-download them.
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests package

# ---------- run ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Never run the JVM as root: user code is interpreted rather than executed, but defence in
# depth costs nothing here.
RUN addgroup -S algolens && adduser -S algolens -G algolens

COPY --from=build /build/target/*.jar app.jar
RUN chown -R algolens:algolens /app
USER algolens

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
