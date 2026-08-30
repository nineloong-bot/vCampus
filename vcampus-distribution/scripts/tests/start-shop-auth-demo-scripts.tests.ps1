$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..\..')).Path
$runtimeRoot = Join-Path $repoRoot 'target\shop-auth-demo'
$testRoot = Join-Path $repoRoot 'target\shop-auth-demo-script-test'
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

    Write-Host 'Shop Auth Demo startup script tests passed.'
} finally {
    $env:PATH = $originalPath
    $env:SHOP_AUTH_DEMO_SCRIPT_CAPTURE = $originalCapture
}
