FROM golang:1.27-alpine AS build
WORKDIR /app
COPY go.mod .
COPY . .
RUN CGO_ENABLED=0 go build -o pokemon-api-service .

FROM alpine:3.21
COPY --from=build /app/pokemon-api-service /pokemon-api-service
EXPOSE 8081
ENTRYPOINT ["/pokemon-api-service"]
