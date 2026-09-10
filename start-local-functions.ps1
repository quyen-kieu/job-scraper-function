$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$artifactName = 'job-scraper-function-20260815154131779'
$scriptRoot = Join-Path $root "target\azure-functions\$artifactName"

if (-not (Test-Path $scriptRoot)) {
    Write-Host 'Azure Functions package not found. Building it first...'
    mvn -q -DskipTests package
}

if (-not (Test-Path $scriptRoot)) {
    throw "Generated Azure Functions metadata was not found at '$scriptRoot'."
}

$port = if ($args.Count -gt 0) { $args[0] } else { '7071' }
Write-Host "Starting Azure Functions from '$scriptRoot' on port $port"
func start --script-root $scriptRoot --port $port

