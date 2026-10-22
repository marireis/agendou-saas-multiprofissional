param(
    [string]$WebBaseUrl = 'http://localhost:3000',
    [string]$ApiBaseUrl = 'http://localhost:8080'
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$handler = New-Object System.Net.Http.HttpClientHandler
$handler.AllowAutoRedirect = $false
$handler.UseCookies = $false
$client = New-Object System.Net.Http.HttpClient($handler)
$client.Timeout = [TimeSpan]::FromSeconds(30)
function Check-Response([string]$Url, [int]$Status, [hashtable]$Expected) {
    $response = $client.GetAsync($Url).GetAwaiter().GetResult()
    try {
        if ([int]$response.StatusCode -ne $Status) { throw "Unexpected status for $Url : $($response.StatusCode)" }
        foreach ($name in $Expected.Keys) {
            if (-not $response.Headers.Contains($name)) { throw "Missing $name on $Url" }
            $value = [string]::Join(', ', $response.Headers.GetValues($name))
            if (-not $value.Contains($Expected[$name])) { throw "Unexpected $name on $Url" }
        }
        Write-Output "PASS $Url ($Status)"
    } finally { $response.Dispose() }
}
try {
    $pageHeaders = @{
        'Referrer-Policy' = 'no-referrer'
        'X-Frame-Options' = 'DENY'
        'X-Content-Type-Options' = 'nosniff'
        'Permissions-Policy' = 'camera=(), microphone=(), geolocation=()'
        'Content-Security-Policy' = "base-uri 'self'; object-src 'none'; frame-ancestors 'none'; form-action 'self'"
    }
    foreach ($path in @('/', '/entrar', '/painel/financeiro', '/painel/conferencia', '/plataforma')) {
        Check-Response ($WebBaseUrl.TrimEnd('/') + $path) 200 $pageHeaders
    }
    $privateHeaders = @{'Referrer-Policy'='no-referrer';'Cache-Control'='no-store';'X-Frame-Options'='DENY';'X-Content-Type-Options'='nosniff'}
    foreach ($base in @($ApiBaseUrl, $WebBaseUrl)) {
        Check-Response ($base.TrimEnd('/') + '/api/v1/admin/finance') 401 $privateHeaders
    }
} finally { $client.Dispose(); $handler.Dispose() }
