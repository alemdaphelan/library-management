param(
    [Parameter(Mandatory = $true)]
    [string]$BackupPath,
    [string]$Container = "library_postgres",
    [string]$User = "postgres"
)

$ErrorActionPreference = "Stop"
$resolvedBackup = [System.IO.Path]::GetFullPath($BackupPath)
if (-not (Test-Path -LiteralPath $resolvedBackup -PathType Leaf)) {
    throw "Backup file does not exist: $resolvedBackup"
}

$verificationDatabase = "huit_week11_verify_$(Get-Date -Format 'yyyyMMddHHmmss')"
if ($verificationDatabase -notmatch '^huit_week11_verify_[0-9]{14}$') {
    throw "Unsafe verification database name"
}
$containerBackup = "/tmp/huit-week11-verify.dump"

try {
    docker exec $Container createdb -U $User $verificationDatabase
    if ($LASTEXITCODE -ne 0) { throw "Could not create verification database" }
    docker cp $resolvedBackup "${Container}:$containerBackup"
    if ($LASTEXITCODE -ne 0) { throw "Could not copy backup into container" }
    docker exec $Container pg_restore -U $User -d $verificationDatabase --no-owner --no-privileges $containerBackup
    if ($LASTEXITCODE -ne 0) { throw "pg_restore failed" }
    $tableCount = docker exec $Container psql -U $User -d $verificationDatabase -Atc "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'"
    if ($LASTEXITCODE -ne 0 -or [int]$tableCount -lt 1) { throw "Restored database contains no public tables" }
    [pscustomobject]@{ Verified = $true; PublicTableCount = [int]$tableCount; Database = $verificationDatabase } | ConvertTo-Json
} finally {
    docker exec $Container rm -f $containerBackup 2>$null | Out-Null
    docker exec $Container dropdb -U $User --if-exists $verificationDatabase 2>$null | Out-Null
}
