package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"sort"
	"strings"
	"sync"
	"time"
)

const defaultPokemonServiceURL = "http://localhost:8081"

var excludedFormMarkers = []string{"-mega", "-mega-x", "-mega-y", "-gmax", "-totem"}

type catalogClient struct {
	baseURL string
	client  *http.Client
}

func newCatalogClient(baseURL string, client *http.Client) *catalogClient {
	if baseURL == "" {
		baseURL = defaultPokemonServiceURL
	}
	if client == nil {
		client = &http.Client{Timeout: 20 * time.Second}
	}
	return &catalogClient{baseURL: strings.TrimRight(baseURL, "/"), client: client}
}

func (c *catalogClient) get(path string, destination any) error {
	response, err := c.client.Get(c.baseURL + path)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	if response.StatusCode == http.StatusNotFound {
		return fmt.Errorf("catalog resource not found")
	}
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return fmt.Errorf("catalog returned status %d", response.StatusCode)
	}
	return json.NewDecoder(io.LimitReader(response.Body, 12<<20)).Decode(destination)
}

func (c *catalogClient) types() ([]TypeData, error) {
	var response struct {
		Items []TypeData `json:"items"`
	}
	if err := c.get("/types", &response); err != nil {
		return nil, err
	}
	if len(response.Items) == 0 {
		return nil, errors.New("catalog returned no type data")
	}
	return response.Items, nil
}

func (c *catalogClient) pokemon(name string) (Pokemon, error) {
	var pokemon Pokemon
	err := c.get("/pokemon/"+url.PathEscape(name), &pokemon)
	return pokemon, err
}

func (c *catalogClient) species(name string) (Species, error) {
	var species Species
	err := c.get("/species/"+url.PathEscape(name), &species)
	return species, err
}

type Server struct {
	catalog *catalogClient
}

func NewServer(catalogURL string, client *http.Client) *Server {
	return &Server{catalog: newCatalogClient(catalogURL, client)}
}

func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, _ *http.Request) {
		writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
	})
	mux.HandleFunc("POST /recommendations", s.handleAnalysis)
	mux.HandleFunc("POST /recommendations/pokemon", s.handlePokemonRecommendations)
	return mux
}

func (s *Server) handleAnalysis(w http.ResponseWriter, r *http.Request) {
	pokemons, err := requestTeam(r)
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	types, err := s.catalog.types()
	if err != nil {
		writeError(w, http.StatusBadGateway, "could not load type data")
		return
	}
	writeJSON(w, http.StatusOK, analyzeTeam(pokemons, typeIndex(types)))
}

func (s *Server) handlePokemonRecommendations(w http.ResponseWriter, r *http.Request) {
	var request struct {
		Pokemons []Pokemon `json:"pokemons"`
		Type     string    `json:"type"`
	}
	if err := json.NewDecoder(io.LimitReader(r.Body, 2<<20)).Decode(&request); err != nil {
		writeError(w, http.StatusBadRequest, "body must be valid JSON")
		return
	}
	if err := validateTeam(request.Pokemons); err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	request.Type = strings.ToLower(strings.TrimSpace(request.Type))
	if request.Type == "" {
		writeError(w, http.StatusBadRequest, "type is required")
		return
	}
	types, err := s.catalog.types()
	if err != nil {
		writeError(w, http.StatusBadGateway, "could not load type data")
		return
	}
	index := typeIndex(types)
	requestedType, exists := index[request.Type]
	if !exists {
		writeError(w, http.StatusBadRequest, "unknown Pokémon type")
		return
	}

	recommendations, err := s.recommendPokemon(request.Pokemons, request.Type, requestedType, index)
	if err != nil {
		writeError(w, http.StatusBadGateway, "could not load Pokémon candidates")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"items": recommendations})
}

func requestTeam(r *http.Request) ([]Pokemon, error) {
	var request struct {
		Pokemons []Pokemon `json:"pokemons"`
	}
	if err := json.NewDecoder(io.LimitReader(r.Body, 2<<20)).Decode(&request); err != nil {
		return nil, errors.New("body must be valid JSON")
	}
	return request.Pokemons, validateTeam(request.Pokemons)
}

