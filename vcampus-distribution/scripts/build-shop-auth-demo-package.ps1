param(
    [string] $OutputRoot,
    [switch] $SkipBuild
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $repoRoot 'target\shop-auth-demo-release'
}

if (-not $SkipBuild) {
    $previousLocation = Get-Location
    try {
        Set-Location -LiteralPath $repoRoot
        & mvn '-DskipTests' package
        if ($LASTEXITCODE -ne 0) {
            throw "Shop Demo 打包构建失败，退出码：$LASTEXITCODE"
        }
    } finally {
        Set-Location -LiteralPath $previousLocation
    }
}

$requiredSources = @(
    (Join-Path $repoRoot 'vcampus-distribution\lib\vCampusServer.jar'),
    (Join-Path $repoRoot 'vcampus-distribution\lib\vCampusClient.jar'),
    (Join-Path $repoRoot 'vcampus-distribution\config\logback.xml'),
    (Join-Path $repoRoot 'vcampus-database\demo\vcampus-shop-auth-demo.accdb'),
    (Join-Path $repoRoot 'vcampus-database\schema'),
    (Join-Path $repoRoot 'vcampus-database\seed'),
    (Join-Path $repoRoot 'vcampus-distribution\templates\shop-auth-demo\启动服务端.bat'),
    (Join-Path $repoRoot 'vcampus-distribution\templates\shop-auth-demo\启动客户端.bat'),
    (Join-Path $repoRoot 'vcampus-distribution\templates\shop-auth-demo\使用说明.txt')
)
foreach ($source in $requiredSources) {
    if (-not (Test-Path -LiteralPath $source)) {
        throw "Shop Demo 打包缺少必需文件：$source"
    }
}

$releaseId = '{0}-{1}' -f (Get-Date -Format 'yyyyMMdd-HHmmss'), ([Guid]::NewGuid().ToString('N').Substring(0, 8))
$releaseRoot = Join-Path $OutputRoot $releaseId
$packageRoot = Join-Path $releaseRoot 'vCampus-Shop-Demo'
$libRoot = Join-Path $packageRoot 'lib'
$configRoot = Join-Path $packageRoot 'config'
$databaseRoot = Join-Path $packageRoot 'database'

New-Item -ItemType Directory -Path $libRoot, $configRoot, $databaseRoot | Out-Null

Copy-Item -LiteralPath (Join-Path $repoRoot 'vcampus-distribution\lib\vCampusServer.jar') -Destination $libRoot
Copy-Item -LiteralPath (Join-Path $repoRoot 'vcampus-distribution\lib\vCampusClient.jar') -Destination $libRoot
Copy-Item -LiteralPath (Join-Path $repoRoot 'vcampus-distribution\config\logback.xml') -Destination $configRoot
Copy-Item -LiteralPath (Join-Path $repoRoot 'vcampus-database\demo\vcampus-shop-auth-demo.accdb') -Destination $databaseRoot
Copy-Item -LiteralPath (Join-Path $repoRoot 'vcampus-database\schema') -Destination $databaseRoot -Recurse
Copy-Item -LiteralPath (Join-Path $repoRoot 'vcampus-database\seed') -Destination $databaseRoot -Recurse
Copy-Item -LiteralPath (Join-Path $repoRoot 'vcampus-distribution\templates\shop-auth-demo\启动服务端.bat') -Destination $packageRoot
Copy-Item -LiteralPath (Join-Path $repoRoot 'vcampus-distribution\templates\shop-auth-demo\启动客户端.bat') -Destination $packageRoot
Copy-Item -LiteralPath (Join-Path $repoRoot 'vcampus-distribution\templates\shop-auth-demo\使用说明.txt') -Destination $packageRoot

$zipPath = Join-Path $releaseRoot 'vCampus-Shop-Demo.zip'
Compress-Archive -LiteralPath $packageRoot -DestinationPath $zipPath -CompressionLevel Optimal

Write-Host "Shop Demo 便携包已生成：$zipPath"
Write-Output $zipPath
