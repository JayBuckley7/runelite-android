param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [Parameter(Mandatory=$true)][string]$Jdk,
    [Parameter(Mandatory=$true)][string]$Sdk,
    [Parameter(Mandatory=$true)][string]$Apk,
    [Parameter(Mandatory=$true)][string]$JoptJar,
    [Parameter(Mandatory=$true)][string]$ProtobufJar,
    [Parameter(Mandatory=$true)][string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-[0-9]+$') { throw 'This test only targets an emulator.' }
function Run($Exe, [string[]]$Arguments) {
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Exe failed ($LASTEXITCODE)" }
}
$androidJar = "$Sdk/platforms/android-37.0/android.jar"
$r8 = (Get-ChildItem "$env:USERPROFILE/.gradle/caches/modules-2/files-2.1/com.android.tools/r8" -Recurse -Filter '*.jar' | Select-Object -First 1).FullName
New-Item -ItemType Directory -Force "$OutputDirectory/classes" | Out-Null
Run "$Jdk/bin/javac.exe" @('--release', '8', '-cp', "$JoptJar;$ProtobufJar", '-d', "$OutputDirectory/classes", "$PSScriptRoot/TestReleaseProtobuf.java", "$PSScriptRoot/TestReleasePluginApi.java")
Run "$Jdk/bin/jar.exe" @('cf', "$OutputDirectory/probe.jar", '-C', "$OutputDirectory/classes", '.')
# Dependencies are library inputs only: the test must use the copies inside the APK.
Run "$Jdk/bin/java.exe" @('-cp', $r8, 'com.android.tools.r8.D8', '--release', '--min-api', '26', '--lib', $androidJar, '--lib', $JoptJar, '--lib', $ProtobufJar, '--output', "$OutputDirectory/probe-dex.jar", "$OutputDirectory/probe.jar")
Get-FileHash -Algorithm SHA256 $Apk, $JoptJar, $ProtobufJar | Format-List | Out-File "$OutputDirectory/inputs-sha256.txt"
$adb = "$Sdk/platform-tools/adb.exe"
$remote = '/data/local/tmp/release-plugin-api'
Run $adb @('-s', $Serial, 'shell', 'mkdir', '-p', $remote)
Run $adb @('-s', $Serial, 'push', $Apk, "$remote/host.apk")
Run $adb @('-s', $Serial, 'push', "$OutputDirectory/probe-dex.jar", "$remote/probe.jar")
Run $adb @('-s', $Serial, 'shell', "CLASSPATH=$remote/host.apk`:$remote/probe.jar dalvikvm TestReleasePluginApi") | Tee-Object "$OutputDirectory/android-results.txt"
Run $adb @('-s', $Serial, 'shell', 'rm', "$remote/host.apk")
