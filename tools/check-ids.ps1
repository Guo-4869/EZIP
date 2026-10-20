# Static consistency check: every R.id used in a class must exist in a layout that class inflates.
# (The compiler validates R fields, but findViewById() on the wrong layout would only fail at runtime.)
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$ResDir = Join-Path $Root "app\src\main\res"
$SrcDir = Join-Path $Root "app\src\main\java"

$layoutIds = @{}
Get-ChildItem (Join-Path $ResDir "layout") -Filter "*.xml" | ForEach-Object {
    $name = $_.BaseName
    $ids = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($m in [regex]::Matches((Get-Content $_.FullName -Raw), '@\+id/([A-Za-z0-9_]+)')) {
        [void]$ids.Add($m.Groups[1].Value)
    }
    $layoutIds[$name] = $ids
}

$problems = @()
Get-ChildItem $SrcDir -Recurse -Filter "*.java" | ForEach-Object {
    $text = Get-Content $_.FullName -Raw
    $usedLayouts = @([regex]::Matches($text, 'R\.layout\.([A-Za-z0-9_]+)') | ForEach-Object { $_.Groups[1].Value })
    $usedIds = @([regex]::Matches($text, 'R\.id\.([A-Za-z0-9_]+)') | ForEach-Object { $_.Groups[1].Value }) | Sort-Object -Unique
    if ($usedIds.Count -eq 0) { return }
    if ($usedLayouts.Count -eq 0) {
        $problems += "$($_.Name): uses R.id but inflates no layout"
        return
    }
    foreach ($id in $usedIds) {
        $found = $false
        foreach ($layout in $usedLayouts) {
            if ($layoutIds.ContainsKey($layout) -and $layoutIds[$layout].Contains($id)) { $found = $true; break }
        }
        if (-not $found) {
            $problems += "$($_.Name): R.id.$id not found in layouts [$($usedLayouts -join ', ')]"
        }
    }
}

Write-Host "Layouts checked: $($layoutIds.Count)"
if ($problems.Count -eq 0) {
    Write-Host "ID CHECK OK" -ForegroundColor Green
    exit 0
}
Write-Host "ID CHECK FOUND $($problems.Count) PROBLEMS:" -ForegroundColor Red
$problems | ForEach-Object { Write-Host "  - $_" }
exit 1
