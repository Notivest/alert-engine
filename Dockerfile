###############################################################################
# 1) BUILD
###############################################################################
FROM gradle:8.5-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle bootJar --no-daemon

###############################################################################
# 2) RUNTIME
###############################################################################
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copiamos la app
COPY --from=build /app/build/libs/*.jar app.jar

# 🔑 Descargamos SOLO el JAR correcto
RUN curl -fsSL \
    https://download.newrelic.com/newrelic/java-agent/newrelic-agent/current/newrelic.jar \
    -o newrelic.jar

ENV PORT=8080 \
    NEW_RELIC_APP_NAME=alert-engine-prod \
    NEW_RELIC_LOG_FILE_NAME=STDOUT

EXPOSE 8080

ENTRYPOINT ["sh","-c","exec java -Dserver.port=$PORT -javaagent:/app/newrelic.jar -jar /app/app.jar"]
