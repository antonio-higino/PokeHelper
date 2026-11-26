package main

import (
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"
)

func TestPokemonEndpointCachesPokeAPIResponse(t *testing.T) {
	var upstreamCalls int32
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&upstreamCalls, 1)
		if r.URL.Path != "/pokemon/pikachu" {
			t.Fatalf("unexpected upstream path: %s", r.URL.Path)
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"id":25,"name":"pikachu"}`))
	}))
	defer upstream.Close()

	server := &Server{client: NewPokeAPIClient(upstream.URL, time.Hour, upstream.Client())}
	api := httptest.NewServer(server.Handler())
	defer api.Close()

	for range 2 {
		response, err := http.Get(api.URL + "/pokemon/pikachu")
		if err != nil {
			t.Fatal(err)
		}
		response.Body.Close()
		if response.StatusCode != http.StatusOK {
			t.Fatalf("expected 200, got %d", response.StatusCode)
		}
	}
	if got := atomic.LoadInt32(&upstreamCalls); got != 1 {
		t.Fatalf("expected one upstream request after cache hit, got %d", got)
	}
}
