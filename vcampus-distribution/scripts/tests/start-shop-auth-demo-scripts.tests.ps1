$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..\..')).Path
$runtimeRoot = Join-Path $repoRoot 'target\shop-auth-demo'
$testRunId = [Guid]::NewGuid().ToString('N')
$testRoot = Join-Path $repoRoot "target\shop-auth-demo-script-test\$testRunId"
$fakeBin = Join-Path $testRoot 'fake-bin'
$originalPath = $env:PATH
$originalCapture = $env:SHOP_AUTH_DEMO_SCRIPT_CAPTURE

function Assert-Equal([string] $expected, [string] $actual, [string] $message) {
    if ($actual -cne $expected) {
        throw "$message`nExpected: $expected`nActual:   $actual"
    }
}

function Assert-Contains([string] $actual, [string] $expected, [string] $message) {
    if (-not $actual.Contains($expected)) {
        throw "$message`nExpected fragment: $expected`nActual:            $actual"
    }
}

try {
    New-Item -ItemType Directory -Force -Path $fakeBin | Out-Null
    Set-Content -LiteralPath (Join-Path $fakeBin 'mvn.cmd') -Encoding Ascii -Value @(
        '@echo off'
        'exit /b 0'
    )
    Set-Content -LiteralPath (Join-Path $fakeBin 'java.cmd') -Encoding Ascii -Value @(
        '@echo off'
        'echo %CD%>"%SHOP_AUTH_DEMO_SCRIPT_CAPTURE%.cwd"'
        'echo %*>"%SHOP_AUTH_DEMO_SCRIPT_CAPTURE%.args"'
        'exit /b 0'
    )
    $env:PATH = "$fakeBin;$originalPath"

    $serverCapture = Join-Path $testRoot 'server'
    $env:SHOP_AUTH_DEMO_SCRIPT_CAPTURE = $serverCapture
    & (Join-Path $repoRoot 'vcampus-distribution\scripts\start-shop-auth-demo-server.ps1')
    $serverCwd = (Get-Content -Raw -LiteralPath "$serverCapture.cwd").Trim()
    $serverArgs = (Get-Content -Raw -LiteralPath "$serverCapture.args").Trim()

    Assert-Equal $runtimeRoot $serverCwd 'Server Java process must run in the ignored Shop Demo runtime directory.'
    Assert-Contains $serverArgs "-Dlogback.configurationFile=$(Join-Path $repoRoot 'vcampus-distribution\config\logback.xml')" `
        'Server must use an absolute Logback configuration path after changing directory.'
    Assert-Contains $serverArgs "-cp $(Join-Path $repoRoot 'vcampus-distribution\lib\vCampusServer.jar') edu.seu.vcampus.server.shop.demo.ShopAuthDemoServerMain" `
        'Server must launch the Shop Auth Demo server main class from its absolute shaded JAR path.'
    Assert-Contains $serverArgs "$(Join-Path $repoRoot 'vcampus-database\demo\vcampus-shop-auth-demo.accdb') 19090" `
        'Server must preserve the documented database path and port.'
    Assert-Contains $serverArgs "$(Join-Path $repoRoot 'vcampus-database\schema') $(Join-Path $repoRoot 'vcampus-database\seed')" `
        'Server must preserve the schema and seed paths after changing directory.'

    $clientCapture = Join-Path $testRoot 'client'
    $env:SHOP_AUTH_DEMO_SCRIPT_CAPTURE = $clientCapture
    & (Join-Path $repoRoot 'vcampus-distribution\scripts\start-shop-auth-demo-client.ps1')
    $clientCwd = (Get-Content -Raw -LiteralPath "$clientCapture.cwd").Trim()
    $clientArgs = (Get-Content -Raw -LiteralPath "$clientCapture.args").Trim()

    Assert-Equal $runtimeRoot $clientCwd 'Client Java process must run in the ignored Shop Demo runtime directory.'
    Assert-Contains $clientArgs "-Dlogback.configurationFile=$(Join-Path $repoRoot 'vcampus-distribution\config\logback.xml')" `
        'Client must use an absolute Logback configuration path after changing directory.'
    Assert-Contains $clientArgs "-cp $(Join-Path $repoRoot 'vcampus-distribution\lib\vCampusClient.jar') edu.seu.vcampus.client.shop.demo.ShopAuthDemoClientMain" `
        'Client must launch the Shop Auth Demo client main class from its absolute shaded JAR path.'
    Assert-Contains $clientArgs 'edu.seu.vcampus.client.shop.demo.ShopAuthDemoClientMain 127.0.0.1 19090' `
        'Client must pass the local Shop Demo server defaults to the Java main class.'

    $remoteClientCapture = Join-Path $testRoot 'remote-client'
    $env:SHOP_AUTH_DEMO_SCRIPT_CAPTURE = $remoteClientCapture
    & (Join-Path $repoRoot 'vcampus-distribution\scripts\start-shop-auth-demo-client.ps1') `
        -ServerHost '100.64.12.34' -ServerPort 23456
    $remoteClientArgs = (Get-Content -Raw -LiteralPath "$remoteClientCapture.args").Trim()

    Assert-Contains $remoteClientArgs 'edu.seu.vcampus.client.shop.demo.ShopAuthDemoClientMain 100.64.12.34 23456' `
        'Client must pass the selected remote Shop Demo server to the Java main class.'

    Write-Host 'Shop Auth Demo startup script tests passed.'
} finally {
    $env:PATH = $originalPath
    $env:SHOP_AUTH_DEMO_SCRIPT_CAPTURE = $originalCapture
}
