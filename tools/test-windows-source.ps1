#requires -version 5
<#
.SYNOPSIS
  Run one checked-in nREPL alias through native Windows Chez.

.DESCRIPTION
  Invokes Jolt's source-mode CLI directly from PowerShell. MSYS2 is needed only
  to build Chez on Windows x86-64; test execution does not route native paths
  or Clojure source through bash.
#>
param(
  [string]$ProjectPath = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
  [string]$RuntimePath = "D:\src\jolt-proposal",
  [string]$ChezExe = "D:\chez-10.4.1\bin\scheme.exe",
  [string]$TestAlias = "-M:test",
  [string]$GitLibsPath = "",
  [string]$ShellExe = "",
  [ValidateSet("x86-64", "aarch64")]
  [string]$ExpectedArch = "x86-64",
  [switch]$InstallHegel,
  [int]$TimeoutSeconds = 1200
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $ChezExe)) {
  throw "test-windows-source.ps1: scheme.exe not found at $ChezExe"
}
if (-not (Test-Path (Join-Path $RuntimePath "host\chez\cli.ss"))) {
  throw "test-windows-source.ps1: host\chez\cli.ss not found under $RuntimePath"
}
if ($TimeoutSeconds -le 0) {
  throw "test-windows-source.ps1: TimeoutSeconds must be positive"
}
if (-not $TestAlias.StartsWith("-M:")) {
  throw "test-windows-source.ps1: TestAlias must be a -M: alias"
}

if ([string]::IsNullOrWhiteSpace($ShellExe)) {
  $candidates = @(
    "$env:ProgramFiles\Git\bin\sh.exe",
    "${env:ProgramFiles(x86)}\Git\bin\sh.exe",
    "C:\Program Files\Git\bin\sh.exe"
  ) | Where-Object { $_ -and (Test-Path $_) }
  if ($candidates) {
    $ShellExe = $candidates[0]
  }
  else {
    $command = Get-Command sh -ErrorAction SilentlyContinue
    if ($command) {
      $ShellExe = $command.Source
    }
  }
}
if ([string]::IsNullOrWhiteSpace($ShellExe) -or -not (Test-Path $ShellExe)) {
  throw "test-windows-source.ps1: sh.exe not found; pass -ShellExe explicitly"
}

$env:JOLT_PWD = $ProjectPath
$env:JOLT_AOT_CACHE = "0"
$env:JOLT_VERSION = "dev"
$env:JOLT_SH = (Resolve-Path $ShellExe).Path
$env:JOLT_EXPECTED_ARCH = $ExpectedArch

# With HOME unset, Jolt's fallback gitlibs path is relative. The runtime writes
# it relative to JOLT_PWD but can later check it relative to the process working
# directory, so native Windows dependency resolution must use an absolute path.
if ([string]::IsNullOrWhiteSpace($GitLibsPath)) {
  $GitLibsPath = Join-Path $ProjectPath ".jolt-cache\gitlibs"
}
if (-not (Test-Path $GitLibsPath)) {
  $null = New-Item -ItemType Directory -Force -Path $GitLibsPath
}
$env:JOLT_GITLIBS = (Resolve-Path $GitLibsPath).Path

function Invoke-Jolt {
  param(
    [string]$Phase,
    [string[]]$JoltArgs
  )

  Write-Host $Phase
  $arguments = @("--script", "host\chez\cli.ss") + $JoltArgs
  $process = Start-Process `
    -FilePath $ChezExe `
    -ArgumentList $arguments `
    -NoNewWindow `
    -PassThru

  # PowerShell 5.1 may leave ExitCode empty until the process handle has been
  # materialized. Refuse to turn an unobserved exit code into false success.
  $null = $process.Handle
  if ($process.WaitForExit($TimeoutSeconds * 1000)) {
    $exitCode = $process.ExitCode
    if ($null -eq $exitCode) {
      throw "$Phase observed no process exit code; refusing to report success"
    }
    if ($exitCode -ne 0) {
      throw "$Phase failed with exit code $exitCode"
    }
  }
  else {
    [Console]::Error.WriteLine(
      "$Phase timed out after $TimeoutSeconds seconds; terminating PID $($process.Id)"
    )
    try {
      $process.Kill()
      $process.WaitForExit()
    }
    catch {
      [Console]::Error.WriteLine(
        "failed to terminate timed-out PID $($process.Id): $($_.Exception.Message)"
      )
    }
    throw "$Phase timed out"
  }
}

Write-Host "nREPL native Windows source gate"
Write-Host "  JOLT_PWD = $env:JOLT_PWD"
Write-Host "  runtime  = $RuntimePath"
Write-Host "  scheme   = $ChezExe"
Write-Host "  sh       = $env:JOLT_SH"
Write-Host "  arch     = $env:JOLT_EXPECTED_ARCH"
Write-Host "  alias    = $TestAlias"
Write-Host "  gitlibs  = $env:JOLT_GITLIBS"
Write-Host "  hegel    = $InstallHegel"
Write-Host ""

Push-Location $RuntimePath
try {
  if ($InstallHegel) {
    $installAlias = "-A:" + $TestAlias.Substring(3)
    Invoke-Jolt -Phase "Install libhegel for $installAlias" `
      -JoltArgs @($installAlias, "-m", "hegel.install")
  }
  Invoke-Jolt -Phase "Run $TestAlias" -JoltArgs @($TestAlias)
}
finally {
  Pop-Location
}
