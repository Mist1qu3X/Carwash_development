# Starts PostgreSQL and the backend (with the React UI) via Docker Compose.
# Usage from the project root:
#   powershell -ExecutionPolicy Bypass -File .\backend\start-compose.ps1
$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot ".env"
$composeFile = Join-Path $PSScriptRoot "docker-compose.yml"

if (-not (Test-Path $envFile)) {
    Write-Host "File .env not found in $projectRoot. Copy .env.example to .env and fill in the values." -ForegroundColor Red
    exit 1
}

docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Docker is not running. Start Docker Desktop and try again." -ForegroundColor Red
    exit 1
}

docker compose --env-file $envFile -f $composeFile up -d --build --wait
if ($LASTEXITCODE -ne 0) {
    Write-Host "docker compose failed. Logs: docker compose --env-file .env -f backend/docker-compose.yml logs backend" -ForegroundColor Red
    exit $LASTEXITCODE
}

$port = (Get-Content $envFile | Where-Object { $_ -match '^APP_PORT=' }) -replace 'APP_PORT=', ''
if (-not $port) { $port = "8080" }
Write-Host ""
Write-Host "Carwash is running:" -ForegroundColor Green
Write-Host "  Web UI   http://localhost:$port/"
Write-Host "  Swagger  http://localhost:$port/swagger-ui.html (admin, HTTP Basic)"
Write-Host "  Health   http://localhost:$port/actuator/health"
