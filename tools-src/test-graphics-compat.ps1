param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [Parameter(Mandatory=$true)][string]$Jdk,
    [Parameter(Mandatory=$true)][string]$Sdk,
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [string]$DiagnosticApk,
    [string]$Repo = (Split-Path -Parent $PSScriptRoot)
)
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-[0-9]+$') { throw 'Only an emulator may run this diagnostic probe.' }
function Run($Exe, [string[]]$Arguments) {
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Exe failed ($LASTEXITCODE)" }
}
New-Item -ItemType Directory -Force "$OutputDirectory/classes" | Out-Null
Run "$Jdk/bin/javac.exe" @('--release', '8', '-encoding', 'UTF-8', '-d', "$OutputDirectory/classes", "$PSScriptRoot/TestGraphicsCompatibility.java")
Run "$Jdk/bin/java.exe" @('-Djava.awt.headless=true', '-cp', "$OutputDirectory/classes", 'TestGraphicsCompatibility', "$OutputDirectory/desktop-gallery.png") | Tee-Object "$OutputDirectory/desktop-results.txt"
$adb = "$Sdk/platform-tools/adb.exe"
$package = 'net.runelite.mp.compatprobe'
$activity = "$package/net.runelite.mp.compatprobe.SwingContractProbeActivity"
# This must be the separate diagnostic APK built with tools-src/swing-ui-probe/probe.init.gradle.
# A normal Zygote-launched Activity is necessary: shell app_process lacks Android font initialization.
$installed = & $adb -s $Serial shell pm path $package
if ($LASTEXITCODE -ne 0 -or -not ($installed -match '^package:')) { throw 'Install the separate compatprobe APK before running this script.' }
$remoteApk = (($installed | Where-Object { $_ -match '^package:' } | Select-Object -First 1) -replace '^package:', '').Trim()
$remoteDigest = & $adb -s $Serial shell sha256sum $remoteApk
if ($LASTEXITCODE -ne 0 -or ($remoteDigest -join '') -notmatch '^([0-9a-fA-F]{64})') { throw 'Could not hash installed diagnostic APK.' }
$installedHash = $Matches[1]
$remoteDigest | Out-File -Encoding utf8 "$OutputDirectory/installed-apk-sha256.txt"
if ($DiagnosticApk -and (Get-FileHash -Algorithm SHA256 -LiteralPath $DiagnosticApk).Hash -ne $installedHash) { throw 'Installed diagnostic APK differs from the supplied artifact.' }
Run $adb @('-s', $Serial, 'shell', 'am', 'force-stop', $package)
Run $adb @('-s', $Serial, 'shell', 'run-as', $package, 'rm', '-f', 'files/graphics-result.txt', 'files/graphics-gallery.png')
Run $adb @('-s', $Serial, 'shell', 'am', 'start', '-W', '-n', $activity, '--es', 'probe', 'graphics')
$deadline = [DateTime]::UtcNow.AddSeconds(90)
$result = $null
while ([DateTime]::UtcNow -lt $deadline) {
    $available = & $adb -s $Serial shell run-as $package test -f files/graphics-result.txt
    if ($LASTEXITCODE -eq 0) {
        $result = & $adb -s $Serial shell run-as $package cat files/graphics-result.txt
        break
    }
    Start-Sleep -Milliseconds 500
}
$pidOutput = & $adb -s $Serial shell pidof $package
$probePid = ($pidOutput -join '').Trim()
if ($probePid) { Run $adb @('-s', $Serial, 'logcat', "--pid=$probePid", '-d') | Out-File -Encoding utf8 "$OutputDirectory/android-log.txt" }
if (-not $result) { throw 'Diagnostic timed out; inspect android-log.txt for setup or rendering failures.' }
$result | Tee-Object "$OutputDirectory/android-results.txt"
if ($DiagnosticApk) { Get-FileHash -Algorithm SHA256 -LiteralPath $DiagnosticApk | Format-List | Out-File "$OutputDirectory/tested-apk-sha256.txt" }
if (($result -join "`n") -notmatch '^PASS graphics contracts') { throw 'Android graphics contracts failed; see android-results.txt.' }
# Copy binary data without PowerShell's text redirection/transcoding.
$info = New-Object System.Diagnostics.ProcessStartInfo
$info.FileName = $adb
$info.Arguments = "-s $Serial exec-out run-as $package cat files/graphics-gallery.png"
$info.UseShellExecute = $false
$info.CreateNoWindow = $true
$info.RedirectStandardOutput = $true
$process = [System.Diagnostics.Process]::Start($info)
$output = [System.IO.File]::Create((Join-Path $OutputDirectory 'android-gallery.png'))
try { $process.StandardOutput.BaseStream.CopyTo($output) } finally { $output.Dispose() }
$process.WaitForExit()
if ($process.ExitCode -ne 0) { throw 'Could not retrieve diagnostic gallery.' }
