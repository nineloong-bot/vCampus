param(
    [string] $ServerHost = '127.0.0.1',
    [string] $ServerPort = '19090'
)

$ErrorActionPreference = 'Stop'

$worktreeRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$runtimeRoot = Join-Path $worktreeRoot 'target\shop-auth-demo'
$logbackConfig = Join-Path $worktreeRoot 'vcampus-distribution\config\logback.xml'
$clientJar = Join-Path $worktreeRoot 'vcampus-distribution\lib\vCampusClient.jar'
$previousLocation = Get-Location

try {
    Set-Location -LiteralPath $worktreeRoot
    & mvn -pl vcampus-client -am package
    if ($LASTEXITCODE -ne 0) {
        throw "Shop Demo 客户端构建失败，退出码：$LASTEXITCODE"
    }

    New-Item -ItemType Directory -Force -Path $runtimeRoot | Out-Null
    Set-Location -LiteralPath $runtimeRoot
    & java "-Dlogback.configurationFile=$logbackConfig" `
        -cp $clientJar `
        edu.seu.vcampus.client.shop.demo.ShopAuthDemoClientMain `
        $ServerHost `
        $ServerPort
    if ($LASTEXITCODE -ne 0) {
        throw "Shop Demo 客户端退出，退出码：$LASTEXITCODE"
    }
} finally {
    Set-Location -LiteralPath $previousLocation
}
