package main

import (
	"context"
	"errors"

	pb "github.com/antonio-higino/pokehelper/rpc/gen/pokehelper/v1"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/emptypb"
	"google.golang.org/protobuf/types/known/structpb"
)

type grpcAdapter struct {
	pb.UnimplementedPokemonCatalogServer
	domain *Server
}

func grpcDocument(data map[string]any, err error) (*pb.Document, error) {
	if err != nil {
		switch {
		case errors.Is(err, context.Canceled):
			return nil, status.Error(codes.Canceled, "request canceled")
		case errors.Is(err, context.DeadlineExceeded):
			return nil, status.Error(codes.DeadlineExceeded, "request deadline exceeded")
		case errors.Is(err, errInvalid):
			return nil, status.Error(codes.InvalidArgument, err.Error())
		case errors.Is(err, errNotFound):
			return nil, status.Error(codes.NotFound, err.Error())
		case errors.Is(err, errUnavailable):
			return nil, status.Error(codes.Unavailable, err.Error())
		default:
			return nil, status.Error(codes.Internal, "catalog failed")
		}
	}
	value, err := structpb.NewStruct(data)
	if err != nil {
		return nil, status.Error(codes.Internal, "could not encode document")
	}
	return &pb.Document{Data: value}, nil
}

func (a *grpcAdapter) GetPokemon(ctx context.Context, r *pb.LookupRequest) (*pb.Document, error) {
	return grpcDocument(a.domain.lookup(ctx, "/pokemon/", r.GetIdentifier()))
}
func (a *grpcAdapter) GetSpecies(ctx context.Context, r *pb.LookupRequest) (*pb.Document, error) {
	return grpcDocument(a.domain.lookup(ctx, "/pokemon-species/", r.GetIdentifier()))
}
func (a *grpcAdapter) GetType(ctx context.Context, r *pb.LookupRequest) (*pb.Document, error) {
	return grpcDocument(a.domain.lookup(ctx, "/type/", r.GetIdentifier()))
}
func (a *grpcAdapter) ListTypes(ctx context.Context, _ *emptypb.Empty) (*pb.Document, error) {
	return grpcDocument(a.domain.listTypes(ctx))
}
