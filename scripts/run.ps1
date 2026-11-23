# Starts the app. Needs a Java 21 runtime. During development the game's jre can stand in for it.
param([string]$Java = $env:SYX_JAVA, [string]$Settings)
$ErrorActionPreference = 'Stop'
if (-not $Java) { throw 'Set -Java or SYX_JAVA to a Java 21 folder, for example <game>\jre.' }
$root = Split-Path $PSScriptRoot -Parent
$out = Join-Path $root 'out'
$classpath = (Join-Path $out 'core') + ';' + (Join-Path $out 'gui') + ';' + (Join-Path $root 'lib\*')
$launchArgs = @('-cp', $classpath)
if ($Settings) { $launchArgs += "-Dsyx.settings=$Settings" }
& (Join-Path $Java 'bin\javaw.exe') @launchArgs 'syx.gui.Main'
