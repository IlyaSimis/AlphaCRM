# syntax=docker/dockerfile:1.7
# Сборка backend: Maven + JDK 21, результат — слоёный образ на JRE 21 без root.

FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY backend/app/pom.xml backend/app/pom.xml
# Сначала только pom-файлы: зависимости кэшируются, пока не меняются pom.xml.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl backend/app -am dependency:resolve dependency:resolve-plugins || true
COPY backend/app/src backend/app/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl backend/app -am package -DskipTests -Dspotless.check.skip=true \
 && cp backend/app/target/quiet-clinic-api-*.jar /workspace/app.jar \
 && java -Djarmode=tools -jar /workspace/app.jar extract --layers --launcher --destination /workspace/extracted

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S clinic && adduser -S -G clinic -H -s /sbin/nologin clinic \
 && mkdir -p /var/lib/alphacrm/drive && chown clinic:clinic /var/lib/alphacrm/drive
WORKDIR /app
COPY --from=build --chown=clinic:clinic /workspace/extracted/dependencies/ ./
COPY --from=build --chown=clinic:clinic /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=clinic:clinic /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=clinic:clinic /workspace/extracted/application/ ./
USER clinic
ENV SPRING_PROFILES_ACTIVE=prod \
    PORT=8081 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom"
EXPOSE 8081
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8081/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
