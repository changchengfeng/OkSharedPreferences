param(
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$gradleProps = Join-Path $env:USERPROFILE ".gradle\gradle.properties"

function Test-GradleProperty([string]$Name) {
    if (-not (Test-Path $gradleProps)) { return $false }
    return (Select-String -Path $gradleProps -Pattern "^$Name=" -Quiet)
}

$required = @(
    @{ Name = "centralPortalUsername"; Alt = "ossrhUsername" },
    @{ Name = "centralPortalPassword"; Alt = "ossrhPassword" },
    @{ Name = "signingKeyId"; Alt = $null },
    @{ Name = "signingKey"; Alt = $null },
    @{ Name = "signingPassword"; Alt = $null }
)

$missing = @()
foreach ($item in $required) {
    $ok = Test-GradleProperty $item.Name
    if (-not $ok -and $item.Alt) {
        $ok = Test-GradleProperty $item.Alt
    }
    if (-not $ok) { $missing += $item.Name }
}

if ($missing.Count -gt 0) {
    Write-Host "Missing properties in $gradleProps:"
    $missing | ForEach-Object { Write-Host "  - $_" }
    Write-Host ""
    Write-Host "See gradle.properties.example in repo root."
    exit 1
}

Push-Location $repoRoot
if ($DryRun) {
    .\gradlew.bat :OkSharedPreferences:assembleRelease :OkSharedPreferences:publishMavenAarPublicationToMavenCentralRepository --dry-run
} else {
    .\gradlew.bat :OkSharedPreferences:assembleRelease :OkSharedPreferences:publishMavenAarPublicationToMavenCentralRepository
}
Pop-Location

Write-Host ""
Write-Host "After upload, open https://central.sonatype.com/publishing to release the deployment."
