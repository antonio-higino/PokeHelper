FROM golang:1.27-alpine AS build
WORKDIR /app/recomendationService
COPY rpc/ /app/rpc/
COPY recomendationService/ .
RUN CGO_ENABLED=0 go build -o recommendation-service .

FROM alpine:3.21
COPY --from=build /app/recomendationService/recommendation-service /recommendation-service
EXPOSE 8082 50052
ENTRYPOINT ["/recommendation-service"]
