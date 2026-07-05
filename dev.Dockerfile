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
    NEW_RELIC_APP_NAME=alert-engine-dev \
    NEW_RELIC_LOG_FILE_NAME=STDOUT \
    NEW_RELIC_DISTRIBUTED_TRACING_ENABLED=true \
    NEW_RELIC_APPLICATION_LOGGING_ENABLED=true \
    NEW_RELIC_APPLICATION_LOGGING_FORWARDING_ENABLED=true \
    NEW_RELIC_APPLICATION_LOGGING_LOCAL_DECORATING_ENABLED=true \
    NEW_RELIC_APPLICATION_LOGGING_METRICS_ENABLED=true

EXPOSE 8080

ENTRYPOINT ["sh","-c","exec java -Dserver.port=$PORT -javaagent:/app/newrelic.jar -jar /app/app.jar"]
