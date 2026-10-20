param(
    [string]$Container = "library_postgres",
    [string]$Database = "huit_library",
    [string]$User = "postgres",
    [string]$OutputPath = "backups/huit_library.dump"
)

$ErrorActionPreference = "Stop"
$resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath)
$parent = Split-Path -Parent $resolvedOutput
if (-not (Test-Path -LiteralPath $parent)) {
    New-Item -ItemType Directory -Path $parent | Out-Null
}

$running = docker inspect --format '{{.State.Running}}' $Container 2>$null
if ($LASTEXITCODE -ne 0 -or $running -ne "true") {
    throw "PostgreSQL container '$Container' is not running"
}

$containerBackup = "/tmp/huit-week11-backup.dump"
try {
    docker exec $Container pg_dump -U $User -d $Database -Fc -f $containerBackup
    if ($LASTEXITCODE -ne 0) { throw "pg_dump failed" }
    docker cp "${Container}:$containerBackup" $resolvedOutput
    if ($LASTEXITCODE -ne 0) { throw "docker cp failed" }
} finally {
    docker exec $Container rm -f $containerBackup 2>$null | Out-Null
}

$file = Get-Item -LiteralPath $resolvedOutput
if ($file.Length -le 0) { throw "Backup file is empty" }
[pscustomobject]@{ Path = $file.FullName; Bytes = $file.Length; Database = $Database } | ConvertTo-Json
