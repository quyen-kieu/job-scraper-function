$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

# The azure-functions-maven-plugin stages the app under target\azure-functions\<functionAppName>\,
# where functionAppName defaults to a timestamped value. Discover the newest staging folder instead
# of hardcoding it, so `mvn clean` (or deleting target\) never breaks this script.
function Get-StagingRoot {
    $stagingParent = Join-Path $root 'target\azure-functions'
    if (-not (Test-Path $stagingParent)) { return $null }
    Get-ChildItem $stagingParent -Directory |
        Where-Object { Test-Path (Join-Path $_.FullName 'host.json') } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}

$scriptRoot = Get-StagingRoot
if (-not $scriptRoot) {
    Write-Host 'Azure Functions package not found. Building it first...'
    mvn -q -DskipTests package
    $scriptRoot = Get-StagingRoot
}

if (-not $scriptRoot) {
    throw "Generated Azure Functions staging folder was not found under 'target\azure-functions'."
}

# The plugin copies local.settings.json (real local secrets) into the staging folder. It is needed
# for `func start`, so it is left in place, but target\ is git-ignored and should be deleted
# (`mvn clean`) when not actively running locally - never zip/share the staging folder.

$port = if ($args.Count -gt 0) { $args[0] } else { '7071' }
Write-Host "Starting Azure Functions from '$scriptRoot' on port $port"
func start --script-root $scriptRoot --port $port

