param(
    [string]$DeviceId = "127.0.0.1:5555"
)

$ErrorActionPreference = "Stop"

$projectRoot = $PSScriptRoot
$devecoHome = "C:\Users\shenhk\DevEco Studio"
$ohosSdk = Join-Path $devecoHome "sdk\default\openharmony"
$hdc = Join-Path $ohosSdk "toolchains\hdc.exe"
$hvigor = Join-Path $devecoHome "tools\hvigor\bin\hvigorw.bat"
$konanClang = Join-Path $env:USERPROFILE ".konan\dependencies\llvm-12.0.1-windows-x86_64-20250713\bin\clang++.exe"
$sharedLibrary = Join-Path $projectRoot "shared\build\bin\ohosArm64\debugShared\libshared.so"
$sharedHeader = Join-Path $projectRoot "shared\build\bin\ohosArm64\debugShared\libshared_api.h"
$nativeLibDir = Join-Path $projectRoot "ohosApp\entry\libs\arm64-v8a"
$nativeSourceDir = Join-Path $projectRoot "ohosApp\entry\src\main\cpp"
$hapOutputDir = Join-Path $projectRoot "ohosApp\entry\build\default\outputs\default"

foreach ($tool in @($ohosSdk, $hdc, $hvigor)) {
    if (-not (Test-Path -LiteralPath $tool)) {
        throw "OpenHarmony tool was not found: $tool"
    }
}

try {
    & $konanClang --version | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "clang++ returned exit code $LASTEXITCODE" }
}
catch {
    throw "Windows blocked Kotlin/Native clang++. Allow this file in the application-control policy, then rerun: $konanClang"
}

$env:OHOS_SDK_HOME = $ohosSdk

Push-Location $projectRoot
try {
    & .\gradlew.bat -c .\settings.ohos.gradle.kts :shared:linkDebugSharedOhosArm64 --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "OpenHarmony shared-library build failed." }

    Copy-Item -LiteralPath $sharedLibrary -Destination $nativeLibDir -Force
    Copy-Item -LiteralPath $sharedHeader -Destination $nativeSourceDir -Force

    Push-Location (Join-Path $projectRoot "ohosApp")
    try {
        & $hvigor --mode module -p module=entry@default -p product=default -p requiredDeviceType=phone assembleHap --analyze=normal --parallel
        if ($LASTEXITCODE -ne 0) { throw "HAP package build failed." }
    }
    finally {
        Pop-Location
    }

    $hap = Get-ChildItem -LiteralPath $hapOutputDir -Filter "*-signed.hap" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $hap) { throw "No signed HAP was produced. Configure app signing in DevEco Studio first." }

    $targets = & $hdc list targets
    if ($LASTEXITCODE -ne 0 -or -not ($targets -contains $DeviceId)) {
        throw "Device $DeviceId was not found. Start the emulator or pass -DeviceId."
    }

    & $hdc -t $DeviceId shell aa force-stop com.kuikly.stockchat
    & $hdc -t $DeviceId install -r $hap.FullName
    if ($LASTEXITCODE -ne 0) { throw "HAP installation failed." }
    & $hdc -t $DeviceId shell aa start -a EntryAbility -b com.kuikly.stockchat
    if ($LASTEXITCODE -ne 0) { throw "App launch failed." }

    Write-Host "OpenHarmony app launched on $DeviceId"
}
finally {
    Pop-Location
}
