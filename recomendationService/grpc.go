package main

import (
	"context"
	"encoding/json"
	"errors"

	pb "github.com/antonio-higino/pokehelper/rpc/gen/pokehelper/v1"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/structpb"
)

type grpcAdapter struct {
	pb.UnimplementedRecommendationsServer
	domain *Server
}

func decodeTeam(documents []*structpb.Struct) ([]Pokemon, error) {
	team := make([]Pokemon, len(documents))
	for i, document := range documents {
		if document == nil {
			return nil, status.Error(codes.InvalidArgument, "pokemon must be an object")
		}
		data, err := json.Marshal(document.AsMap())
		if err != nil {
			return nil, status.Error(codes.InvalidArgument, "invalid pokemon")
		}
		if err = json.Unmarshal(data, &team[i]); err != nil {
			return nil, status.Error(codes.InvalidArgument, "invalid pokemon fields")
		}
	}
	return team, nil
}

func grpcDocument(value any, err error) (*pb.Document, error) {
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
			return nil, status.Error(codes.Internal, "recommendations failed")
		}
	}
	data, err := json.Marshal(value)
	if err != nil {
		return nil, status.Error(codes.Internal, "could not encode result")
	}
	document := new(structpb.Struct)
	if err = document.UnmarshalJSON(data); err != nil {
		return nil, status.Error(codes.Internal, "could not encode document")
	}
	return &pb.Document{Data: document}, nil
}

func (a *grpcAdapter) AnalyzeTeam(ctx context.Context, r *pb.TeamRequest) (*pb.Document, error) {
	team, err := decodeTeam(r.GetPokemons())
	if err != nil {
		return nil, err
	}
	return grpcDocument(a.domain.analyze(ctx, team))
}

func (a *grpcAdapter) RecommendPokemon(ctx context.Context, r *pb.RecommendationRequest) (*pb.Document, error) {
	team, err := decodeTeam(r.GetPokemons())
	if err != nil {
		return nil, err
	}
	items, err := a.domain.recommend(ctx, team, r.GetType())
	return grpcDocument(map[string]any{"items": items}, err)
}
