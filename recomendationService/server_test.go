package main

import "testing"

func TestAnalyzeTeamReportsWeaknessAndDefensiveType(t *testing.T) {
	types := map[string]TypeData{
		"electric": {Name: "electric", DamageRelations: DamageRelations{DoubleDamageFrom: []NamedResource{{Name: "ground"}}}},
		"ground":   {Name: "ground"},
		"flying":   {Name: "flying", DamageRelations: DamageRelations{NoDamageFrom: []NamedResource{{Name: "ground"}}}},
	}
	team := []Pokemon{{ID: 25, Name: "pikachu", Types: []PokemonTypeSlot{{Type: NamedResource{Name: "electric"}}}}}

	analysis := analyzeTeam(team, types)
	if analysis.Coverage["ground"].Weak != 1 {
		t.Fatalf("expected Pikachu to be weak to ground: %#v", analysis.Coverage["ground"])
	}
	if len(analysis.RecommendedTypes) == 0 || analysis.RecommendedTypes[0].Type != "flying" {
		t.Fatalf("expected flying as the best defensive recommendation: %#v", analysis.RecommendedTypes)
	}
}

func TestValidateTeamRejectsDuplicatedPokemon(t *testing.T) {
	team := []Pokemon{
		{ID: 25, Name: "pikachu", Types: []PokemonTypeSlot{{Type: NamedResource{Name: "electric"}}}},
		{ID: 25, Name: "pikachu", Types: []PokemonTypeSlot{{Type: NamedResource{Name: "electric"}}}},
	}
	if err := validateTeam(team); err == nil {
		t.Fatal("expected duplicate Pokémon validation to fail")
	}
}
