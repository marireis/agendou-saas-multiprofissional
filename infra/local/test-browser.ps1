$ErrorActionPreference = 'Stop'
$apiDirectory = Join-Path $PSScriptRoot '../../apps/api'
if (-not $env:JAVA_HOME) {
    $localJdk = Join-Path $env:USERPROFILE '.jdks/corretto-21.0.5'
    if (Test-Path (Join-Path $localJdk 'bin/java.exe')) { $env:JAVA_HOME = $localJdk }
}
Push-Location $apiDirectory
try {
    & .\mvnw.cmd '-Dit.test=BrowserJourneyE2E' verify
    if ($LASTEXITCODE -ne 0) { throw 'Browser journey failed. Inspect target/browser-e2e.log and apps/web/playwright-report.' }
} finally { Pop-Location }
