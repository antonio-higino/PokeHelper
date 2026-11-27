package main

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	pb "github.com/antonio-higino/pokehelper/rpc/gen/pokehelper/v1"
	"github.com/antonio-higino/pokehelper/rpc/testutil"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/structpb"
)

func TestRecommendationsGRPC(t *testing.T) {
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`{"items":[{"name":"electric","damage_relations":{"double_damage_from":[{"name":"ground"}]}},{"name":"ground"},{"name":"flying","damage_relations":{"no_damage_from":[{"name":"ground"}]}}]}`))
	}))
	defer upstream.Close()
	domain := NewServer(upstream.URL, upstream.Client())
	conn := testutil.Dial(t, "pokehelper.v1.Recommendations", func(s *grpc.Server) { pb.RegisterRecommendationsServer(s, &grpcAdapter{domain: domain}) })
	client := pb.NewRecommendationsClient(conn)
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	pokemon := new(structpb.Struct)
	if err := pokemon.UnmarshalJSON([]byte(`{"id":25,"name":"pikachu","types":[{"type":{"name":"electric"}}]}`)); err != nil {
		t.Fatal(err)
	}
	team := []*structpb.Struct{pokemon}
	response, err := client.AnalyzeTeam(ctx, &pb.TeamRequest{Pokemons: team})
	if err != nil {
		t.Fatal(err)
	}
	coverage := response.GetData().AsMap()["coverage"].(map[string]any)
	if coverage["ground"].(map[string]any)["weak"] != float64(1) {
		t.Fatalf("coverage: %v", coverage)
	}
	_, err = client.RecommendPokemon(ctx, &pb.RecommendationRequest{Pokemons: team, Type: "flying"})
	if err != nil {
		t.Fatal(err)
	}
	for _, invalid := range [][]*structpb.Struct{nil, {pokemon, pokemon}} {
		_, err = client.AnalyzeTeam(ctx, &pb.TeamRequest{Pokemons: invalid})
		if status.Code(err) != codes.InvalidArgument {
			t.Fatalf("invalid team: %v", err)
		}
	}
	_, err = client.RecommendPokemon(ctx, &pb.RecommendationRequest{Pokemons: team, Type: "unknown"})
	if status.Code(err) != codes.InvalidArgument {
		t.Fatalf("invalid type: %v", err)
	}
	upstream.Close()
	_, err = client.AnalyzeTeam(ctx, &pb.TeamRequest{Pokemons: team})
	if status.Code(err) != codes.Unavailable {
		t.Fatalf("catalog down: %v", err)
	}
}
