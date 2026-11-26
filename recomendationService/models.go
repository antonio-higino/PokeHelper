package main

type NamedResource struct {
	Name string `json:"name"`
	URL  string `json:"url"`
}

type PokemonTypeSlot struct {
	Slot int           `json:"slot"`
	Type NamedResource `json:"type"`
}

type PokemonStat struct {
	BaseStat int           `json:"base_stat"`
	Stat     NamedResource `json:"stat"`
}

type Pokemon struct {
	ID      int               `json:"id"`
	Name    string            `json:"name"`
	Types   []PokemonTypeSlot `json:"types"`
	Stats   []PokemonStat     `json:"stats"`
	Species NamedResource     `json:"species"`
	Sprites map[string]any    `json:"sprites,omitempty"`
}

type DamageRelations struct {
	DoubleDamageFrom []NamedResource `json:"double_damage_from"`
	HalfDamageFrom   []NamedResource `json:"half_damage_from"`
	NoDamageFrom     []NamedResource `json:"no_damage_from"`
}

type TypePokemon struct {
	Pokemon NamedResource `json:"pokemon"`
}

type TypeData struct {
	Name            string          `json:"name"`
	DamageRelations DamageRelations `json:"damage_relations"`
	Pokemon         []TypePokemon   `json:"pokemon"`
}

type Species struct {
	IsBaby      bool `json:"is_baby"`
	IsLegendary bool `json:"is_legendary"`
	IsMythical  bool `json:"is_mythical"`
}

type Coverage struct {
	Immune        int `json:"immune"`
	VeryResistant int `json:"veryResistant"`
	Resistant     int `json:"resistant"`
	Neutral       int `json:"neutral"`
	Weak          int `json:"weak"`
	VeryWeak      int `json:"veryWeak"`
}

type TypeRecommendation struct {
	Type  string `json:"type"`
	Score int    `json:"score"`
}

type AnalysisResponse struct {
	Coverage         map[string]Coverage  `json:"coverage"`
	RecommendedTypes []TypeRecommendation `json:"recommendedTypes"`
}

type CandidateRecommendation struct {
	Pokemon         Pokemon `json:"pokemon"`
	BST             int     `json:"bst"`
	CombinationRank int     `json:"combinationRank"`
}