func validateTeam(team []Pokemon) error {
	if len(team) < 1 || len(team) > 6 {
		return errors.New("a team must contain between 1 and 6 pokemons")
	}
	ids := make(map[int]bool, len(team))
	for _, pokemon := range team {
		if pokemon.ID <= 0 || strings.TrimSpace(pokemon.Name) == "" || len(pokemon.Types) == 0 {
			return errors.New("each pokemon needs an id, name and at least one type")
		}
		if ids[pokemon.ID] {
			return errors.New("a team cannot contain repeated pokemons")
		}
		ids[pokemon.ID] = true
	}
	return nil
}

func typeIndex(types []TypeData) map[string]TypeData {
	index := make(map[string]TypeData, len(types))
	for _, typeData := range types {
		index[typeData.Name] = typeData
	}
	return index
}

func analyzeTeam(team []Pokemon, types map[string]TypeData) AnalysisResponse {
	coverage := coverageMatrix(team, types)
	return AnalysisResponse{Coverage: coverage, RecommendedTypes: recommendedTypes(coverage, types)}
}

func coverageMatrix(team []Pokemon, types map[string]TypeData) map[string]Coverage {
	coverage := make(map[string]Coverage, len(types))
	for attackType := range types {
		coverage[attackType] = Coverage{}
	}
	for _, pokemon := range team {
		for attackType, multiplier := range pokemonMultipliers(pokemon, types) {
			value := coverage[attackType]
			switch {
			case multiplier == 0:
				value.Immune++
			case multiplier <= 0.25:
				value.VeryResistant++
			case multiplier == 0.5:
				value.Resistant++
			case multiplier == 2:
				value.Weak++
			case multiplier >= 4:
				value.VeryWeak++
			default:
				value.Neutral++
			}
			coverage[attackType] = value
		}
	}
	return coverage
}

func pokemonMultipliers(pokemon Pokemon, types map[string]TypeData) map[string]float64 {
	multipliers := make(map[string]float64, len(types))
	for name := range types {
		multipliers[name] = 1
	}
	for _, slot := range pokemon.Types {
		typeData, exists := types[slot.Type.Name]
		if !exists {
			continue
		}
		for _, resource := range typeData.DamageRelations.DoubleDamageFrom {
			multipliers[resource.Name] *= 2
		}
		for _, resource := range typeData.DamageRelations.HalfDamageFrom {
			multipliers[resource.Name] *= 0.5
		}
		for _, resource := range typeData.DamageRelations.NoDamageFrom {
			multipliers[resource.Name] = 0
		}
	}
	return multipliers
}

func coverageScore(coverage Coverage) float64 {
	return float64(coverage.Immune)*2 + float64(coverage.VeryResistant)*1.5 + float64(coverage.Resistant) -
		float64(coverage.Weak) - float64(coverage.VeryWeak)*1.5
}

func teamScore(team []Pokemon, types map[string]TypeData) float64 {
	penalty := 0.0
	for _, coverage := range coverageMatrix(team, types) {
		if score := coverageScore(coverage); score < 0 {
			penalty += score * score
		}
	}
	return -penalty
}

func recommendedTypes(coverage map[string]Coverage, types map[string]TypeData) []TypeRecommendation {
	weaknesses := make(map[string]bool)
	for attackType, value := range coverage {
		if value.VeryWeak*2+value.Weak > value.Immune+value.VeryResistant+value.Resistant {
			weaknesses[attackType] = true
		}
	}
	recommendations := make([]TypeRecommendation, 0, len(types))
	for name, typeData := range types {
		score := 0
		for weakness := range weaknesses {
			if hasType(typeData.DamageRelations.NoDamageFrom, weakness) {
				score += 4
			}
			if hasType(typeData.DamageRelations.HalfDamageFrom, weakness) {
				score += 2
			}
			if hasType(typeData.DamageRelations.DoubleDamageFrom, weakness) {
				score -= 2
			}
		}
		recommendations = append(recommendations, TypeRecommendation{Type: name, Score: score})
	}
	sort.Slice(recommendations, func(i, j int) bool {
		if recommendations[i].Score == recommendations[j].Score {
			return recommendations[i].Type < recommendations[j].Type
		}
		return recommendations[i].Score > recommendations[j].Score
	})
	if len(recommendations) > 5 {
		recommendations = recommendations[:5]
	}
	return recommendations
}

