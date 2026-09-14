param(
    [Parameter(Mandatory = $true)]
    [string]$DeviceId
)

$projectScript = Join-Path $PSScriptRoot "..\runOhosApp.ps1"
& $projectScript -DeviceId $DeviceId
exit $LASTEXITCODE
