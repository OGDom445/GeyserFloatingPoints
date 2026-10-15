$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$scratch = Join-Path $project 'build/compatibility'
$provided = Join-Path $scratch 'provided'
New-Item -ItemType Directory -Force $scratch, $provided | Out-Null

function Download-Checked($url, $path, $sha256) {
    Invoke-WebRequest $url -OutFile $path
    if ($sha256 -and (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $sha256) {
        throw "Hash mismatch: $path"
    }
}

$maven = 'https://repo.maven.apache.org/maven2'
$asm = Join-Path $scratch 'asm-9.8.jar'
Download-Checked "$maven/org/ow2/asm/asm/9.8/asm-9.8.jar" $asm $null
foreach ($module in @('netty-common','netty-buffer','netty-transport','netty-resolver','netty-codec-base','netty-codec','netty-handler','netty-codec-http','netty-codec-http2','netty-codec-haproxy')) {
    Download-Checked "$maven/io/netty/$module/4.2.15.Final/$module-4.2.15.Final.jar" (Join-Path $provided "$module.jar") $null
}
Download-Checked "$maven/com/google/code/gson/gson/2.14.0/gson-2.14.0.jar" (Join-Path $provided 'gson.jar') $null

Push-Location $project
try {
    & ./gradlew.bat clean shadowJar --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
    & javac --release 21 -cp $asm -d $scratch (Join-Path $PSScriptRoot 'CompatibilityCheck.java')
    if ($LASTEXITCODE -ne 0) { throw 'Harness compilation failed' }
    foreach ($target in @(
        @{Version='2.11.3'; Build=1254; Hash='9ebd45b5728a6ffa97a91766d7511c4292c84e6a226bfde0233d5a95b373a20e'},
        @{Version='2.12.0'; Build=1258; Hash='585eb449dbdaef09691433f84d29a5c557c3a27effc40c6d50b245725dc91b41'}
    )) {
        $geyser = Join-Path $scratch "Geyser-Spigot-$($target.Version).jar"
        Download-Checked "https://download.geysermc.org/v2/projects/geyser/versions/$($target.Version)/builds/$($target.Build)/downloads/spigot" $geyser $target.Hash
        & java -cp "$scratch;$asm" CompatibilityCheck (Join-Path $project 'build/libs/geyserfloatingpoints.jar') $geyser $provided
        if ($LASTEXITCODE -ne 0) { throw "Compatibility check failed on $($target.Version)" }
    }
} finally {
    Pop-Location
}
