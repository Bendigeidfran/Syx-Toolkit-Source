# Compiles the toolkit. Needs a Java 21 JDK, and no game files.
param([string]$Jdk = $env:SYX_JDK)
$ErrorActionPreference = 'Stop'
if (-not $Jdk) { throw 'Set -Jdk or SYX_JDK to a Java 21 JDK folder.' }
$javac = Join-Path $Jdk 'bin\javac.exe'
$root = Split-Path $PSScriptRoot -Parent
$out = Join-Path $root 'out'
if (Test-Path $out) { Remove-Item -Recurse -Force $out }

function Compile($name, $sources, $classpath, $extra) {
    $target = Join-Path $out $name
    New-Item -ItemType Directory -Force $target | Out-Null
    $files = Get-ChildItem (Join-Path $root $sources) -Recurse -Filter *.java | ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' }
    $argFile = Join-Path $out "$name.args"
    [IO.File]::WriteAllLines($argFile, [string[]]$files)
    $javacArgs = @('-encoding', 'UTF-8', '-Xlint:all', '-d', $target) + $extra
    if ($classpath) { $javacArgs += @('-cp', $classpath) }
    & $javac @javacArgs "@$argFile"
    if ($LASTEXITCODE -ne 0) { throw "Compiling $name failed." }
}

$libs = Join-Path $root 'lib'
foreach ($needed in 'flatlaf-3.7.2.jar', 'jlayer-1.0.1.jar', 'javasound-vorbis-2.1.0.jar') {
    if (-not (Test-Path (Join-Path $libs $needed))) { throw "lib\$needed is missing. Run scripts\fetch-libs.ps1 first." }
}
$libClasspath = Join-Path $libs '*'

# Audio conversion needs java.desktop and the two jars, which the core's own sources must not see.
# Its classes still go in out\core, and its public methods use only java.base types, so core stays limited.
Compile 'core' 'audio/src' $libClasspath @('--limit-modules', 'java.base,java.desktop')
Compile 'core' 'core/src' (Join-Path $out 'core') @('--limit-modules', 'java.base')
Compile 'gui' 'gui/src' ((Join-Path $out 'core') + ';' + $libClasspath) @()
# The command line and MCP server can only see java.base too, and need nothing from the game or the GUI.
Compile 'mcp' 'mcp/src' (Join-Path $out 'core') @('--limit-modules', 'java.base')
Compile 'cli' 'cli/src' ((Join-Path $out 'core') + ';' + (Join-Path $out 'mcp')) @('--limit-modules', 'java.base')
Compile 'test' 'test/src' ((Join-Path $out 'core') + ';' + (Join-Path $out 'gui') + ';' + (Join-Path $out 'mcp') + ';' + (Join-Path $out 'cli') + ';' + $libClasspath) @()
Write-Output "Built to $out"
