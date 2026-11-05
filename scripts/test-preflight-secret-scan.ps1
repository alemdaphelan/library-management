$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$tokens = $null
$parseErrors = $null
$sourcePath = Join-Path $PSScriptRoot 'week12d-preflight.ps1'
$ast = [System.Management.Automation.Language.Parser]::ParseFile($sourcePath, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count -ne 0) { throw 'Preflight PowerShell syntax error' }
$functionAst = $ast.Find({
    param($node)
    $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
        $node.Name -eq 'Get-SecretScanContent'
}, $true)
if ($null -eq $functionAst) { throw 'Missing preflight scan function' }
. ([scriptblock]::Create($functionAst.Extent.Text))

$clientPath = 'mobile-app/android/app/google-services.json'
$dummyKey = 'AIza' + ('A' * 35)
$pattern = 'AIza[0-9A-Za-z_-]{20,}'
$fixture = @{ client = @(@{ api_key = @(@{ current_key = $dummyKey }) }) }
$json = $fixture | ConvertTo-Json -Depth 10
if ((Get-SecretScanContent $clientPath $json) -match $pattern) {
    throw 'Firebase client identifier was not isolated'
}
if ((Get-SecretScanContent 'server-config.json' $json) -notmatch $pattern) {
    throw 'Other files bypass the secret scan'
}
$fixture['server_key'] = 'AIza' + ('B' * 35)
$withServerKey = $fixture | ConvertTo-Json -Depth 10
if ((Get-SecretScanContent $clientPath $withServerKey) -notmatch $pattern) {
    throw 'Other fields inside client config bypass the secret scan'
}
$invalidRejected = $false
try { Get-SecretScanContent $clientPath '{bad' | Out-Null } catch { $invalidRejected = $true }
if (-not $invalidRejected) { throw 'Malformed client config was accepted' }
Write-Output 'Preflight syntax and 4 client-field isolation checks PASS'
