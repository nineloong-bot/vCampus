$ErrorActionPreference = 'Stop'

$worktreeRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$previousLocation = Get-Location

try {
    Set-Location -LiteralPath $worktreeRoot
    & mvn -pl vcampus-client -am package
    if ($LASTEXITCODE -ne 0) {
        throw "Shop Demo 客户端构建失败，退出码：$LASTEXITCODE"
    }

    & java '-Dlogback.configurationFile=vcampus-distribution/config/logback.xml' `
        -cp 'vcampus-distribution/lib/vCampusClient.jar' `
        edu.seu.vcampus.client.shop.demo.ShopAuthDemoClientMain
    if ($LASTEXITCODE -ne 0) {
        throw "Shop Demo 客户端退出，退出码：$LASTEXITCODE"
    }
} finally {
    Set-Location -LiteralPath $previousLocation
}
