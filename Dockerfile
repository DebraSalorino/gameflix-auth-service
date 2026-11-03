# =====================================================================
# Dockerfile for the GameFlix Auth Service (Module 5: Dockerize).
# It packages the JAR that Maven already built (./mvnw clean package)
# into a small image that only contains a Java 21 runtime.
#
# Build the JAR first, then the image:
#     ./mvnw clean package -DskipTests
#     docker compose up --build -d
# =====================================================================

# Java 21 runtime (JRE, not the full JDK) on Alpine Linux, a very small base image.
# The version must be 21 or newer because pom.xml compiles for Java 21;
# an older runtime would fail with UnsupportedClassVersionError.
FROM eclipse-temurin:21-jre-alpine

# SECURITY: create an ordinary (non-root) user and group named "app".
# If someone ever broke into the running app, they would not have root
# (administrator) rights inside the container. -S makes a "system" account
# with no password and no login shell. -g/-u give the group and user a fixed
# number (10001), so the USER line below can refer to it by number.
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app

# Every following instruction, and the running app, uses /app as its folder.
WORKDIR /app

# Copy the executable Spring Boot JAR from Maven's target/ folder and give it
# a fixed name. --chown makes the "app" user its owner instead of root.
COPY --chown=app:app target/*.jar app.jar

# From here on, run as the non-root user. The number (user:group) is used
# instead of the name because Docker can always resolve a number, even on
# systems that don't know the name "app" (Hadolint rule DL3066).
USER 10001:10001

# Documents that the app listens on port 8080 inside the container.
# (compose.yaml decides which port on your Mac maps to it.)
EXPOSE 8080

# The command the container runs when it starts: launch the Spring Boot app.
# SECURITY: no passwords or JWT secret appear in this file; compose.yaml passes
# them in as environment variables when the container starts.
ENTRYPOINT ["java", "-jar", "app.jar"]
