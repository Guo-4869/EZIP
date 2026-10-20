<#
    EZip - build script (Windows, no Gradle required)
    ---------------------------------------------------------
    Builds a signed, installable APK from the sources in app/src/main.

    Pipeline:
      1. aapt2 compile      -> build/obj/res.zip
      2. aapt2 link         -> build/obj/base.apk (+ generated R.java)
      3. javac              -> build/classes
      4. d8                 -> build/dex/classes.dex
      5. package            -> classes.dex added into base.apk
      6. zipalign           -> 4-byte aligned
      7. apksigner          -> dist/EZip-<version>.apk
      8. apksigner verify   -> sanity check

    Usage:
      powershell -ExecutionPolicy Bypass -File build.ps1
      powershell -ExecutionPolicy Bypass -File build.ps1 -Jdk "C:\jdk-17" -Sdk "C:\Android\Sdk"

    Requirements: JDK 8+ (11/17/21 all fine) and Android SDK build-tools 34 + platform android-34.
    If you have no SDK, run:  node tools\fetch.js  (downloads only what is missing)
#>
[CmdletBinding()]
param(
    [string]$Jdk,
    [string]$Sdk,
    [string]$VersionName = "1.0.10",
    [int]$VersionCode = 11,
    [string]$OutSuffix = "",
    [int]$MinSdk = 26,
    [int]$TargetSdk = 34,
    [switch]$SkipVerify,
    [switch]$Compat
)

if ($Compat) {
    $TargetSdk = 29
    if (-not $OutSuffix) { $OutSuffix = "-compat" }
}

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$AppDir = Join-Path $Root "app\src\main"
$ResDir = Join-Path $AppDir "res"
$Manifest = Join-Path $AppDir "AndroidManifest.xml"
$SrcDir = Join-Path $AppDir "java"
$LibDir = Join-Path $Root "libs"
$BuildDir = Join-Path $Root "build"
$DistDir = Join-Path $Root "dist"
$KsDir = Join-Path $Root "keystore"

function Write-Step([string]$Text) {
    Write-Host ""
    Write-Host "==> $Text" -ForegroundColor Cyan
}

function Fail([string]$Text) {
    Write-Host ""
    Write-Host "BUILD FAILED: $Text" -ForegroundColor Red
    exit 1
}

function Invoke-Checked([string]$Exe, [string[]]$Arguments, [string]$What) {
    # native tools print progress/warnings on stderr; keep it quiet unless they fail
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & $Exe @Arguments 2>&1 | Out-String
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previous
    }
    if ($code -ne 0) {
        Write-Host $output
        Fail "$What (exit code $code)"
    }
}

# ---------------------------------------------------------------- toolchain
function Resolve-Jdk {
    param([string]$Hint)
    $cands = @()
    if ($Hint) { $cands += $Hint }
    if ($env:JAVA_HOME) { $cands += $env:JAVA_HOME }
    $cands += (Join-Path $env:USERPROFILE ".ezip-toolchain\jdk")
    $cands += (Join-Path $env:USERPROFILE ".ezip-toolchain\jdk-17")
    $cands += "D:\program_files\PyCharm 2024.3.1.1\jbr"
    $cands += (Join-Path $env:ProgramFiles "Android\Android Studio\jbr")
    $cands += (Join-Path $env:LOCALAPPDATA "Programs\Android Studio\jbr")
    foreach ($base in @((Join-Path $env:ProgramFiles "Java"), (Join-Path $env:ProgramFiles "Eclipse Adoptium"), (Join-Path $env:ProgramFiles "Microsoft"), (Join-Path $env:ProgramFiles "Zulu"))) {
        if (Test-Path $base) { $cands += (Get-ChildItem $base -Directory -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }) }
    }
    foreach ($c in $cands) {
        if ($c -and (Test-Path (Join-Path $c "bin\javac.exe"))) { return (Resolve-Path $c).Path }
    }
    return $null
}

function Resolve-Sdk {
    param([string]$Hint)
    $cands = @()
    if ($Hint) { $cands += $Hint }
    if ($env:ANDROID_HOME) { $cands += $env:ANDROID_HOME }
    if ($env:ANDROID_SDK_ROOT) { $cands += $env:ANDROID_SDK_ROOT }
    $cands += (Join-Path $env:USERPROFILE ".ezip-toolchain\android-sdk")
    $cands += (Join-Path $env:LOCALAPPDATA "Android\Sdk")
    foreach ($c in $cands) {
        if ($c -and (Test-Path (Join-Path $c "build-tools"))) { return (Resolve-Path $c).Path }
    }
    return $null
}

