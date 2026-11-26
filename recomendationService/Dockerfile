FROM golang:1.27-alpine AS build
WORKDIR /app
COPY go.mod .
COPY . .
RUN CGO_ENABLED=0 go build -o recommendation-service .

FROM alpine:3.21
COPY --from=build /app/recommendation-service /recommendation-service
EXPOSE 8082
ENTRYPOINT ["/recommendation-service"]
