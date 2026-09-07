param([string]$OutputDirectory)

# 从仓库 SQL 快照创建独立测试包，保留原发行库。
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
if (!$OutputDirectory) {
    $OutputDirectory = Join-Path $repoRoot 'artifacts\vCampus-full-test-data-20260907'
    if (Test-Path -LiteralPath $OutputDirectory) {
        $OutputDirectory += '-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
    }
}
$packageRoot = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $packageRoot) { throw "Output already exists: $packageRoot" }
foreach ($file in @('vCampusClient.jar', 'vCampusServer.jar')) {
    if (!(Test-Path -LiteralPath (Join-Path $repoRoot "vcampus-distribution\lib\$file"))) {
        throw "Missing $file. Run Maven package first."
    }
}
Get-Command java -ErrorAction Stop | Out-Null
foreach ($folder in @('lib', 'config', 'data', 'database', 'tools', 'logs')) {
    New-Item -ItemType Directory -Path (Join-Path $packageRoot $folder) -Force | Out-Null
}
foreach ($folder in @('schema', 'seed')) {
    Copy-Item -LiteralPath (Join-Path $repoRoot "vcampus-database\$folder") `
        -Destination (Join-Path $packageRoot "database\$folder") -Recurse
}
# 启动时仅初始化空流水号，保持后续新增学生的编号连续。
$seedFile = Join-Path $packageRoot 'database\seed\020_test_accounts.sql'
$text = [IO.File]::ReadAllText($seedFile).Replace(
    "WHERE sequenceKey = 'CAMPUS_CARD_GLOBAL';",
    "WHERE sequenceKey = 'CAMPUS_CARD_GLOBAL' AND currentValue < 1;")
[IO.File]::WriteAllText($seedFile, $text, [Text.UTF8Encoding]::new($false))
foreach ($file in @('vCampusClient.jar', 'vCampusServer.jar')) {
    Copy-Item -LiteralPath (Join-Path $repoRoot "vcampus-distribution\lib\$file") `
        -Destination (Join-Path $packageRoot "lib\$file")
}
Get-ChildItem -LiteralPath (Join-Path $repoRoot 'vcampus-distribution\config') -File | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $packageRoot 'config')
}
foreach ($file in @('server-with-data.properties', 'client.properties')) {
    $path = Join-Path $packageRoot "config\$file"
    $text = [IO.File]::ReadAllText($path).Replace('server.port=8888', 'server.port=18888')
    [IO.File]::WriteAllText($path, $text, [Text.UTF8Encoding]::new($false))
}
foreach ($role in @('server', 'client')) {
    $jarName = if ($role -eq 'server') { 'vCampusServer' } else { 'vCampusClient' }
    $configName = if ($role -eq 'server') { 'server-with-data' } else { 'client' }
    $lines = @('@echo off', 'cd /d "%~dp0"',
        "java -Dfile.encoding=UTF-8 -Dlogback.configurationFile=config\logback.xml -jar lib\$jarName.jar config\$configName.properties",
        'if errorlevel 1 pause')
    [IO.File]::WriteAllText((Join-Path $packageRoot "start-$role.bat"),
        ($lines -join "`r`n") + "`r`n", [Text.Encoding]::ASCII)
}
Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tools') -File | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $packageRoot 'tools')
}
Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'docs') -File | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination $packageRoot
}
$serverJar = Join-Path $packageRoot 'lib\vCampusServer.jar'
$database = Join-Path $packageRoot 'data\vCampus.accdb'
& java '-Dfile.encoding=UTF-8' -Xmx1500m --class-path $serverJar `
    (Join-Path $packageRoot 'tools\BuildDataset.java') $database `
    (Join-Path $packageRoot 'database') (Join-Path $packageRoot 'tools\generated.sql')
if ($LASTEXITCODE -ne 0) { throw 'Dataset build failed' }
& java '-Dfile.encoding=UTF-8' -Xmx1500m --class-path $serverJar `
    (Join-Path $packageRoot 'tools\ValidateDataset.java') $database `
    (Join-Path $packageRoot 'tools\counts.tsv')
if ($LASTEXITCODE -ne 0) { throw 'Dataset validation failed' }
Compress-Archive -LiteralPath $packageRoot -DestinationPath ($packageRoot + '.zip')
Write-Output "Package ready: $packageRoot"
