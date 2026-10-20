# Guards against a class of runtime crash that the compiler cannot see:
#  1. new ArrayAdapter(Context, R.layout.X, ...) requires layout X's root to be a TextView
#     (the adapter casts the whole row to TextView -> ClassCastException with a custom root).
#  2. (SomeType) ...inflate(R.layout.X ...) requires X's root tag to be that type.
# Both only fail at runtime, on a real device, when the view is first rendered.
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$SrcDir = Join-Path $Root "app\src\main\java"
$ResDir = Join-Path $Root "app\src\main\res"

function Get-LayoutRoot([string]$name) {
    foreach ($dir in @("layout", "layout-land", "layout-v26", "layout-night")) {
        $path = Join-Path (Join-Path $ResDir $dir) "$name.xml"
        if (Test-Path $path) {
            $text = Get-Content $path -Raw
            $m = [regex]::Match($text, '<([A-Za-z0-9_.]+)[\s>/]')
            if ($m.Success) { return $m.Groups[1].Value }
        }
    }
    return $null
}

$problems = @()
$adapterUses = 0
$castUses = 0

Get-ChildItem $SrcDir -Recurse -Filter "*.java" | ForEach-Object {
    $file = $_.Name
    $text = Get-Content $_.FullName -Raw

    # 1) ArrayAdapter with a custom layout
    foreach ($m in [regex]::Matches($text, 'new\s+(?:[A-Za-z0-9_.]+\.)?ArrayAdapter[\s\S]{0,400}?R\.layout\.([A-Za-z0-9_]+)')) {
        $layout = $m.Groups[1].Value
        $adapterUses++
        $root = Get-LayoutRoot $layout
        if ($root -ne "TextView") {
            $problems += "$file : ArrayAdapter uses R.layout.$layout whose root is <$root>, must be <TextView>"
        }
    }

    # 2) casting an inflated layout to a type that does not match its root element
    foreach ($m in [regex]::Matches($text, '\(\s*([A-Z][A-Za-z0-9_.]*)\s*\)\s*[^;{}]{0,200}?inflate\(\s*R\.layout\.([A-Za-z0-9_]+)')) {
        $castTo = $m.Groups[1].Value.Split('.')[-1]
        $layout = $m.Groups[2].Value
        $castUses++
        $root = Get-LayoutRoot $layout
        if ($null -eq $root) { continue }
        if ($root -ne $castTo) {
            $problems += "$file : casts inflated R.layout.$layout to $castTo but its root is <$root>"
        }
    }
}

Write-Host "ArrayAdapter layouts checked: $adapterUses"
Write-Host "Inflate-cast pairs checked: $castUses"
if ($problems.Count -eq 0) {
    Write-Host "ADAPTER CHECK OK" -ForegroundColor Green
    exit 0
}
Write-Host "ADAPTER CHECK FOUND $($problems.Count) PROBLEMS:" -ForegroundColor Red
$problems | ForEach-Object { Write-Host "  - $_" }
exit 1
