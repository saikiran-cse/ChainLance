# Loads backend/.env into THIS PowerShell session only, then starts Spring Boot.
$ErrorActionPreference = "Stop"
$envFile = Join-Path $PSScriptRoot ".env"

if (-not (Test-Path $envFile)) {
    Write-Host "Missing $envFile" -ForegroundColor Red
    Write-Host "Copy .env.example to .env and fill in the values first." -ForegroundColor Yellow
    exit 1
}

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    $idx = $line.IndexOf("=")
    if ($idx -lt 1) { return }
    $name  = $line.Substring(0, $idx).Trim()
    $value = $line.Substring($idx + 1).Trim().Trim('"').Trim("'")
    [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
}

if (-not $env:DB_PASSWORD -or $env:DB_PASSWORD -eq "change-me") {
    Write-Host "DB_PASSWORD is empty or still 'change-me' in .env" -ForegroundColor Red
    exit 1
}

Write-Host "Environment loaded. Starting backend..." -ForegroundColor Green
mvn spring-boot:run
