package main

import (
	"context"
	pb "github.com/antonio-higino/pokehelper/rpc/gen/pokehelper/v1"
	transport "github.com/antonio-higino/pokehelper/rpc/server"
	"google.golang.org/grpc"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"
)

func main() {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	port := os.Getenv("PORT")
	if port == "" {
		port = "8081"
	}
	grpcPort := os.Getenv("GRPC_PORT")
	if grpcPort == "" {
		grpcPort = "50051"
	}
	domain := NewServer(os.Getenv("POKEAPI_BASE_URL"), 6*time.Hour)
	server, health := transport.New(func(s *grpc.Server) { pb.RegisterPokemonCatalogServer(s, &grpcAdapter{domain: domain}) })
	log.Printf("pokemon-api-service HTTP :%s, gRPC :%s", port, grpcPort)
	if err := transport.Run(ctx, server, health, ":"+grpcPort, ":"+port, domain.Handler()); err != nil {
		log.Fatal(err)
	}
}
