param([string]$PostgresBin = 'C:\Program Files\PostgreSQL\18\bin', [string]$BackupFile, [switch]$ReuseBuild, [switch]$LoadSmoke)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
if (-not (Test-Path -LiteralPath (Join-Path $PostgresBin 'initdb.exe'))) { throw 'Specify -PostgresBin' }
foreach ($port in 55432,18080) {
    if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) {
        throw "Test port $port is occupied"
    }
}
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('rms-test-' + [Guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($testRoot)
$cluster = Join-Path $testRoot 'pg'
$app = $null
$databaseStarted = $false
try {
    & (Join-Path $PostgresBin 'initdb.exe') -D $cluster -U rms_test --auth=trust --encoding=UTF8 --locale=C
    if ($LASTEXITCODE) { throw 'initdb failed' }
    & (Join-Path $PostgresBin 'pg_ctl.exe') -D $cluster -l (Join-Path $testRoot 'postgres.log') -o '-p 55432 -h 127.0.0.1' -w start
    if ($LASTEXITCODE) { throw 'pg_ctl failed' }
    $databaseStarted = $true
    & (Join-Path $PostgresBin 'createdb.exe') -h 127.0.0.1 -p 55432 -U rms_test rms_test
    if ($LASTEXITCODE) { throw 'createdb failed' }

    $env:RMS_TEST_ONLY = 'yes'
    $env:DB_URL = 'jdbc:postgresql://127.0.0.1:55432/rms_test'
    $env:DB_USER = $env:DB_MIGRATION_USER = 'rms_test'
    $env:DB_PASSWORD = $env:DB_MIGRATION_PASSWORD = 'rms-test-placeholder'
    $env:SPRING_PROFILES_ACTIVE = 'test'
    $env:SPRING_APPLICATION_JSON = '{}'
    $env:SPRING_DATASOURCE_URL = $env:DB_URL
    $env:SPRING_DATASOURCE_USERNAME = $env:DB_USER
    $env:SPRING_DATASOURCE_PASSWORD = $env:DB_PASSWORD
    $env:SPRING_FLYWAY_URL = $env:DB_URL
    $env:SPRING_FLYWAY_USER = $env:DB_USER
    $env:SPRING_FLYWAY_PASSWORD = $env:DB_PASSWORD
    $env:SPRING_FLYWAY_ENABLED = 'true'
    $env:OWNER_LOGIN = 'local-owner'
    $env:OWNER_NAME = 'Local QA Owner'
    $env:OWNER_PASSWORD = [Guid]::NewGuid().ToString('N')
    $env:TELEGRAM_BOT_TOKEN = $env:TELEGRAM_CHAT_ID = ''
    $env:APP_BIND = '127.0.0.1'
    $env:APP_PORT = '18080'
    $env:SHOP_ORIGIN = 'http://127.0.0.1:18080'
    $env:APP_SECURE_COOKIE = 'false'
    $env:MEDIA_DIR = Join-Path $testRoot 'media'
    if (-not $ReuseBuild) {
        & (Join-Path $PSScriptRoot 'build.ps1')
        .\mvnw.cmd -B test
        if ($LASTEXITCODE) { throw 'Maven tests failed' }
    } else { Write-Host 'Reusing verified JAR: build and JUnit are NOT RUN in this invocation' }
    $java = Join-Path $env:JAVA_HOME 'bin\java.exe'
    $jar = 'target/Real_Mobil_Shop_Sayt-0.0.1-SNAPSHOT.jar'
    if ($BackupFile) { & (Join-Path $PSScriptRoot 'check-backup.ps1') -BackupFile $BackupFile -PostgresBin $PostgresBin }
    $inspectionLog = Join-Path $testRoot 'db-inspect.log'
    $probe = Start-Process -FilePath $java -ArgumentList @('-jar',$jar,'inspect-database') -PassThru -Wait -WindowStyle Hidden `
        -RedirectStandardOutput $inspectionLog -RedirectStandardError (Join-Path $testRoot 'db-inspect-error.log')
    $inspection = Get-Content -LiteralPath $inspectionLog -Raw
    if ($probe.ExitCode -ne 1 -or $inspection -notmatch 'database=rms_test schema=public user=rms_test host=127.0.0.1 port=55432 readOnly=true' `
        -or $inspection -notmatch 'AT-26: DataSource does not match the owner local PostgreSQL') {
        throw 'Expected the JPA inspection to report the isolated database and reject it as the owner database'
    }
    Write-Host 'PASS: JPA read-only inspection identifies and rejects the isolated database as the owner database'
    & $java -jar $jar bootstrap-owner
    if ($LASTEXITCODE) { throw 'Bootstrap failed' }
    $app = Start-Process -FilePath $java -ArgumentList @('-jar',$jar) -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $testRoot 'app.log') -RedirectStandardError (Join-Path $testRoot 'app-error.log')
    $ready = $false
    for ($i=0; $i -lt 50; $i++) {
        if ($app.HasExited) { throw 'Application exited during startup' }
        try {
            $response = Invoke-WebRequest -Uri "$env:SHOP_ORIGIN/api/v1/categories" -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -eq 200) { $ready = $true; break }
        } catch { Start-Sleep -Seconds 1 }
    }
    if (-not $ready) { throw 'Application readiness timeout' }
    node.exe scripts/check-api.mjs
    if ($LASTEXITCODE) { throw 'HTTP checks failed' }
    if ($LoadSmoke) { & (Join-Path $PSScriptRoot 'check-load-smoke.ps1') -PostgresBin $PostgresBin }
    if ($env:RMS_PLAYWRIGHT_DIR -and $env:RMS_BROWSER_EXE) {
        node.exe scripts/check-browser.mjs
        if ($LASTEXITCODE) { throw 'Browser checks failed' }
    }
} finally {
    if ($app -and -not $app.HasExited) {
        Stop-Process -Id $app.Id
        [void]$app.WaitForExit(5000)
    }
    if ($databaseStarted) {
        & (Join-Path $PostgresBin 'pg_ctl.exe') -D $cluster -m fast -w stop
    }
    Write-Host "Test logs: $testRoot"
}
