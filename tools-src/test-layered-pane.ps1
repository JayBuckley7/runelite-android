$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$output = Join-Path $root 'build/layered-pane-test'
$source = Join-Path $output 'source'
$patched = Join-Path $output 'patched'
$main = Join-Path $output 'main'
$jdk = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { Split-Path (Get-Command javac).Source }
New-Item -ItemType Directory -Force "$source/javax/swing", $patched, $main | Out-Null
Copy-Item -LiteralPath (Join-Path $root 'android-awt/src/main/java/javax/swing/JLayeredPane.java') -Destination "$source/javax/swing/JLayeredPane.java"
& "$jdk/javac.exe" --patch-module "java.desktop=$source" -d $patched "$source/javax/swing/JLayeredPane.java"
if ($LASTEXITCODE) { throw 'Layered pane compilation failed' }
& "$jdk/javac.exe" -d $main (Join-Path $PSScriptRoot 'TestLayeredPane.java')
if ($LASTEXITCODE) { throw 'Test compilation failed' }
& "$jdk/java.exe" '-Djava.awt.headless=true' -cp $main TestLayeredPane
if ($LASTEXITCODE) { throw 'Desktop reference failed' }
& "$jdk/java.exe" '-Djava.awt.headless=true' --patch-module "java.desktop=$patched" -cp $main TestLayeredPane
if ($LASTEXITCODE) { throw 'Compatibility layer failed' }
