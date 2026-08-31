$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..\..')).Path
$builder = Join-Path $repoRoot 'vcampus-distribution\scripts\build-shop-auth-demo-package.ps1'
$testRunId = [Guid]::NewGuid().ToString('N')
$testRoot = Join-Path $repoRoot "target\shop-auth-demo-package-test\$testRunId"
$outputRoot = Join-Path $testRoot 'output'
$extractRoot = Join-Path $testRoot 'extracted'
$fakeBin = Join-Path $testRoot 'fake-bin'
$originalPath = $env:PATH
$originalCapture = $env:SHOP_AUTH_DEMO_PACKAGE_CAPTURE
$originalNoPause = $env:VCAMPUS_SHOP_DEMO_NO_PAUSE

function Assert-True([bool] $condition, [string] $message) {
    if (-not $condition) {
        throw $message
    }
}

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
    Assert-True (Test-Path -LiteralPath $builder -PathType Leaf) `
        'The Shop Auth Demo package builder must exist.'

    $zipOutput = & $builder -SkipBuild -OutputRoot $outputRoot
    $zipPath = [string] ($zipOutput | Select-Object -Last 1)
    Assert-True (Test-Path -LiteralPath $zipPath -PathType Leaf) `
        'The package builder must return the generated ZIP path as its final output line.'

    Expand-Archive -LiteralPath $zipPath -DestinationPath $extractRoot
    $packageRoot = Join-Path $extractRoot 'vCampus-Shop-Demo'
    $requiredFiles = @(
        '启动服务端.bat',
        '启动客户端.bat',
        '使用说明.txt',
        'lib\vCampusServer.jar',
        'lib\vCampusClient.jar',
        'config\logback.xml',
        'database\vcampus-shop-auth-demo.accdb'
    )
    foreach ($relativePath in $requiredFiles) {
        Assert-True (Test-Path -LiteralPath (Join-Path $packageRoot $relativePath) -PathType Leaf) `
            "Portable package is missing $relativePath."
    }
    Assert-True (Test-Path -LiteralPath (Join-Path $packageRoot 'database\schema') -PathType Container) `
        'Portable package must contain database schemas.'
    Assert-True (Test-Path -LiteralPath (Join-Path $packageRoot 'database\seed') -PathType Container) `
        'Portable package must contain database seed files.'
    Assert-True (-not (Test-Path -LiteralPath (Join-Path $packageRoot 'logs'))) `
        'Runtime logs must not be included in the ZIP.'
    Assert-True (-not (Get-ChildItem -LiteralPath $packageRoot -Recurse -Directory | Where-Object Name -eq 'src')) `
        'Source directories must not be included in the ZIP.'
    Assert-True (-not (Get-ChildItem -LiteralPath $packageRoot -Recurse -File -Filter 'pom.xml')) `
        'Maven project files must not be included in the ZIP.'

    New-Item -ItemType Directory -Force -Path $fakeBin | Out-Null
    Set-Content -LiteralPath (Join-Path $fakeBin 'java.cmd') -Encoding Ascii -Value @(
        '@echo off'
        'if "%~1"=="-version" ('
        '  echo openjdk version "21.0.1" 1>&2'
        '  exit /b 0'
        ')'
        'echo %CD%>"%SHOP_AUTH_DEMO_PACKAGE_CAPTURE%.cwd"'
        'echo %*>"%SHOP_AUTH_DEMO_PACKAGE_CAPTURE%.args"'
        'exit /b 0'
    )
    $env:PATH = "$fakeBin;$originalPath"
    $env:VCAMPUS_SHOP_DEMO_NO_PAUSE = '1'

    $serverCapture = Join-Path $testRoot 'server'
    $env:SHOP_AUTH_DEMO_PACKAGE_CAPTURE = $serverCapture
    & "$env:ComSpec" /d /c (Join-Path $packageRoot '启动服务端.bat') | Out-Null
    Assert-Equal $packageRoot ((Get-Content -Raw -LiteralPath "$serverCapture.cwd").Trim()) `
        'The server BAT must run Java from the extracted package root.'
    $serverArgs = (Get-Content -Raw -LiteralPath "$serverCapture.args").Trim()
    Assert-Contains $serverArgs '-Dlogback.configurationFile=config\logback.xml' `
        'The server BAT must use the packaged Logback configuration.'
    Assert-Contains $serverArgs '-cp lib\vCampusServer.jar edu.seu.vcampus.server.shop.demo.ShopAuthDemoServerMain' `
        'The server BAT must launch the Shop Auth Demo server main class.'
    Assert-Contains $serverArgs 'database\vcampus-shop-auth-demo.accdb 19090 database\schema database\seed' `
        'The server BAT must pass the packaged database, port, schema, and seed paths.'

    $clientCapture = Join-Path $testRoot 'client'
    $env:SHOP_AUTH_DEMO_PACKAGE_CAPTURE = $clientCapture
    & "$env:ComSpec" /d /c (Join-Path $packageRoot '启动客户端.bat') | Out-Null
    Assert-Equal $packageRoot ((Get-Content -Raw -LiteralPath "$clientCapture.cwd").Trim()) `
        'The client BAT must run Java from the extracted package root.'
    $clientArgs = (Get-Content -Raw -LiteralPath "$clientCapture.args").Trim()
    Assert-Contains $clientArgs '-Dlogback.configurationFile=config\logback.xml' `
        'The client BAT must use the packaged Logback configuration.'
    Assert-Contains $clientArgs '-cp lib\vCampusClient.jar edu.seu.vcampus.client.shop.demo.ShopAuthDemoClientMain 127.0.0.1 19090' `
        'The client BAT must connect to the local Shop Auth Demo server by default.'

    Write-Host 'Shop Auth Demo portable package tests passed.'
} finally {
    $env:PATH = $originalPath
    $env:SHOP_AUTH_DEMO_PACKAGE_CAPTURE = $originalCapture
    $env:VCAMPUS_SHOP_DEMO_NO_PAUSE = $originalNoPause
}
