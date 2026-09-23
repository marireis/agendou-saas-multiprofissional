param([int]$Port = 8080)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$apiRoot = Join-Path $projectRoot 'apps/api'
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
    $localJdk = Join-Path $env:USERPROFILE '.jdks/corretto-21.0.5'
    if (-not (Test-Path (Join-Path $localJdk 'bin/java.exe'))) { throw 'Configure JAVA_HOME apontando para um JDK 21.' }
    $env:JAVA_HOME = $localJdk
}
if (-not $env:AGENDOU_MFA_ENCRYPTION_KEY) {
    # Local Windows only: DPAPI protects the key for this Windows user. Never print it.
    $keyDirectory = Join-Path $projectRoot '.local'
    $keyFile = Join-Path $keyDirectory 'mfa-key.dpapi'
    if (-not (Test-Path -LiteralPath $keyFile)) {
        New-Item -ItemType Directory -Force -Path $keyDirectory | Out-Null
        $bytes = New-Object byte[] 32
        $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
        $protected = ConvertTo-SecureString ([Convert]::ToBase64String($bytes)) -AsPlainText -Force
        ConvertFrom-SecureString $protected | Set-Content -LiteralPath $keyFile -Encoding UTF8
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
    $protected = (Get-Content -LiteralPath $keyFile -Raw).Trim() | ConvertTo-SecureString
    $env:AGENDOU_MFA_ENCRYPTION_KEY = [Net.NetworkCredential]::new('', $protected).Password
}
$env:AGENDOU_SECURE_COOKIE = 'false' # HTTP localhost only.
$env:PORT = $Port.ToString()
Push-Location $apiRoot
try { & .\mvnw.cmd spring-boot:run; exit $LASTEXITCODE } finally { Pop-Location }
