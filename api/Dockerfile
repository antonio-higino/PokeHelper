FROM gradle:9.5.1-jdk21 AS build
WORKDIR /workspace/api
COPY api/settings.gradle.kts api/build.gradle.kts api/gradle.properties ./
COPY api/gradle ./gradle
RUN gradle dependencies --no-daemon -q > /dev/null
COPY protos /workspace/protos
COPY api/src ./src
RUN gradle installDist --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/api/build/install/pokehelper-api ./
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["/app/bin/pokehelper-api"]
