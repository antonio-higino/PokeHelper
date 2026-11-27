# Requires Go and protoc 33.5 in PATH. protoc's include directory must contain
# google/protobuf/{empty,struct}.proto (bundled in the official release).
param()
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$pluginDir = Join-Path ([IO.Path]::GetTempPath()) 'pokehelper-protoc-plugins'
New-Item -ItemType Directory -Force $pluginDir | Out-Null
$previousBin = $env:GOBIN
$previousPath = $env:PATH
try {
    $env:GOBIN = $pluginDir
    $env:PATH = "$pluginDir;$env:PATH"
    go install google.golang.org/protobuf/cmd/protoc-gen-go@v1.36.11
    if ($LASTEXITCODE -ne 0) { throw 'protoc-gen-go installation failed' }
    go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@v1.6.1
    if ($LASTEXITCODE -ne 0) { throw 'protoc-gen-go-grpc installation failed' }
    Push-Location $repoRoot
    try {
        protoc -I protos --go_out=rpc --go_opt=module=github.com/antonio-higino/pokehelper/rpc --go-grpc_out=rpc --go-grpc_opt=module=github.com/antonio-higino/pokehelper/rpc protos/pokehelper/v1/services.proto
        if ($LASTEXITCODE -ne 0) { throw 'protoc failed' }
    } finally { Pop-Location }
} finally {
    $env:GOBIN = $previousBin
    $env:PATH = $previousPath
}
