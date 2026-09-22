#
# Bootstrap usado pelo mvnw.cmd no Windows: le a distributionUrl de
# maven-wrapper.properties, baixa e extrai o Maven em ~/.m2/wrapper/dists
# (se ainda nao estiver la) e repassa todos os argumentos para o mvn.cmd
# extraido.
#
# Este wrapper foi escrito a mao (nao gerado pelo plugin oficial
# maven-wrapper-plugin) porque o ambiente usado para criar esta mudanca nao
# tinha acesso de rede ao Maven Central para rodar o plugin. Funcionalmente
# equivalente ao wrapper oficial para o caso de uso deste projeto. Quando
# houver Maven + rede disponiveis, rodar
# `mvn -N org.apache.maven.plugins:maven-wrapper-plugin:3.3.2:wrapper -Dmaven=3.9.9`
# substitui estes arquivos pelos gerados oficialmente, se preferir.
#
$ErrorActionPreference = "Stop"

$propsPath = Join-Path $PSScriptRoot "maven-wrapper.properties"
if (-not (Test-Path $propsPath)) {
    Write-Error "Arquivo $propsPath nao encontrado."
    exit 1
}

$distributionUrlLine = Get-Content $propsPath | Where-Object { $_ -match '^distributionUrl=' } | Select-Object -First 1
if (-not $distributionUrlLine) {
    Write-Error "distributionUrl nao definido em $propsPath."
    exit 1
}
$distributionUrl = ($distributionUrlLine -split '=', 2)[1].Trim()

$distFile = Split-Path $distributionUrl -Leaf
if ($distFile -match 'apache-maven-([0-9.]+)-bin\.zip') {
    $distVersion = $Matches[1]
} else {
    Write-Error "Nao foi possivel identificar a versao do Maven a partir de '$distFile'."
    exit 1
}

$cacheDir = Join-Path $env:USERPROFILE ".m2\wrapper\dists\apache-maven-$distVersion"
$mavenHome = Join-Path $cacheDir "apache-maven-$distVersion"
$mvnCmd = Join-Path $mavenHome "bin\mvn.cmd"

if (-not (Test-Path $mvnCmd)) {
    New-Item -ItemType Directory -Force -Path $cacheDir | Out-Null
    Write-Host "Baixando Apache Maven $distVersion..."
    $downloadPath = Join-Path $cacheDir $distFile
    Invoke-WebRequest -Uri $distributionUrl -OutFile $downloadPath
    Expand-Archive -Path $downloadPath -DestinationPath $cacheDir -Force
    Remove-Item $downloadPath

    if (-not (Test-Path $mvnCmd)) {
        Write-Error "Extracao concluida, mas $mvnCmd nao foi encontrado. Verifique a distributionUrl em $propsPath."
        exit 1
    }
}

& $mvnCmd @args
exit $LASTEXITCODE
