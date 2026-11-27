package main

import (
	"context"
	"errors"
	"fmt"
	"strings"
)

var errInvalid = errors.New("invalid argument")
var errNotFound = errors.New("resource not found")
var errUnavailable = errors.New("catalog unavailable")

func (s *Server) analyze(ctx context.Context, team []Pokemon) (AnalysisResponse, error) {
	if err := validateTeam(team); err != nil {
		return AnalysisResponse{}, fmt.Errorf("%w: %s", errInvalid, err)
	}
	types, err := s.catalog.types(ctx)
	if err != nil {
		if ctx.Err() != nil {
			return AnalysisResponse{}, ctx.Err()
		}
		return AnalysisResponse{}, errUnavailable
	}
	return analyzeTeam(team, typeIndex(types)), nil
}

func (s *Server) recommend(ctx context.Context, team []Pokemon, requested string) ([]CandidateRecommendation, error) {
	if err := validateTeam(team); err != nil {
		return nil, fmt.Errorf("%w: %s", errInvalid, err)
	}
	requested = strings.ToLower(strings.TrimSpace(requested))
	if requested == "" {
		return nil, fmt.Errorf("%w: type is required", errInvalid)
	}
	types, err := s.catalog.types(ctx)
	if err != nil {
		if ctx.Err() != nil {
			return nil, ctx.Err()
		}
		return nil, errUnavailable
	}
	index := typeIndex(types)
	data, exists := index[requested]
	if !exists {
		return nil, fmt.Errorf("%w: unknown Pokémon type", errInvalid)
	}
	return s.recommendPokemon(ctx, team, requested, data, index)
}
