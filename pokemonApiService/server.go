package main

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"sync"
	"time"
)

const defaultPokeAPIBaseURL = "https://pokeapi.co/api/v2"

var expectedTypes = []string{
	"bug", "dark", "dragon", "electric", "fairy", "fighting", "fire", "flying",
	"ghost", "grass", "ground", "ice", "normal", "poison", "psychic", "rock", "steel", "water",
}

type cacheEntry struct {
	body      []byte
	expiresAt time.Time
}

type PokeAPIClient struct {
	baseURL string
	ttl     time.Duration
	client  *http.Client
	mu      sync.RWMutex
	cache   map[string]cacheEntry
}

func NewPokeAPIClient(baseURL string, ttl time.Duration, client *http.Client) *PokeAPIClient {
	if baseURL == "" {
		baseURL = defaultPokeAPIBaseURL
	}
	if ttl <= 0 {
		ttl = 6 * time.Hour
	}
	if client == nil {
		client = &http.Client{Timeout: 12 * time.Second}
	}
	return &PokeAPIClient{
		baseURL: strings.TrimRight(baseURL, "/"), ttl: ttl, client: client, cache: make(map[string]cacheEntry),
	}
}

func (c *PokeAPIClient) Get(resource string) ([]byte, int, error) {
	return c.GetContext(context.Background(), resource)
}

func (c *PokeAPIClient) GetContext(ctx context.Context, resource string) ([]byte, int, error) {
	c.mu.RLock()
	entry, found := c.cache[resource]
	c.mu.RUnlock()
	if found && time.Now().Before(entry.expiresAt) {
		return entry.body, http.StatusOK, nil
	}

	request, err := http.NewRequestWithContext(ctx, http.MethodGet, c.baseURL+resource, nil)
	if err != nil {
		return nil, 0, err
	}
	request.Header.Set("Accept", "application/json")
	request.Header.Set("User-Agent", "PokeHelper/1.0")
	response, err := c.client.Do(request)
	if err != nil {
		return nil, 0, err
	}
	defer response.Body.Close()
	body, err := io.ReadAll(io.LimitReader(response.Body, 10<<20))
	if err != nil {
		return nil, 0, err
	}
	if response.StatusCode >= 200 && response.StatusCode < 300 {
		c.mu.Lock()
		c.cache[resource] = cacheEntry{body: body, expiresAt: time.Now().Add(c.ttl)}
		c.mu.Unlock()
	}
	return body, response.StatusCode, nil
}

type Server struct {
	client *PokeAPIClient
}

func NewServer(baseURL string, ttl time.Duration) *Server {
	return &Server{client: NewPokeAPIClient(baseURL, ttl, nil)}
}

func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, _ *http.Request) {
		writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
	})
	mux.HandleFunc("GET /pokemon/", s.handlePokemon)
	mux.HandleFunc("GET /species/", s.handleSpecies)
	mux.HandleFunc("GET /types", s.handleTypes)
	mux.HandleFunc("GET /types/", s.handleType)
	return mux
}

func (s *Server) handlePokemon(w http.ResponseWriter, r *http.Request) {
	s.proxy(w, r, "/pokemon/", "/pokemon/")
}

func (s *Server) handleSpecies(w http.ResponseWriter, r *http.Request) {
	s.proxy(w, r, "/species/", "/pokemon-species/")
}

func (s *Server) handleType(w http.ResponseWriter, r *http.Request) {
	s.proxy(w, r, "/types/", "/type/")
}

func (s *Server) proxy(w http.ResponseWriter, r *http.Request, prefix, upstreamPrefix string) {
	identifier, err := safeIdentifier(strings.TrimPrefix(r.URL.Path, prefix))
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	body, status, err := s.client.GetContext(r.Context(), upstreamPrefix+url.PathEscape(identifier))
	if err != nil {
		writeError(w, http.StatusBadGateway, "could not reach PokeAPI")
		return
	}
	if status == http.StatusNotFound {
		writeError(w, http.StatusNotFound, "Pokémon resource not found")
		return
	}
	if status < 200 || status >= 300 {
		writeError(w, http.StatusBadGateway, fmt.Sprintf("PokeAPI returned status %d", status))
		return
	}
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write(body)
}

func (s *Server) handleTypes(w http.ResponseWriter, r *http.Request) {
	type result struct {
		index int
		body  json.RawMessage
		err   error
	}
	results := make(chan result, len(expectedTypes))
	for index, typeName := range expectedTypes {
		go func(index int, typeName string) {
			body, status, err := s.client.GetContext(r.Context(), "/type/"+typeName)
			if err != nil {
				results <- result{index: index, err: err}
				return
			}
			if status < 200 || status >= 300 {
				results <- result{index: index, err: fmt.Errorf("PokeAPI returned status %d", status)}
				return
			}
			results <- result{index: index, body: body}
		}(index, typeName)
	}

	items := make([]json.RawMessage, len(expectedTypes))
	for range expectedTypes {
		result := <-results
		if result.err != nil {
			writeError(w, http.StatusBadGateway, "could not load type data from PokeAPI")
			return
		}
		items[result.index] = result.body
	}
	writeJSON(w, http.StatusOK, map[string]any{"items": items})
}

var identifierPattern = regexp.MustCompile(`^[a-z0-9-]+$`)

func safeIdentifier(value string) (string, error) {
	value, err := url.PathUnescape(value)
	if err != nil || !identifierPattern.MatchString(strings.ToLower(value)) {
		return "", fmt.Errorf("resource identifier must be one path segment")
	}
	return strings.ToLower(value), nil
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, map[string]string{"error": message})
}
