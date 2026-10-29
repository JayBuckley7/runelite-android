$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $root 'build/input-dialog-test'
$source = Join-Path $out 'source'
$classes = Join-Path $out 'classes'
$main = Join-Path $out 'main'
$jdk = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { Split-Path (Get-Command javac).Source }
New-Item -ItemType Directory -Force "$source/javax/swing", $classes, $main | Out-Null
Copy-Item -LiteralPath (Join-Path $root 'android-awt/src/main/java/javax/swing/JOptionPane.java') -Destination "$source/javax/swing/JOptionPane.java"
& "$jdk/javac.exe" --patch-module "java.desktop=$source" -d $classes "$source/javax/swing/JOptionPane.java"
if ($LASTEXITCODE) { throw 'Input dialog compilation failed' }
& "$jdk/javac.exe" --patch-module "java.desktop=$classes" -d $main (Join-Path $PSScriptRoot 'TestSwingInputDialogs.java')
if ($LASTEXITCODE) { throw 'Input dialog test compilation failed' }
& "$jdk/java.exe" '-Djava.awt.headless=true' --patch-module "java.desktop=$classes" -cp $main TestSwingInputDialogs
if ($LASTEXITCODE) { throw 'Input dialog test failed' }
