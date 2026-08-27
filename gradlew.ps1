#!/usr/bin/env pwsh
# Gradle wrapper for PowerShell.
# Usage: .\gradlew.ps1 <task> [args...]

$ErrorActionPreference = 'Continue'

# ------- 项目根目录（脚本所在目录） -------
$appHome = $PSScriptRoot
if ([string]::IsNullOrEmpty($appHome)) {
    $appHome = (Get-Location).Path
}

$appBaseName = Split-Path -Leaf $appHome

# ------- 定位 wrapper jar -------
$wrapperJar = Join-Path $appHome (Join-Path 'gradle\wrapper' 'gradle-wrapper.jar')
if (-not (Test-Path -LiteralPath $wrapperJar)) {
    Write-Error "Could not find Gradle wrapper jar at: $wrapperJar"
    exit 1
}

# ------- 解析 java 可执行文件 -------
$javaExe = $null
if ($env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (Test-Path -LiteralPath $candidate) {
        $javaExe = $candidate
    }
}
if (-not $javaExe) {
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($cmd) {
        $javaExe = $cmd.Source
    }
}
if (-not $javaExe) {
    Write-Error "Java not found. Set JAVA_HOME to a JDK or add 'java' to PATH."
    exit 1
}

# ------- 组装 JVM 参数 -------
function Join-Opts([string]$raw) {
    if ([string]::IsNullOrWhiteSpace($raw)) {
        return @()
    }
    return @($raw.Trim() -split '\s+')
}

$defaultJvmOpts = Join-Opts $env:DEFAULT_JVM_OPTS
$javaOpts      = Join-Opts $env:JAVA_OPTS
$gradleOpts    = Join-Opts $env:GRADLE_OPTS

# ------- 运行 Gradle -------
$runArgs = @()
$runArgs += $defaultJvmOpts
$runArgs += $javaOpts
$runArgs += $gradleOpts
$runArgs += "-Dorg.gradle.appname=$appBaseName"
$runArgs += '-classpath', $wrapperJar
$runArgs += 'org.gradle.wrapper.GradleWrapperMain'
if ($args.Count -gt 0) {
    $runArgs += $args
}

& $javaExe @runArgs
exit $LASTEXITCODE
