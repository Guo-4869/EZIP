# Compiles the Android-free archive engine + the desktop harness and runs it.
# Usage: powershell -ExecutionPolicy Bypass -File tools\run-engine-test.ps1
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

function Resolve-Jdk {
    $cands = @()
    if ($env:JAVA_HOME) { $cands += $env:JAVA_HOME }
    $cands += (Join-Path $env:USERPROFILE ".ezip-toolchain\jdk")
    $cands += "D:\program_files\PyCharm 2024.3.1.1\jbr"
    $cands += (Join-Path $env:ProgramFiles "Android\Android Studio\jbr")
    $cands += (Join-Path $env:ProgramFiles "Java")
    foreach ($c in $cands) {
        if ($c -and (Test-Path (Join-Path $c "bin\javac.exe"))) { return (Resolve-Path $c).Path }
    }
    return $null
}

$JdkHome = Resolve-Jdk
if (-not $JdkHome) { Write-Host "No JDK found" -ForegroundColor Red; exit 1 }
$javac = Join-Path $JdkHome "bin\javac.exe"
$java = Join-Path $JdkHome "bin\java.exe"

$Out = Join-Path $Root "build\enginetest-classes"
if (Test-Path $Out) { Remove-Item -Recurse -Force $Out }
New-Item -ItemType Directory -Force -Path $Out | Out-Null

$LibDir = Join-Path $Root "libs"
$Jars = @(Get-ChildItem $LibDir -Filter "*.jar" | ForEach-Object { $_.FullName })
$cp = ($Jars -join ";")

$Sources = @()
# only the Android-free engine packages are needed here
foreach ($pkg in @("archive", "util")) {
    $Sources += (Get-ChildItem (Join-Path $Root "app\src\main\java\com\ezip\app\$pkg") -Recurse -Filter "*.java" |
        ForEach-Object { $_.FullName })
}
$Sources += (Join-Path $Root "tools\EngineTest.java")

Write-Host "Compiling $($Sources.Count) source files..."
& $javac -encoding UTF-8 -nowarn -d $Out -classpath $cp @Sources
if ($LASTEXITCODE -ne 0) { Write-Host "javac failed" -ForegroundColor Red; exit 1 }

Push-Location $Root
try {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $ErrorActionPreference = "Continue"
    & $java "-Dfile.encoding=UTF-8" -classpath "$Out;$cp" EngineTest (Join-Path $Root "build\enginetest")
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}
exit $code
