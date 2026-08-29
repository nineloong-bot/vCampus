$ErrorActionPreference = 'Stop'

$worktreeRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$previousLocation = Get-Location

try {
    Set-Location -LiteralPath $worktreeRoot
    & mvn -pl vcampus-server -am package
    if ($LASTEXITCODE -ne 0) {
        throw "Shop Demo 服务端构建失败，退出码：$LASTEXITCODE"
    }

    & java '-Dlogback.configurationFile=vcampus-distribution/config/logback.xml' `
        -cp 'vcampus-distribution/lib/vCampusServer.jar' `
        edu.seu.vcampus.server.shop.demo.ShopAuthDemoServerMain
    if ($LASTEXITCODE -ne 0) {
        throw "Shop Demo 服务端退出，退出码：$LASTEXITCODE"
    }
} finally {
    Set-Location -LiteralPath $previousLocation
}
