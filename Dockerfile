FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY src src
# 테스트는 Testcontainers 가 Docker 를 요구해 이미지 빌드 안에서 돌리지 않는다
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon installDist

FROM eclipse-temurin:25-jre
RUN useradd --system bot
COPY --from=build /app/build/install/DiscordBotPractice /app
WORKDIR /app
USER bot
ENTRYPOINT ["/app/bin/DiscordBotPractice"]
