param(
    [Parameter(Mandatory = $true)]
    [string]$DeviceId
)

$ErrorActionPreference = "Stop"
$env:CI = "true"

$projectRoot = $PSScriptRoot

# DevEco Studio install location is machine-specific: never hard-code it.
# Resolution order: DEVECO_SDK_HOME -> sibling of the OHOS SDK -> common install roots.
$devecoHome = $null
if ($env:DEVECO_SDK_HOME -and (Test-Path -LiteralPath (Join-Path $env:DEVECO_SDK_HOME "sdk\default\openharmony"))) {
    $devecoHome = $env:DEVECO_SDK_HOME
}
if (-not $devecoHome) {
    $searchRoots = New-Object System.Collections.ArrayList
    if ($env:OHOS_SDK_HOME) {
        [void]$searchRoots.Add((Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $env:OHOS_SDK_HOME))))
    }
    [void]$searchRoots.Add("D:\dev\DevEco Studio")
    [void]$searchRoots.Add("C:\Program Files\Huawei\DevEco Studio")
    [void]$searchRoots.Add((Join-Path $env:LOCALAPPDATA "Huawei\DevEco Studio"))
    [void]$searchRoots.Add((Join-Path $env:USERPROFILE "DevEco Studio"))
    $devecoHome = $searchRoots |
        Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ "sdk\default\openharmony")) } |
        Select-Object -First 1
}
if (-not $devecoHome) {
    throw "DevEco Studio was not found. Set DEVECO_SDK_HOME to the DevEco install root, then rerun."
}

$ohosSdk = Join-Path $devecoHome "sdk\default\openharmony"
$hdc = Join-Path $ohosSdk "toolchains\hdc.exe"
# Use DevEco's hvigor wrapper, not the bare hvigor.js: only the wrapper wires up
# the bundled @ohos/hvigor-ohos-plugin resolution (bare node hvigor.js dies with
# "Cannot find module '@ohos/hvigor-ohos-plugin'").
$hvigor = Join-Path $devecoHome "tools\hvigor\bin\hvigorw.bat"
$ohosNode = Join-Path $devecoHome "tools\node"

# The Kotlin/Native LLVM bundle is downloaded on the first ohosArm64 link and its
# version directory changes over time, so discover it instead of pinning a name.
$konanClang = Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE ".konan\dependencies") `
        -Filter "clang++.exe" -Recurse -ErrorAction SilentlyContinue |
    Select-Object -First 1 -ExpandProperty FullName
$sharedLibrary = Join-Path $projectRoot "shared\build\bin\ohosArm64\debugShared\libshared.so"
$sharedHeader = Join-Path $projectRoot "shared\build\bin\ohosArm64\debugShared\libshared_api.h"
$nativeLibDir = Join-Path $projectRoot "ohosApp\entry\libs\arm64-v8a"
$nativeSourceDir = Join-Path $projectRoot "ohosApp\entry\src\main\cpp"
$hapOutputDir = Join-Path $projectRoot "ohosApp\entry\build\default\outputs\default"
$ohosPageAssetDir = Join-Path $projectRoot "ohosApp\entry\src\main\resources\rawfile\ApiConfigPage"

foreach ($tool in @($ohosSdk, $hdc, $hvigor)) {
    if (-not (Test-Path -LiteralPath $tool)) {
        throw "OpenHarmony tool was not found: $tool"
    }
}

# The prebuilt OHOS transport libs are gitignored (*.so), so a fresh clone has to
# restore them by hand. Fail loudly here instead of at the linker.
$requiredPrebuiltLibs = @("libpbcurlwrapper.so", "libc++_shared.so", "libopenssl.so")
$missingLibs = $requiredPrebuiltLibs |
    Where-Object { -not (Test-Path -LiteralPath (Join-Path $nativeLibDir $_)) }
if ($missingLibs) {
    throw ("Missing prebuilt OHOS libs in $nativeLibDir : $($missingLibs -join ', '). " +
        "Fetch them from Tencent-TDS/KuiklyBase-components -> NetworkKMM/ohosApp/entry/libs/arm64-v8a/ .")
}

# clang++ normally appears only after the first link downloads the Kotlin/Native
# LLVM bundle, so a missing file is expected on a fresh machine. Warn, don't fail.
if (-not $konanClang) {
    Write-Warning "Kotlin/Native clang++ not found under ~/.konan/dependencies yet; the first link will download it."
}
else {
    try {
        & $konanClang --version | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "clang++ returned exit code $LASTEXITCODE" }
    }
    catch {
        Write-Warning ("clang++ could not run ($konanClang). If Windows application-control is blocking it, " +
            "allow the file and rerun. Otherwise the first link will re-fetch the toolchain.")
    }
}

$env:OHOS_SDK_HOME = $ohosSdk
$env:DEVECO_SDK_HOME = $devecoHome

Push-Location $projectRoot
try {
    & .\gradlew.bat -c .\settings.ohos.gradle.kts :shared:linkDebugSharedOhosArm64 --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "OpenHarmony shared-library build failed." }

    Copy-Item -LiteralPath $sharedLibrary -Destination $nativeLibDir -Force
    Copy-Item -LiteralPath $sharedHeader -Destination $nativeSourceDir -Force

    # Kuikly's ImageUri.pageAssets resolves from the HAP rawfile bundle on
    # HarmonyOS. The Android source-set asset declaration does not feed the
    # separate HAP build, so mirror the shared page assets before packaging.
    New-Item -ItemType Directory -Path $ohosPageAssetDir -Force | Out-Null
    Get-ChildItem -LiteralPath (Join-Path $projectRoot "shared\src\commonMain\assets\ApiConfigPage") -File |
        Copy-Item -Destination $ohosPageAssetDir -Force

    Push-Location (Join-Path $projectRoot "ohosApp")
    try {
        # hvigorw.bat shells out to node.exe by name, so DevEco's bundled Node
        # must be reachable both by PATH and by NODE_HOME.
        $env:NODE_HOME = $ohosNode
        $env:PATH = "$ohosNode;$env:PATH"
        # Known failure mode on machines where the Shell/Recycle-Bin API is not
        # usable from the calling context: hvigor aborts dependency bootstrap with
        # "[safe-delete] 操作失败: ... .npmrc.lock: Error during a `trash` operation".
        # It is not caused by missing packages. Run the build from DevEco Studio
        # (Build > Build Hap(s)) or from DevEco's own integrated terminal instead.
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
    # hdc expects a bare HAP filename. Passing an absolute Windows path through
    # a Unix-like shell can rewrite the path and make installation fail.
    Push-Location $hap.DirectoryName
    try {
        & $hdc -t $DeviceId install -r $hap.Name
        if ($LASTEXITCODE -ne 0) { throw "HAP installation failed." }
    }
    finally {
        Pop-Location
    }
    & $hdc -t $DeviceId shell aa start -a EntryAbility -b com.kuikly.stockchat
    if ($LASTEXITCODE -ne 0) { throw "App launch failed." }

    Write-Host "OpenHarmony app launched on $DeviceId"
}
finally {
    Pop-Location
}
