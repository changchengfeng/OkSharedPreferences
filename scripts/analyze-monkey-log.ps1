param(
    [Parameter(Mandatory = $true)]
    [string]$LogFile,

    [string]$MonkeyLog = "",
    [string]$OutputFile = "",
    [string]$Package = "online.greatfeng.oksharedpreferences"
)

if (-not (Test-Path $LogFile)) {
    Write-Error "Log file not found: $LogFile"
    exit 1
}

$lines = Get-Content -Path $LogFile -ErrorAction SilentlyContinue
$monkeyLines = @()
if ($MonkeyLog -and (Test-Path $MonkeyLog)) {
    $monkeyLines = Get-Content -Path $MonkeyLog -ErrorAction SilentlyContinue
}

function Get-Matches {
    param([string[]]$Source, [string]$Pattern)
    Select-String -InputObject $Source -Pattern $Pattern -AllMatches | ForEach-Object { $_.Line }
}

$fatal = Get-Matches $lines '(?i)FATAL EXCEPTION'
$anr = Get-Matches $lines '(?i)ANR in'
$crashes = Get-Matches $lines '(?i)(am_crash|Process .* has died|Force finishing activity)'
$native = Get-Matches $lines '(?i)(SIGSEGV|SIGABRT|tombstone|DEBUG\s*:.*backtrace|Fatal signal)'
$decode = Get-Matches $lines '(?i)DecodeException'
$okspErrors = Get-Matches $lines '(?i)(OkSharedPreferencesImpl|OkSharedPreferencesManager|OkSharedPreferences|OkFileObserver).*\sE\s'
$strictMode = Get-Matches $lines '(?i)StrictMode'
$jni = Get-Matches $lines '(?i)(JNI DETECTED ERROR|NoSuchMethodError|UnsatisfiedLinkError)'
$packageLines = Get-Matches $lines $Package

$monkeyCrashes = @()
$monkeyErrors = @()
if ($monkeyLines.Count -gt 0) {
    $monkeyCrashes = Get-Matches $monkeyLines '(?i)(CRASH|Exception|Error)'
    $monkeyErrors = Get-Matches $monkeyLines '(?i)(Events injected|Network stats|Monkey finished)'
}

$report = @()
$report += "# OkSharedPreferences Monkey Log Analysis"
$report += ""
$report += "- Log file: ``$LogFile``"
$report += "- Total log lines: $($lines.Count)"
$report += "- Package-related lines: $($packageLines.Count)"
if ($MonkeyLog) {
    $report += "- Monkey log: ``$MonkeyLog``"
}
$report += ""
$report += "## Summary"
$report += ""
$report += "| Category | Count | Severity |"
$report += "|----------|------:|----------|"
$report += "| FATAL EXCEPTION | $($fatal.Count) | $(if ($fatal.Count -gt 0) { 'CRITICAL' } else { 'OK' }) |"
$report += "| ANR | $($anr.Count) | $(if ($anr.Count -gt 0) { 'CRITICAL' } else { 'OK' }) |"
$report += "| Process crash markers | $($crashes.Count) | $(if ($crashes.Count -gt 0) { 'HIGH' } else { 'OK' }) |"
$report += "| Native crash markers | $($native.Count) | $(if ($native.Count -gt 0) { 'HIGH' } else { 'OK' }) |"
$report += "| DecodeException | $($decode.Count) | $(if ($decode.Count -gt 0) { 'HIGH' } else { 'OK' }) |"
$report += "| OkSP library E logs | $($okspErrors.Count) | $(if ($okspErrors.Count -gt 0) { 'MEDIUM' } else { 'OK' }) |"
$report += "| JNI errors | $($jni.Count) | $(if ($jni.Count -gt 0) { 'HIGH' } else { 'OK' }) |"
$report += "| StrictMode | $($strictMode.Count) | INFO |"
$report += ""

function Add-Section {
    param([string]$Title, [string[]]$Items, [int]$Max = 30)
    $script:report += "## $Title ($($Items.Count))"
    $script:report += ""
    if ($Items.Count -eq 0) {
        $script:report += "_None found._"
    } else {
        $take = [Math]::Min($Items.Count, $Max)
        for ($i = 0; $i -lt $take; $i++) {
            $script:report += "- $($Items[$i])"
        }
        if ($Items.Count -gt $Max) {
            $script:report += "- ... and $($Items.Count - $Max) more"
        }
    }
    $script:report += ""
}

Add-Section "FATAL EXCEPTION" $fatal
Add-Section "ANR" $anr
Add-Section "Process crashes" $crashes
Add-Section "Native crashes" $native
Add-Section "DecodeException" $decode
Add-Section "OkSharedPreferences error logs" $okspErrors
Add-Section "JNI errors" $jni

if ($monkeyLines.Count -gt 0) {
    Add-Section "Monkey summary lines" $monkeyErrors 20
    Add-Section "Monkey crash/error lines" $monkeyCrashes 20
}

$hasCritical = ($fatal.Count + $anr.Count + $crashes.Count + $native.Count + $jni.Count) -gt 0
$report += "## Verdict"
$report += ""
if ($hasCritical) {
    $report += "**FAIL** — Critical crash/ANR/native issues detected. Review sections above."
} elseif ($decode.Count -gt 0 -or $okspErrors.Count -gt 0) {
    $report += "**WARN** — No hard crashes, but library errors were logged. Review OkSP sections."
} else {
    $report += "**PASS** — No crashes, ANRs, or library errors detected in captured logs."
}
$report += ""

$text = ($report -join [Environment]::NewLine)
if ($OutputFile) {
    $text | Out-File -FilePath $OutputFile -Encoding utf8
    Write-Host "Analysis written to $OutputFile"
} else {
    Write-Output $text
}
