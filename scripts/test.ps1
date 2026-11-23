# Runs the tests. Tests that need the game's parser skip when no game folder is given.
param([string]$Java = $env:SYX_JAVA, [string]$Game = $env:SYX_GAME)
$ErrorActionPreference = 'Stop'
if (-not $Java) { throw 'Set -Java or SYX_JAVA to a Java 21 folder, for example <game>\jre.' }
$root = Split-Path $PSScriptRoot -Parent
$out = Join-Path $root 'out'
$classpath = (Join-Path $out 'core') + ';' + (Join-Path $out 'gui') + ';' + (Join-Path $out 'mcp') + ';' + (Join-Path $out 'cli') + ';' + (Join-Path $out 'test') + ';' + (Join-Path $root 'lib\*')
$testArgs = @()
if ($Game) { $testArgs += $Game }
$failures = 0
foreach ($test in 'syx.test.SyntaxCheckTest', 'syx.test.ModSetupTest', 'syx.test.ModInfoTest', 'syx.test.AssetSchemaTest', 'syx.test.AssetEditTest', 'syx.test.ParityTest', 'syx.test.OperationsTest', 'syx.test.WizardTest', 'syx.test.PortraitWizardTest', 'syx.test.HandbookTest', 'syx.test.ThemeTest', 'syx.test.ManifestTest', 'syx.test.AudioConversionTest', 'syx.test.PortraitSimTest', 'syx.test.MapSpriteSimTest', 'syx.test.NormalGenTest') {
    Write-Output "--- $test"
    & (Join-Path $Java 'bin\java.exe') -cp $classpath $test @testArgs
    if ($LASTEXITCODE -ne 0) { $failures++ }
}
exit $failures
