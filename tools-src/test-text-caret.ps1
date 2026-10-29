$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $root 'build/text-caret-test'
$source = Join-Path $out 'source'
$classes = Join-Path $out 'classes'
$main = Join-Path $out 'main'
$jdk = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { Split-Path (Get-Command javac).Source }
$files = @('javax/swing/text/JTextComponent.java', 'javax/swing/text/DefaultCaret.java', 'javax/swing/JTextArea.java')
New-Item -ItemType Directory -Force $classes, $main | Out-Null
foreach ($file in $files) {
    $dest = Join-Path $source $file
    New-Item -ItemType Directory -Force (Split-Path $dest) | Out-Null
    Copy-Item -LiteralPath (Join-Path $root "android-awt/src/main/java/$file") -Destination $dest
}
& "$jdk/javac.exe" --patch-module "java.desktop=$source" -d $classes @($files | ForEach-Object { Join-Path $source $_ })
if ($LASTEXITCODE) { throw 'Caret compilation failed' }
& "$jdk/javac.exe" -d $main (Join-Path $PSScriptRoot 'TestTextCaret.java')
if ($LASTEXITCODE) { throw 'Caret test compilation failed' }
& "$jdk/java.exe" '-Djava.awt.headless=true' -cp $main TestTextCaret
if ($LASTEXITCODE) { throw 'Desktop reference failed' }
& "$jdk/java.exe" '-Djava.awt.headless=true' --patch-module "java.desktop=$classes" -cp $main TestTextCaret
if ($LASTEXITCODE) { throw 'Caret compatibility test failed' }
