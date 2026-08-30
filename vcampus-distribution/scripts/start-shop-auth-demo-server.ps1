$ErrorActionPreference = 'Stop'

$worktreeRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$runtimeRoot = Join-Path $worktreeRoot 'target\shop-auth-demo'
$logbackConfig = Join-Path $worktreeRoot 'vcampus-distribution\config\logback.xml'
$serverJar = Join-Path $worktreeRoot 'vcampus-distribution\lib\vCampusServer.jar'
$database = Join-Path $worktreeRoot 'vcampus-database\demo\vcampus-shop-auth-demo.accdb'
$schemas = Join-Path $worktreeRoot 'vcampus-database\schema'
$seeds = Join-Path $worktreeRoot 'vcampus-database\seed'
$previousLocation = Get-Location

try {
    Set-Location -LiteralPath $worktreeRoot
    & mvn -pl vcampus-server -am package
    if ($LASTEXITCODE -ne 0) {
        throw "Shop Demo 服务端构建失败，退出码：$LASTEXITCODE"
    }

    New-Item -ItemType Directory -Force -Path $runtimeRoot | Out-Null
    Set-Location -LiteralPath $runtimeRoot
    & java "-Dlogback.configurationFile=$logbackConfig" `
        -cp $serverJar `
        edu.seu.vcampus.server.shop.demo.ShopAuthDemoServerMain `
        $database 19090 $schemas $seeds
    if ($LASTEXITCODE -ne 0) {
        throw "Shop Demo 服务端退出，退出码：$LASTEXITCODE"
    }
} finally {
    Set-Location -LiteralPath $previousLocation
}
