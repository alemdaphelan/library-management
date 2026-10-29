param(
    [string]$BaseUrl = "http://localhost:8080",
    [Parameter(Mandatory = $true)]
    [string]$AdminToken,
    [string]$OutputPath = "Docs/rag_evaluation_2026-10-20.json",
    [ValidateRange(0, 10000)]
    [int]$DelayMillis = 5500
)

$ErrorActionPreference = "Stop"
$headers = @{ Authorization = "Bearer $AdminToken" }
$uri = "$BaseUrl/api/v1/admin/rag/evaluate?delayMillis=$DelayMillis"
$report = Invoke-RestMethod -Method Post -Uri $uri -Headers $headers
$json = $report | ConvertTo-Json -Depth 20
$resolvedOutput = [System.IO.Path]::GetFullPath((Join-Path (Get-Location) $OutputPath))
$parent = Split-Path -Parent $resolvedOutput
if (-not (Test-Path -LiteralPath $parent)) {
    New-Item -ItemType Directory -Path $parent | Out-Null
}
[System.IO.File]::WriteAllText($resolvedOutput, $json, [System.Text.UTF8Encoding]::new($false))

[pscustomobject]@{
    output = $resolvedOutput
    datasetVersion = $report.datasetVersion
    totalCases = $report.totalCases
    retrievalHitRateAtK = $report.retrievalHitRateAtK
    retrievalRecallAtK = $report.retrievalRecallAtK
    correctSourceRate = $report.correctSourceRate
    groundedAnswerRate = $report.groundedAnswerRate
    citationRate = $report.citationRate
    correctRefusalRate = $report.correctRefusalRate
    fallbackRate = $report.fallbackRate
    p50LatencyMs = $report.p50LatencyMs
    p95LatencyMs = $report.p95LatencyMs
    unexpectedErrors = $report.unexpectedErrors
} | ConvertTo-Json
