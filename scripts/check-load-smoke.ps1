param([string]$PostgresBin='C:\Program Files\PostgreSQL\18\bin')
$ErrorActionPreference='Stop'
if($env:RMS_TEST_ONLY -ne 'yes' -or $env:SPRING_DATASOURCE_URL -ne 'jdbc:postgresql://127.0.0.1:55432/rms_test'){
    throw 'Use the isolated test launcher'
}
if(Get-NetTCPConnection -State Listen -LocalPort 18081 -ErrorAction SilentlyContinue){throw 'Load smoke port is occupied'}
$argsDb=@('-h','127.0.0.1','-p','55432','-U','rms_test')
& (Join-Path $PostgresBin 'createdb.exe') @argsDb rms_load
if($LASTEXITCODE){throw 'An empty rms_load database is required'}
$folder=Join-Path $env:TEMP ('rms-load-smoke-'+[Guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($folder)
$names=@('SPRING_DATASOURCE_URL','SPRING_FLYWAY_URL','APP_PORT','SHOP_ORIGIN','LOAD_ORIGIN','LOAD_ADMINS_FILE','LOAD_OUTPUT','LOAD_MODE','LOAD_CLIENTS','LOAD_RPS','LOAD_WARMUP_SECONDS','LOAD_SECONDS','RMS_LOAD_ALLOW','RMS_PSQL')
$previous=@{}
foreach($name in $names){$previous[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
$app=$null
try {
    $env:SPRING_DATASOURCE_URL=$env:SPRING_FLYWAY_URL='jdbc:postgresql://127.0.0.1:55432/rms_load'
    $env:APP_PORT='18081'
    $env:SHOP_ORIGIN=$env:LOAD_ORIGIN='http://127.0.0.1:18081'
    $env:LOAD_ADMINS_FILE=Join-Path $folder 'accounts.load-credentials.json'
    $env:LOAD_OUTPUT=Join-Path $folder 'sample'
    $env:LOAD_MODE='open'
    $env:LOAD_CLIENTS='10'
    $env:LOAD_RPS='20'
    $env:LOAD_WARMUP_SECONDS='2'
    $env:LOAD_SECONDS='10'
    $env:RMS_LOAD_ALLOW='owned-test-environment'
    $java=Join-Path $env:JAVA_HOME 'bin\java.exe'
    & $java -jar target/Real_Mobil_Shop_Sayt-0.0.1-SNAPSHOT.jar bootstrap-owner *> (Join-Path $folder 'bootstrap.log')
    if($LASTEXITCODE){throw 'Load bootstrap failed'}
    & (Join-Path $PostgresBin 'psql.exe') @argsDb -d rms_load -X -v ON_ERROR_STOP=1 -f scripts/load/seed.sql *> (Join-Path $folder 'seed.log')
    if($LASTEXITCODE){throw 'Load fixture failed'}
    $app=Start-Process -FilePath $java -ArgumentList @('-jar','target/Real_Mobil_Shop_Sayt-0.0.1-SNAPSHOT.jar') -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $folder 'app.log') -RedirectStandardError (Join-Path $folder 'app-error.log')
    $ready=$false
    for($i=0;$i -lt 60;$i++){
        if($app.HasExited){throw 'Load application exited'}
        try{
            $r=Invoke-WebRequest -Uri "$env:LOAD_ORIGIN/api/v1/categories" -UseBasicParsing -TimeoutSec 2
            if($r.StatusCode -eq 200){$ready=$true;break}
        }catch{Start-Sleep -Seconds 1}
    }
    if(-not $ready){throw 'Load smoke startup timeout'}
    $env:RMS_PSQL=Join-Path $PostgresBin 'psql.exe'
    node.exe scripts/load/query-plans.mjs
    if($LASTEXITCODE){throw 'Query plan diagnostics failed'}
    node.exe scripts/load/prepare-accounts.mjs
    if($LASTEXITCODE){throw 'Load accounts failed'}
    node.exe scripts/load/run.mjs *> (Join-Path $folder 'generator.log')
    $loadExit=$LASTEXITCODE
    node.exe scripts/load/reconcile.mjs
    if($LASTEXITCODE){throw 'Receipt reconciliation failed'}
    $summary=Get-Content -LiteralPath ($env:LOAD_OUTPUT+'.json') -Raw | ConvertFrom-Json
    Write-Host "LOCAL SMOKE ONLY: requested 20 RPS for 10 s, 10 admins; requests=$($summary.requests), errors=$($summary.errors), dropped=$($summary.dropped)"
    Write-Host "Synthetic fixture 10000 models / 20000 variants / 40000 photo metadata / 100000 initial leads. Raw results: $folder"
    if($loadExit){throw 'Local load generator smoke failed; this is not a 1000/2000 SLO test'}
} finally {
    if($app -and -not $app.HasExited){Stop-Process -Id $app.Id;[void]$app.WaitForExit(5000)}
    # Remove only the secret credential file created by this script, not the reports.
    $credentialFile=Join-Path $folder 'accounts.load-credentials.json'
    if(Test-Path -LiteralPath $credentialFile){Remove-Item -LiteralPath $credentialFile}
    foreach($name in $names){[Environment]::SetEnvironmentVariable($name,$previous[$name],'Process')}
}
