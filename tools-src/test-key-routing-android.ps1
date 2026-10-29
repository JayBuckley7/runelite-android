param(
    [Parameter(Mandatory=$true)][string]$Apk,
    [Parameter(Mandatory=$true)][string]$Sdk,
    [Parameter(Mandatory=$true)][string]$Jdk,
    [string]$Serial = 'emulator-5580'
)
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-[0-9]+$') { throw 'This probe only targets an emulator.' }
function Run($Exe, [string[]]$Arguments) {
    & $Exe @Arguments
    if ($LASTEXITCODE) { throw "$Exe failed ($LASTEXITCODE)" }
}
$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $root 'build/key-routing-test'
New-Item -ItemType Directory -Force "$out/classes", "$out/lib" | Out-Null
$r8 = (Get-ChildItem "$env:USERPROFILE/.gradle/caches/modules-2/files-2.1/com.android.tools/r8" -Recurse -Filter '*.jar' | Select-Object -First 1).FullName
$androidJar = "$Sdk/platforms/android-37.0/android.jar"
$awt = Join-Path $root 'android-awt/build/intermediates/compile_library_classes_jar/release/bundleLibCompileToJarRelease/classes.jar'
Run "$Jdk/bin/javac.exe" @('--release', '8', '-d', "$out/classes", "$PSScriptRoot/TestKeyDispatch.java", "$PSScriptRoot/TestLayeredPane.java", "$PSScriptRoot/TestTextCaret.java")
Run "$Jdk/bin/jar.exe" @('cf', "$out/probe.jar", '-C', "$out/classes", '.')
Run "$Jdk/bin/java.exe" @('-cp', $r8, 'com.android.tools.r8.D8', '--release', '--min-api', '26', '--lib', $androidJar, '--lib', $awt, '--output', "$out/probe-dex.jar", "$out/probe.jar")
$zip = [System.IO.Compression.ZipFile]::OpenRead($Apk)
try {
    $entry = $zip.GetEntry('lib/x86_64/librunelite_awt_native.so')
    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, "$out/lib/librunelite_awt_native.so", $true)
} finally { $zip.Dispose() }
$adb = "$Sdk/platform-tools/adb.exe"
$remote = '/data/local/tmp/key-routing-test'
Run $adb @('-s', $Serial, 'shell', 'mkdir', '-p', "$remote/lib")
Run $adb @('-s', $Serial, 'push', $Apk, "$remote/host.apk")
Run $adb @('-s', $Serial, 'push', "$out/probe-dex.jar", "$remote/probe.jar")
Run $adb @('-s', $Serial, 'push', "$out/lib/librunelite_awt_native.so", "$remote/lib/")
Run $adb @('-s', $Serial, 'shell', "CLASSPATH=$remote/host.apk`:$remote/probe.jar dalvikvm -Djava.library.path=$remote/lib TestKeyDispatch net.runelite.mp.ui.bridge.KeyDispatch dispatchAwtEvent")
Run $adb @('-s', $Serial, 'shell', "CLASSPATH=$remote/host.apk`:$remote/probe.jar dalvikvm -Djava.library.path=$remote/lib TestLayeredPane host")
Run $adb @('-s', $Serial, 'shell', "CLASSPATH=$remote/host.apk`:$remote/probe.jar dalvikvm -Djava.library.path=$remote/lib TestTextCaret")
