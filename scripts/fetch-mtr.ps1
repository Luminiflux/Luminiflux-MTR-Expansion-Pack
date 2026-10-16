# Fetches the Minecraft Transit Railway 3.x Forge build that LMEP compiles against.
#
# The jar is deliberately not committed (it is ~76 MB). Run this once after cloning:
#     pwsh -File scripts/fetch-mtr.ps1
#
# The download URL is resolved live through the Modrinth API rather than hard-coded,
# so this keeps working when Modrinth rotates its CDN paths.

$ErrorActionPreference = 'Stop'

$modVersion = '1.20.1-3.2.2-hotfix-2'
$slug       = 'minecraft-transit-railway'
$target     = Join-Path $PSScriptRoot "..\libs\mtr-forge-$modVersion.jar"
$headers    = @{ 'User-Agent' = 'LMEP-build-script/1.0 (github.com/Luminiflux)' }

if (Test-Path $target) {
    Write-Host "Already present: $target"
    exit 0
}

New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null

Write-Host "Resolving $slug $modVersion (loaders=forge, game_versions=1.20.1) ..."
$query = "https://api.modrinth.com/v2/project/$slug/version?loaders=%5B%22forge%22%5D&game_versions=%5B%221.20.1%22%5D"
$versions = Invoke-RestMethod -Uri $query -Headers $headers -TimeoutSec 60

# Modrinth labels MTR releases as "FORGE-4.0.5+1.20.1", which is not the version string
# in the mod's own metadata. Match on the artifact filename instead - it carries the
# metadata version verbatim, e.g. "MTR-forge-1.20.1-3.2.2-hotfix-2.jar".
$match = $null
$matchFile = $null
foreach ($candidate in $versions) {
    foreach ($candidateFile in $candidate.files) {
        if ($candidateFile.filename -like "*$modVersion*") {
            $match = $candidate
            $matchFile = $candidateFile
            break
        }
    }
    if ($match) { break }
}

if (-not $match) {
    Write-Warning "Modrinth has no Forge 1.20.1 file matching '$modVersion'. Available releases:"
    $versions | ForEach-Object { Write-Host ("  " + $_.version_number) }
    Write-Warning "Download the jar manually from https://modrinth.com/mod/$slug/versions and drop it in libs/."
    exit 1
}

Write-Host ("Downloading {0} ({1:N1} MB) ..." -f $matchFile.filename, ($matchFile.size / 1MB))
Invoke-WebRequest -Uri $matchFile.url -OutFile $target -Headers $headers -UseBasicParsing
Write-Host "Saved to $target"

$architectury = Join-Path $PSScriptRoot '..\libs\architectury-9.2.14-forge.jar'
if (-not (Test-Path $architectury)) {
    Write-Warning "Architectury API is missing at $architectury."
    Write-Warning "MTR 3.x requires it at runtime; get 'Architectury API 9.2.14' for 1.20.1 Forge"
    Write-Warning "from https://modrinth.com/mod/architectury-api/versions and place it in libs/."
}
