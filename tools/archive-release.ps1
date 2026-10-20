# Archives the current dist APKs into dist\releases\<version>\ and updates releases\manifest.csv
# so every shipped build is kept for the record.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File tools\archive-release.ps1 -Version 1.0.3
#   powershell -ExecutionPolicy Bypass -File tools\archive-release.ps1 -Version 1.0.3 -Note "fix timestamps"
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [string]$Note = ""
)
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Dist = Join-Path $Root "dist"
$Releases = Join-Path $Dist "releases"
$Target = Join-Path $Releases $Version

$apks = @(Get-ChildItem $Dist -Filter "EZip-$Version*.apk")
if ($apks.Count -eq 0) {
    Write-Host "No APK named EZip-$Version*.apk in $Dist" -ForegroundColor Red
    exit 1
}

New-Item -ItemType Directory -Force -Path $Target | Out-Null
$rows = @()
foreach ($apk in $apks) {
    Copy-Item $apk.FullName (Join-Path $Target $apk.Name) -Force
    $rows += [pscustomobject]@{
        version = $Version
        file    = $apk.Name
        size    = $apk.Length
        sha256  = (Get-FileHash $apk.FullName -Algorithm SHA256).Hash
        date    = (Get-Date).ToString("yyyy-MM-dd HH:mm")
        note    = $Note
    }
}

$manifest = Join-Path $Releases "manifest.csv"
$all = @()
if (Test-Path $manifest) {
    $all = @(Import-Csv $manifest | Where-Object { $_.version -ne $Version })
}
$all += $rows
$all | Sort-Object version, file | Export-Csv $manifest -NoTypeInformation -Encoding UTF8

Write-Host "Archived $($rows.Count) APK(s) to $Target" -ForegroundColor Green
$rows | ForEach-Object { Write-Host ("  {0}  {1:N2} MB  {2}" -f $_.file, ($_.size / 1MB), $_.sha256) }
