# One image containing the whole app: the React bundle is baked into the Spring Boot jar and
# served by it. One service to deploy, one URL, and no CORS -- which is what makes this fit on a
# free tier, where every extra service is another cold start to pay for.
#
# Build locally with:   docker build -t algolens .
# Run locally with:     docker run -p 8080:8080 -e ALGOLENS_JWT_SECRET=$(openssl rand -base64 48) algolens
#
# For local development you do not want this at all -- run the two dev servers instead, see
# README.md. This exists for deployment.

# ---------- 1. build the frontend ----------
FROM node:20-alpine AS frontend
WORKDIR /ui

# Dependencies resolve in their own layer, so editing a component does not re-install them.
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm ci --no-audit --no-fund

COPY frontend/ ./
# No VITE_API_BASE_URL: the API is same-origin in this image.
RUN npm run build

# ---------- 2. build the backend, with the bundle inside it ----------
FROM maven:3.9-eclipse-temurin-17 AS backend
WORKDIR /build

COPY backend/pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY backend/src ./src
# Anything in src/main/resources/static is served by SpaConfig at the application root.
COPY --from=frontend /ui/dist ./src/main/resources/static
RUN mvn -B -ntp -DskipTests package

# ---------- 3. run ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Never run the JVM as root. User code is interpreted rather than executed, but defence in
# depth costs nothing here.
RUN addgroup -S algolens && adduser -S algolens -G algolens

COPY --from=backend /build/target/*.jar app.jar
RUN chown -R algolens:algolens /app
USER algolens

# Free tiers are typically 512 MB. These flags are what keep a JVM comfortable in that:
#   MaxRAMPercentage=70  leaves headroom for metaspace, code cache and thread stacks, which
#                        live outside the heap and are what actually trigger container OOM kills
#   UseSerialGC          G1's own bookkeeping is not worth it below ~2 cores
#   TieredStopAtLevel=1  faster startup, and startup is what a cold-starting free service pays
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Djava.security.egd=file:/dev/./urandom"

# Most platforms inject PORT and expect the app to honour it.
ENV PORT=8080
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=90s --retries=5 \
  CMD wget -qO- "http://localhost:${PORT}/actuator/health" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT} -jar app.jar"]
