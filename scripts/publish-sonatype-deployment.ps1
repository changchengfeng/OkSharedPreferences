param(
    [Parameter(Mandatory = $true)]
    [string]$DeploymentId
)

$ErrorActionPreference = "Stop"
$gradleProps = Join-Path $env:USERPROFILE ".gradle\gradle.properties"
if (-not (Test-Path $gradleProps)) {
    throw "Missing $gradleProps"
}

$map = @{}
Get-Content $gradleProps | Where-Object { $_ -match '^[^#].*=' } | ForEach-Object {
    $k, $v = $_.Split('=', 2)
    $map[$k] = $v
}

$user = $map['centralPortalUsername']
$pass = $map['centralPortalPassword']
if ([string]::IsNullOrWhiteSpace($user) -or [string]::IsNullOrWhiteSpace($pass)) {
    throw "centralPortalUsername / centralPortalPassword not found in gradle.properties"
}

$auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${user}:${pass}"))
$headers = @{ Authorization = "Bearer $auth" }

Write-Host "Checking deployment $DeploymentId ..."
$status = Invoke-RestMethod -Method Post -Uri "https://central.sonatype.com/api/v1/publisher/status?id=$DeploymentId" -Headers $headers
$status | ConvertTo-Json -Depth 6

if ($status.deploymentState -eq 'VALIDATED') {
    Write-Host "Publishing deployment $DeploymentId ..."
    Invoke-RestMethod -Method Post -Uri "https://central.sonatype.com/api/v1/publisher/deployment/$DeploymentId" -Headers $headers
    Write-Host "Publish requested."
} else {
    Write-Host "Deployment is $($status.deploymentState); publish manually when VALIDATED."
}
