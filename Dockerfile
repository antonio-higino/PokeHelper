FROM gradle:9.5.1-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle installDist

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/install/ktor-sample ./
EXPOSE 8080
ENTRYPOINT ["/app/bin/ktor-sample"]