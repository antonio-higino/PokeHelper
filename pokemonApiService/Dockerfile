FROM golang:1.27-alpine AS build
WORKDIR /app/pokemonApiService
COPY rpc/ /app/rpc/
COPY pokemonApiService/ .
RUN CGO_ENABLED=0 go build -o pokemon-api-service .

FROM alpine:3.21
COPY --from=build /app/pokemonApiService/pokemon-api-service /pokemon-api-service
EXPOSE 8081 50051
ENTRYPOINT ["/pokemon-api-service"]
