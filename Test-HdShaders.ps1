param([Parameter(Mandatory)][string]$Device, [switch]$SkipBuild, [switch]$FaceStorageBuffer)
$ErrorActionPreference='Stop'
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.6.7-hotspot'
$env:ANDROID_HOME=Join-Path $env:LOCALAPPDATA 'Android\Sdk'
Push-Location $PSScriptRoot
try {
    if (!$SkipBuild) {
        & .\gradlew.bat :android-hd:dumpGlesShaderProbes "-Phd.faceStorageBuffer=$($FaceStorageBuffer.IsPresent.ToString().ToLowerInvariant())" --console=plain *> build-hd-probes.log
        if ($LASTEXITCODE -ne 0) { Get-Content build-hd-probes.log -Tail 30; throw 'Shader expansion failed' }
    }
    $adb=Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
    $shaderDirectory=Join-Path $PSScriptRoot "android-hd/build/shader-probes/."
    & $adb -s $Device shell mkdir -p /data/local/tmp/hd-shader-probes
    & $adb -s $Device push $shaderDirectory /data/local/tmp/hd-shader-probes/ *> mobile-ganom/hd-shader-push.log
    if ($LASTEXITCODE -ne 0) { throw 'Shader push failed' }
    $results=@()
    foreach ($variant in @('basic','lit','shadow','mobile','tiled','effects','pcss','dither','average')) {
        foreach ($program in @('scene','ui','shadow','gamma','sky','star','sky_nebula_bake','water','terrain','tiled','gap')) {
            $stem="${variant}_${program}"
            $output=& $adb -s $Device shell /data/local/tmp/hd_gles_probe "/data/local/tmp/hd-shader-probes/${stem}_vert.glsl" "/data/local/tmp/hd-shader-probes/${stem}_frag.glsl" 2>&1
            $code=$LASTEXITCODE
            $output | Set-Content "mobile-ganom/hd-shader-$stem.log"
            $results+=@{variant=$variant; program=$program; exitCode=$code}
            Write-Output "$stem exit=$code"
            if ($code -ne 0) { $output | Select-Object -First 5 | Write-Output }
        }
    }
    $results | ConvertTo-Json | Set-Content mobile-ganom/hd-shader-results.json
    if ($results.exitCode | Where-Object { $_ -ne 0 }) { throw 'GLES shader preflight failed' }
} finally { Pop-Location }