Write-Step "Locating toolchain"
$JdkHome = Resolve-Jdk $Jdk
if (-not $JdkHome) { Fail "No JDK found. Install one or pass -Jdk <dir>." }
$SdkHome = Resolve-Sdk $Sdk
if (-not $SdkHome) { Fail "No Android SDK found. Run 'node tools/fetch.js' or pass -Sdk <dir>." }

$env:JAVA_HOME = $JdkHome
$env:PATH = (Join-Path $JdkHome "bin") + ";" + $env:PATH

$BuildTools = Get-ChildItem (Join-Path $SdkHome "build-tools") -Directory -ErrorAction SilentlyContinue |
    Where-Object { Test-Path (Join-Path $_.FullName "aapt2.exe") } |
    Sort-Object { [int]($_.Name -split "\.")[0] } -Descending | Select-Object -First 1
if (-not $BuildTools) { Fail "No usable build-tools (need aapt2.exe) under $SdkHome\build-tools" }
$Bt = $BuildTools.FullName

$PlatformJar = Get-ChildItem (Join-Path $SdkHome "platforms") -Directory -ErrorAction SilentlyContinue |
    Where-Object { Test-Path (Join-Path $_.FullName "android.jar") } |
    Sort-Object Name -Descending | Select-Object -First 1
if (-not $PlatformJar) { Fail "No platform (android.jar) under $SdkHome\platforms" }
$AndroidJar = Join-Path $PlatformJar.FullName "android.jar"

Write-Host "    JDK        : $JdkHome"
Write-Host "    SDK        : $SdkHome"
Write-Host "    build-tools: $($BuildTools.Name)"
Write-Host "    platform   : $($PlatformJar.Name)"
$btMajor = [int](($BuildTools.Name -split "\.")[0])
if ($btMajor -lt 35) {
    Write-Host "    WARNING: build-tools $($BuildTools.Name) bundles an old d8 (8.2) that fails on junrar/commons-lang3." -ForegroundColor Yellow
    Write-Host "             Install build-tools 35+ first:  node tools\fetch.js" -ForegroundColor Yellow
}

$aapt2 = Join-Path $Bt "aapt2.exe"
$d8 = Join-Path $Bt "d8.bat"
$zipalign = Join-Path $Bt "zipalign.exe"
$apksigner = Join-Path $Bt "apksigner.bat"
$javac = Join-Path $JdkHome "bin\javac.exe"
$keytool = Join-Path $JdkHome "bin\keytool.exe"

foreach ($tool in @($aapt2, $d8, $zipalign, $apksigner, $javac)) {
    if (-not (Test-Path $tool)) { Fail "Missing tool: $tool" }
}

# ---------------------------------------------------------------- clean
Write-Step "Preparing output directories"
if (Test-Path $BuildDir) { Remove-Item -Recurse -Force $BuildDir }
$ObjDir = Join-Path $BuildDir "obj"
$GenDir = Join-Path $BuildDir "gen"
$ClassDir = Join-Path $BuildDir "classes"
$DexDir = Join-Path $BuildDir "dex"
New-Item -ItemType Directory -Force -Path $ObjDir, $GenDir, $ClassDir, $DexDir, $DistDir, $KsDir | Out-Null

# ---------------------------------------------------------------- 1. resources
Write-Step "Compiling resources (aapt2 compile)"
$ResZip = Join-Path $ObjDir "res.zip"
Invoke-Checked $aapt2 @("compile", "--dir", $ResDir, "-o", $ResZip) "aapt2 compile"

Write-Step "Linking resources + manifest (aapt2 link)"
$BaseApk = Join-Path $ObjDir "base.apk"
Invoke-Checked $aapt2 @(
    "link", "-o", $BaseApk,
    "-I", $AndroidJar,
    "--manifest", $Manifest,
    "-R", $ResZip,
    "--java", $GenDir,
    "--min-sdk-version", "$MinSdk",
    "--target-sdk-version", "$TargetSdk",
    "--version-code", "$VersionCode",
    "--version-name", $VersionName,
    "--auto-add-overlay"
) "aapt2 link"

# ---------------------------------------------------------------- 2. java
Write-Step "Compiling Java sources (javac)"
$Sources = @()
$Sources += (Get-ChildItem $SrcDir -Recurse -Filter "*.java" | ForEach-Object { $_.FullName })
$Sources += (Get-ChildItem $GenDir -Recurse -Filter "*.java" | ForEach-Object { $_.FullName })
if ($Sources.Count -eq 0) { Fail "No .java sources found under $SrcDir" }

$Jars = @(Get-ChildItem $LibDir -Filter "*.jar" | ForEach-Object { $_.FullName })
$JarPath = ($Jars -join ";")
$ClassPath = if ($JarPath) { "$AndroidJar;$JarPath" } else { $AndroidJar }

