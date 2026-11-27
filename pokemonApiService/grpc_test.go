package main

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	pb "github.com/antonio-higino/pokehelper/rpc/gen/pokehelper/v1"
	"github.com/antonio-higino/pokehelper/rpc/testutil"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/emptypb"
)

func TestCatalogGRPC(t *testing.T) {
	canceled := make(chan struct{}, 1)
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case strings.HasSuffix(r.URL.Path, "/missing"):
			w.WriteHeader(404)
		case strings.HasSuffix(r.URL.Path, "/broken"):
			w.WriteHeader(503)
		case strings.HasSuffix(r.URL.Path, "/slow"):
			<-r.Context().Done()
			canceled <- struct{}{}
		default:
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(`{"id":25,"name":"pikachu","sprites":{"extra":"preserved"}}`))
		}
	}))
	defer upstream.Close()
	conn := testutil.Dial(t, "pokehelper.v1.PokemonCatalog", func(s *grpc.Server) {
		pb.RegisterPokemonCatalogServer(s, &grpcAdapter{domain: NewServer(upstream.URL, time.Hour)})
	})
	client := pb.NewPokemonCatalogClient(conn)
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	for _, method := range []func(context.Context, *pb.LookupRequest, ...grpc.CallOption) (*pb.Document, error){client.GetPokemon, client.GetType, client.GetSpecies} {
		response, err := method(ctx, &pb.LookupRequest{Identifier: "pikachu"})
		if err != nil || response.GetData().AsMap()["name"] != "pikachu" {
			t.Fatalf("lookup: %v %v", response, err)
		}
	}
	types, err := client.ListTypes(ctx, &emptypb.Empty{})
	if err != nil || len(types.GetData().AsMap()["items"].([]any)) != 18 {
		t.Fatalf("types: %v %v", types, err)
	}
	for _, test := range []struct {
		id   string
		code codes.Code
	}{{"", codes.InvalidArgument}, {"a/b", codes.InvalidArgument}, {"missing", codes.NotFound}, {"broken", codes.Unavailable}} {
		_, err := client.GetPokemon(ctx, &pb.LookupRequest{Identifier: test.id})
		if status.Code(err) != test.code {
			t.Errorf("%q: got %v, want %v", test.id, err, test.code)
		}
	}
	deadline, stop := context.WithTimeout(ctx, 50*time.Millisecond)
	defer stop()
	_, err = client.GetPokemon(deadline, &pb.LookupRequest{Identifier: "slow"})
	if status.Code(err) != codes.DeadlineExceeded {
		t.Fatalf("deadline: %v", err)
	}
	select {
	case <-canceled:
	case <-time.After(time.Second):
		t.Fatal("upstream did not receive cancellation")
	}
}
