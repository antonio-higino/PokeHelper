param(
    [string]$Catalog = 'localhost:50051',
    [string]$Recommendations = 'localhost:50052'
)
$ErrorActionPreference = 'Stop'
function Invoke-Grpcurl([string[]]$Arguments) {
    & grpcurl @Arguments
    if ($LASTEXITCODE -ne 0) { throw "grpcurl failed: $Arguments" }
}
Invoke-Grpcurl @('-plaintext', '-max-time', '15', $Catalog, 'list')
Invoke-Grpcurl @('-plaintext', '-max-time', '15', $Recommendations, 'list')
foreach ($entry in @(@($Catalog, 'pokehelper.v1.PokemonCatalog'), @($Recommendations, 'pokehelper.v1.Recommendations'))) {
    $body = @{ service = $entry[1] } | ConvertTo-Json -Compress
    $result = $body | & grpcurl -plaintext -max-time 15 -d '@' $entry[0] grpc.health.v1.Health/Check
    if ($LASTEXITCODE -ne 0 -or ($result | ConvertFrom-Json).status -ne 'SERVING') { throw "health failed: $result" }
    $result
}
$pokemon = '{"identifier":"pikachu"}' | & grpcurl -plaintext -max-time 15 -d '@' $Catalog pokehelper.v1.PokemonCatalog/GetPokemon
if ($LASTEXITCODE -ne 0) { throw 'GetPokemon failed' }
$data = ($pokemon | ConvertFrom-Json).data
if ($data.name -ne 'pikachu') { throw 'Unexpected Pokemon response' }
"GetPokemon: $($data.name) (id=$($data.id))"
$analysis = '{"pokemons":[{"id":25,"name":"pikachu","types":[{"type":{"name":"electric"}}]}]}' | & grpcurl -plaintext -max-time 30 -d '@' $Recommendations pokehelper.v1.Recommendations/AnalyzeTeam
if ($LASTEXITCODE -ne 0) { throw 'AnalyzeTeam failed' }
$data = ($analysis | ConvertFrom-Json).data
if ($data.coverage.ground.weak -ne 1) { throw 'Unexpected defensive coverage' }
"AnalyzeTeam: ground weakness=$($data.coverage.ground.weak), recommendations=$($data.recommendedTypes.Count)"