func hasType(resources []NamedResource, target string) bool {
	for _, resource := range resources {
		if resource.Name == target {
			return true
		}
	}
	return false
}

func (s *Server) recommendPokemon(team []Pokemon, primaryType string, requestedType TypeData, types map[string]TypeData) ([]CandidateRecommendation, error) {
	ranks := combinationRanks(team, primaryType, types)
	inTeam := make(map[int]bool, len(team))
	for _, pokemon := range team {
		inTeam[pokemon.ID] = true
	}
	entries := requestedType.Pokemon
	if len(entries) > 150 {
		entries = entries[:150]
	}
	jobs := make(chan NamedResource)
	results := make(chan *CandidateRecommendation, len(entries))
	var workers sync.WaitGroup
	for range 12 {
		workers.Add(1)
		go func() {
			defer workers.Done()
			for resource := range jobs {
				if isExcludedForm(resource.Name) {
					continue
				}
				pokemon, err := s.catalog.pokemon(resource.Name)
				if err != nil || inTeam[pokemon.ID] {
					continue
				}
				species, err := s.catalog.species(pokemon.Species.Name)
				if err != nil || species.IsBaby || species.IsLegendary || species.IsMythical {
					continue
				}
				key := combinationKey(primaryType, pokemon)
				rank, exists := ranks[key]
				if !exists {
					rank = 999
				}
				results <- &CandidateRecommendation{Pokemon: pokemon, BST: calculateBST(pokemon), CombinationRank: rank}
			}
		}()
	}
	go func() {
		for _, entry := range entries {
			jobs <- entry.Pokemon
		}
		close(jobs)
		workers.Wait()
		close(results)
	}()

	candidates := make([]CandidateRecommendation, 0, len(entries))
	for candidate := range results {
		candidates = append(candidates, *candidate)
	}
	sort.Slice(candidates, func(i, j int) bool { return candidates[i].BST > candidates[j].BST })
	candidates = candidates[:(len(candidates)+1)/2]
	sort.Slice(candidates, func(i, j int) bool {
		if candidates[i].CombinationRank == candidates[j].CombinationRank {
			return candidates[i].BST > candidates[j].BST
		}
		return candidates[i].CombinationRank < candidates[j].CombinationRank
	})
	if len(candidates) > 24 {
		candidates = candidates[:24]
	}
	return candidates, nil
}

func calculateBST(pokemon Pokemon) int {
	total := 0
	for _, stat := range pokemon.Stats {
		total += stat.BaseStat
	}
	return total
}

func combinationRanks(team []Pokemon, primary string, types map[string]TypeData) map[string]int {
	typeNames := make([]string, 0, len(types))
	for name := range types {
		typeNames = append(typeNames, name)
	}
	sort.Strings(typeNames)
	type combination struct {
		key   string
		score float64
	}
	combinations := []combination{{key: primary, score: teamScore(appendVirtual(team, primary, ""), types)}}
	for _, secondary := range typeNames {
		if secondary != primary {
			combinations = append(combinations, combination{key: primary + "/" + secondary, score: teamScore(appendVirtual(team, primary, secondary), types)})
		}
	}
	sort.Slice(combinations, func(i, j int) bool { return combinations[i].score > combinations[j].score })
	ranks := make(map[string]int, len(combinations))
	for index, combination := range combinations {
		ranks[combination.key] = index
	}
	return ranks
}

func appendVirtual(team []Pokemon, primary, secondary string) []Pokemon {
	virtual := Pokemon{ID: -1, Name: "virtual", Types: []PokemonTypeSlot{{Type: NamedResource{Name: primary}}}}
	if secondary != "" {
		virtual.Types = append(virtual.Types, PokemonTypeSlot{Type: NamedResource{Name: secondary}})
	}
	return append(append([]Pokemon{}, team...), virtual)
}

func combinationKey(primary string, pokemon Pokemon) string {
	if len(pokemon.Types) == 1 {
		return primary
	}
	for _, slot := range pokemon.Types {
		if slot.Type.Name != primary {
			return primary + "/" + slot.Type.Name
		}
	}
	return primary
}

func isExcludedForm(name string) bool {
	for _, marker := range excludedFormMarkers {
		if strings.Contains(name, marker) {
			return true
		}
	}
	return false
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, map[string]string{"error": message})
}
