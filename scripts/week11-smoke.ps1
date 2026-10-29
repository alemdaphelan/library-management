param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$root = $BaseUrl.TrimEnd('/')

docker compose config --quiet
if ($LASTEXITCODE -ne 0) {
    throw "docker-compose.yml is invalid"
}

$health = Invoke-RestMethod -Uri "$root/actuator/health" -TimeoutSec 15
if ($health.status -ne "UP") {
    throw "Backend health is not UP"
}

$catalog = Invoke-RestMethod -Uri "$root/books?page=0&size=1" -TimeoutSec 15
if ($null -eq $catalog.content -or $null -eq $catalog.totalElements) {
    throw "Catalog smoke response is invalid"
}

[pscustomobject]@{
    BaseUrl = $root
    Health = $health.status
    CatalogTotal = $catalog.totalElements
    CheckedAt = (Get-Date).ToString("o")
} | ConvertTo-Json
