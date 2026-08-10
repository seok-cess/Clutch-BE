FROM eclipse-temurin:21-jdk-alpine@sha256:1ff763083f2993d57d0bf374ab10bb3e2cb873af6c13a04458ebbd3e0337dc76 AS builder

WORKDIR /workspace

COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c

WORKDIR /app

RUN addgroup -S clutch && adduser -S clutch -G clutch
COPY --from=builder --chown=clutch:clutch /workspace/build/libs/app.jar app.jar

USER clutch
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
