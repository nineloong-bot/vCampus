[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$buildStartedAt = Get-Date

Push-Location $repositoryRoot
try {
    & mvn.cmd clean verify
    if ($LASTEXITCODE -ne 0) { throw "Maven verification failed with exit code $LASTEXITCODE." }

    & mvn.cmd -DskipTests javadoc:aggregate
    if ($LASTEXITCODE -ne 0) { throw "JavaDoc generation failed with exit code $LASTEXITCODE." }

    $generatedDocs = Join-Path $repositoryRoot 'target\reports\apidocs'
    $distributionDocs = Join-Path $repositoryRoot 'vcampus-distribution\docs\apidocs'
    if (-not (Test-Path (Join-Path $generatedDocs 'index.html'))) {
        throw 'The generated JavaDoc index is missing.'
    }

    $expectedDistributionDocs = [IO.Path]::GetFullPath(
        (Join-Path $repositoryRoot 'vcampus-distribution\docs\apidocs'))
    if ([IO.Path]::GetFullPath($distributionDocs) -ne $expectedDistributionDocs) {
        throw 'Refusing to replace JavaDoc outside the distribution directory.'
    }
    if (Test-Path $distributionDocs) {
        Remove-Item -LiteralPath $distributionDocs -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $distributionDocs | Out-Null
    Copy-Item -Path (Join-Path $generatedDocs '*') -Destination $distributionDocs -Recurse -Force

    $requiredOutputs = @(
        'vcampus-distribution\lib\vCampusClient.jar',
        'vcampus-distribution\lib\vCampusServer.jar',
        'vcampus-distribution\docs\apidocs\index.html'
    )
    foreach ($relativePath in $requiredOutputs) {
        $output = Get-Item (Join-Path $repositoryRoot $relativePath)
        if ($output.LastWriteTime -lt $buildStartedAt) {
            throw "Output was not refreshed by this build: $relativePath"
        }
    }
} finally {
    Pop-Location
}

Write-Host 'Course deliverables built and verified successfully.'