$ArgFile = Join-Path $ObjDir "javac.args"
function Fwd([string]$p) { return $p.Replace('\', '/') }
$argLines = @()
$argLines += "-encoding"
$argLines += "UTF-8"
$argLines += "-source"
$argLines += "8"
$argLines += "-target"
$argLines += "8"
$argLines += "-nowarn"
$argLines += "-bootclasspath"
$argLines += "`"$(Fwd $AndroidJar)`""
$argLines += "-classpath"
$argLines += "`"$(Fwd $ClassPath)`""
$argLines += "-d"
$argLines += "`"$(Fwd $ClassDir)`""
foreach ($s in $Sources) { $argLines += "`"$(Fwd $s)`"" }
[System.IO.File]::WriteAllLines($ArgFile, $argLines, (New-Object System.Text.UTF8Encoding($false)))

& $javac "-J-Duser.language=en" "@$ArgFile"
if ($LASTEXITCODE -ne 0) { Fail "javac failed (exit code $LASTEXITCODE)" }

# ---------------------------------------------------------------- 3. dex
Write-Step "Dexing (d8)"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$ClassesJar = Join-Path $ObjDir "classes.jar"
if (Test-Path $ClassesJar) { Remove-Item $ClassesJar -Force }
[System.IO.Compression.ZipFile]::CreateFromDirectory($ClassDir, $ClassesJar, [System.IO.Compression.CompressionLevel]::Optimal, $false)

$DexArgs = @("--release", "--min-api", "$MinSdk", "--lib", $AndroidJar, "--output", $DexDir, $ClassesJar)
$DexArgs += $Jars
& $d8 @DexArgs
if ($LASTEXITCODE -ne 0) { Fail "d8 failed (exit code $LASTEXITCODE)" }
$DexFile = Join-Path $DexDir "classes.dex"
if (-not (Test-Path $DexFile)) { Fail "d8 produced no classes.dex" }

# ---------------------------------------------------------------- 4. package
Write-Step "Packaging APK"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$WithDex = Join-Path $ObjDir "with-dex.apk"
Copy-Item $BaseApk $WithDex -Force
$zip = [System.IO.Compression.ZipFile]::Open($WithDex, [System.IO.Compression.ZipArchiveMode]::Update)
try {
    $existing = $zip.GetEntry("classes.dex")
    if ($existing) { $existing.Delete() }
    $entry = $zip.CreateEntry("classes.dex", [System.IO.Compression.CompressionLevel]::Optimal)
    $outStream = $entry.Open()
    $inStream = [System.IO.File]::OpenRead($DexFile)
    try { $inStream.CopyTo($outStream) } finally { $inStream.Dispose(); $outStream.Dispose() }
} finally {
    $zip.Dispose()
}

# ---------------------------------------------------------------- 5. align
Write-Step "Aligning (zipalign)"
$Aligned = Join-Path $ObjDir "aligned.apk"
Invoke-Checked $zipalign @("-p", "-f", "4", $WithDex, $Aligned) "zipalign"

# ---------------------------------------------------------------- 6. sign
Write-Step "Signing (apksigner)"
$Keystore = Join-Path $KsDir "ezip-release.jks"
$StorePass = "ezip2024"
$KeyPass = "ezip2024"
$Alias = "ezip"
if (-not (Test-Path $Keystore)) {
    Write-Host "    creating release keystore: $Keystore"
    Invoke-Checked $keytool @(
        "-genkeypair", "-keystore", $Keystore, "-alias", $Alias,
        "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
        "-storepass", $StorePass, "-keypass", $KeyPass,
        "-dname", "CN=EZip, OU=EZip, O=EZip, L=Beijing, C=CN"
    ) "keytool"
}

$FinalApk = Join-Path $DistDir "EZip-$VersionName$OutSuffix.apk"
Invoke-Checked $apksigner @(
    "sign",
    "--ks", $Keystore,
    "--ks-key-alias", $Alias,
    "--ks-pass", "pass:$StorePass",
    "--key-pass", "pass:$KeyPass",
    "--out", $FinalApk,
    $Aligned
) "apksigner sign"

if (-not $SkipVerify) {
    Write-Step "Verifying signature"
    Invoke-Checked $apksigner @("verify", "--verbose", $FinalApk) "apksigner verify"
}

# ---------------------------------------------------------------- summary
$size = [Math]::Round((Get-Item $FinalApk).Length / 1MB, 2)
$sha = (Get-FileHash $FinalApk -Algorithm SHA256).Hash
Write-Host ""
Write-Host "BUILD OK" -ForegroundColor Green
Write-Host "  APK   : $FinalApk"
Write-Host "  Ver   : $VersionName (code $VersionCode, targetSdk $TargetSdk)"
Write-Host "  Size  : $size MB"
Write-Host "  SHA256: $sha"
Write-Host ""
Write-Host "Install with:  adb install -r `"$FinalApk`""
exit 0
