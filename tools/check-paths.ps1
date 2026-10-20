# Validates android:pathData strings in res/drawable vectors the same way aapt2's runtime
# PathParser does (command letters, argument counts, no stray characters).
# An invalid path throws InflateException at runtime, so this check matters.
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$DrawableDir = Join-Path $Root "app\src\main\res\drawable"

$paramCount = @{
    'M' = 2; 'L' = 2; 'T' = 2; 'H' = 1; 'V' = 1
    'C' = 6; 'S' = 4; 'Q' = 4; 'A' = 7; 'Z' = 0
}

$problems = @()
$checked = 0

Get-ChildItem $DrawableDir -Filter "*.xml" | ForEach-Object {
    $file = $_.Name
    $text = Get-Content $_.FullName -Raw
    foreach ($match in [regex]::Matches($text, 'android:pathData="([^"]*)"')) {
        $checked++
        $data = $match.Groups[1].Value
        $compact = $data -replace '\s', '' -replace ',', ''
        $tokens = [regex]::Matches($data, '[MmLlHhVvCcSsQqTtAaZz]|-?\d*\.?\d+')
        $joined = -join ($tokens | ForEach-Object { $_.Value })
        if ($joined -ne $compact) {
            $problems += "$file : unexpected characters in pathData: $data"
            continue
        }
        if ($tokens.Count -eq 0 -or $tokens[0].Value -notmatch '^[Mm]$') {
            $problems += "$file : pathData must start with M/m: $data"
            continue
        }
        $i = 0
        while ($i -lt $tokens.Count) {
            $cmd = $tokens[$i].Value
            if ($cmd -notmatch '^[MmLlHhVvCcSsQqTtAaZz]$') {
                $problems += "$file : expected a command at token $i but found '$cmd'"
                break
            }
            $i++
            $upper = $cmd.ToUpper()
            $need = $paramCount[$upper]
            if ($need -eq 0) { continue }
            $argCount = 0
            while ($i -lt $tokens.Count -and $tokens[$i].Value -notmatch '^[MmLlHhVvCcSsQqTtAaZz]$') {
                $argCount++
                $i++
            }
            if ($argCount -eq 0 -or ($argCount % $need) -ne 0) {
                $problems += "$file : command '$cmd' expects a multiple of $need numbers, got $argCount ($data)"
                break
            }
        }
    }
}

Write-Host "Vector pathData strings checked: $checked"
if ($problems.Count -eq 0) {
    Write-Host "PATH CHECK OK" -ForegroundColor Green
    exit 0
}
Write-Host "PATH CHECK FOUND $($problems.Count) PROBLEMS:" -ForegroundColor Red
$problems | ForEach-Object { Write-Host "  - $_" }
exit 1
