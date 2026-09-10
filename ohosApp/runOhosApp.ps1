param(
    [string]$DeviceId = "127.0.0.1:5555"
)

$projectScript = Join-Path $PSScriptRoot "..\runOhosApp.ps1"
& $projectScript -DeviceId $DeviceId
exit $LASTEXITCODE
