param(
    [int]$Hours = 6,
    [string]$Package = "online.greatfeng.oksharedpreferences",
    [string]$Activity = "online.greatfeng.oksharedpreferences/.MainActivity",
    [int]$ThrottleMs = 300,
    [int]$BatchEvents = 1000,
    [string]$DeviceSerial = ""
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$runDir = Join-Path $repoRoot "monkey-runs\run_$timestamp"
New-Item -ItemType Directory -Force -Path $runDir | Out-Null

$logFile = Join-Path $runDir "logcat.txt"
$monkeyLog = Join-Path $runDir "monkey.txt"
$statusFile = Join-Path $runDir "status.txt"
$analysisFile = Join-Path $runDir "analysis.md"
$metaFile = Join-Path $runDir "meta.json"

function Write-Status([string]$Message) {
    $line = "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') $Message"
    Write-Host $line
    Add-Content -Path $statusFile -Value $line
}

function Invoke-Adb {
    param([string[]]$AdbArgs)
    $all = @()
    if ($DeviceSerial) { $all += "-s", $DeviceSerial }
    $all += $AdbArgs
    $prevErr = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $output = & adb @all 2>&1 | ForEach-Object { $_.ToString() }
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = $prevErr
    if ($exitCode -ne 0) {
        throw "adb failed: adb $($all -join ' ') (exit $exitCode)`n$output"
    }
    return $output
}

$start = Get-Date
$end = $start.AddHours($Hours)
$deviceName = if ($DeviceSerial) { $DeviceSerial } else { "default" }

@{
    package = $Package
    activity = $Activity
    hours = $Hours
    throttleMs = $ThrottleMs
    batchEvents = $BatchEvents
    start = $start.ToString("o")
    expectedEnd = $end.ToString("o")
    device = $deviceName
} | ConvertTo-Json | Out-File -FilePath $metaFile -Encoding utf8

Write-Status "Run directory: $runDir"
Write-Status "Planned duration: $Hours h, throttle=${ThrottleMs}ms, batchEvents=$BatchEvents"

Invoke-Adb @("wait-for-device")
$deviceInfo = (Invoke-Adb @("shell", "getprop", "ro.product.model") | Out-String).Trim()
Write-Status "Device: $deviceInfo"

Invoke-Adb @("shell", "am", "force-stop", $Package)
Invoke-Adb @("logcat", "-c")
Invoke-Adb @("shell", "input", "keyevent", "KEYCODE_WAKEUP")
Invoke-Adb @("shell", "am", "start", "-n", $Activity)
Start-Sleep -Seconds 2

Write-Status "Starting logcat -> $logFile"
$logcatArgs = @("logcat", "-v", "threadtime", "-b", "main", "-b", "crash", "-b", "system")
if ($DeviceSerial) { $logcatArgs = @("-s", $DeviceSerial) + $logcatArgs }
$logcatProc = Start-Process -FilePath "adb" -ArgumentList $logcatArgs -RedirectStandardOutput $logFile -PassThru -NoNewWindow

$batch = 0
$totalEvents = 0
while ((Get-Date) -lt $end) {
    $batch++
    $remainingMin = [Math]::Max(0, [int]($end - (Get-Date)).TotalMinutes)
    Write-Status "Monkey batch #$batch start (remaining ~${remainingMin} min)"

    $monkeyArgs = @(
        "shell", "monkey",
        "-p", $Package,
        "--throttle", "$ThrottleMs",
        "--ignore-crashes",
        "--ignore-timeouts",
        "--ignore-security-exceptions",
        "--monitor-native-crashes",
        "--pct-touch", "70",
        "--pct-nav", "15",
        "--pct-majornav", "5",
        "--pct-syskeys", "5",
        "--pct-appswitch", "5",
        "-v", "$BatchEvents"
    )
    if ($DeviceSerial) { $monkeyArgs = @("-s", $DeviceSerial) + $monkeyArgs }

    $prevErr = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $output = & adb @monkeyArgs 2>&1
    $ErrorActionPreference = $prevErr
    $output | ForEach-Object { $_.ToString() } | Add-Content -Path $monkeyLog
    $totalEvents += $BatchEvents

    Write-Status "Monkey batch #$batch done (adb exit=$LASTEXITCODE, totalEvents~$totalEvents)"
    Invoke-Adb @("shell", "am", "start", "-n", $Activity) | Out-Null
    Start-Sleep -Seconds 1
}

if (-not $logcatProc.HasExited) {
    Stop-Process -Id $logcatProc.Id -Force -ErrorAction SilentlyContinue
}
Start-Sleep -Seconds 2

Write-Status "Running analysis..."
& (Join-Path $PSScriptRoot "analyze-monkey-log.ps1") -LogFile $logFile -MonkeyLog $monkeyLog -OutputFile $analysisFile -Package $Package

$finished = Get-Date
Write-Status "Done. elapsed=$([int]($finished - $start).TotalMinutes) min, batches=$batch, totalEvents~$totalEvents"
Write-Status "Analysis: $analysisFile"
