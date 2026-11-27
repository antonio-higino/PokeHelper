package main

import (
	"context"
	"log"
	"os"
	"os/signal"
	"syscall"

	pb "github.com/antonio-higino/pokehelper/rpc/gen/pokehelper/v1"
	transport "github.com/antonio-higino/pokehelper/rpc/server"
	"google.golang.org/grpc"
)

func main() {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	port := os.Getenv("PORT")
	if port == "" {
		port = "8082"
	}
	grpcPort := os.Getenv("GRPC_PORT")
	if grpcPort == "" {
		grpcPort = "50052"
	}
	domain := NewServer(os.Getenv("POKEMON_API_SERVICE_URL"), nil)
	server, health := transport.New(func(s *grpc.Server) { pb.RegisterRecommendationsServer(s, &grpcAdapter{domain: domain}) })
	log.Printf("recommendation-service HTTP :%s, gRPC :%s", port, grpcPort)
	if err := transport.Run(ctx, server, health, ":"+grpcPort, ":"+port, domain.Handler()); err != nil {
		log.Fatal(err)
	}
}
