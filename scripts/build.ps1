$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
Push-Location frontend
try {
    npm.cmd ci --no-audit --no-fund
    if ($LASTEXITCODE) { throw 'npm ci failed' }
    npm.cmd run build
    if ($LASTEXITCODE) { throw 'Frontend build failed' }
} finally {
    Pop-Location
}
.\mvnw.cmd -B -DskipTests package
if ($LASTEXITCODE) { throw 'Maven package failed' }
