# Builds the double-clickable app in dist\, with its own trimmed Java runtime. Run build.ps1 first.
# The finished app needs no Java or JDK to run. It loads SongsOfSyx.jar from the user's install.
# It also writes dist\Syx-Toolkit-<version>.zip for a GitHub release, made before any player files are put back.
# -Dist builds somewhere else, for testing without replacing the app in dist\.
param([string]$Jdk = $env:SYX_JDK, [string]$Dist)
$ErrorActionPreference = 'Stop'
if (-not $Jdk) { throw 'Set -Jdk or SYX_JDK to a Java 21 JDK folder.' }

# jdk.crypto.ec is needed for HTTPS to the handbook, which fails with a handshake error without it.
$modules = 'java.base,java.desktop,jdk.crypto.ec'
# java.desktop brings the others with it.
$expectedModules = 'java.base java.datatransfer java.desktop java.prefs java.xml jdk.crypto.ec'

function ReleaseValue($file, $name) {
    $line = Get-Content $file | Where-Object { $_ -like "$name=*" } | Select-Object -First 1
    if (-not $line) { throw "$file has no $name." }
    return ($line -split '=', 2)[1].Trim('"')
}

$jdkVersion = ReleaseValue (Join-Path $Jdk 'release') 'JAVA_VERSION'
if ($jdkVersion -notlike '21.*') { throw "Packaging needs a Java 21 JDK, but this one is $jdkVersion." }

$root = Split-Path $PSScriptRoot -Parent
$out = Join-Path $root 'out'
$dist = if ($Dist) { $Dist } else { Join-Path $root 'dist' }
# Only the build output inside dist\ is replaced. The player files in the app folder are kept, below.
$appFolder = Join-Path $dist 'Syx Toolkit'
# Windows locks a running app's files, so check before deleting anything.
$running = Get-Process | Where-Object { $_.Path -and $_.Path.StartsWith($appFolder + '\', [StringComparison]::OrdinalIgnoreCase) }
if ($running) {
	$names = ($running | Select-Object -ExpandProperty Name -Unique) -join ', '
	throw "$names is running from $appFolder. Close the app, and any agent client connected to syx.exe, which keeps it open even when idle, then package again. Nothing has been changed."
}
$jarFolder = Join-Path $dist 'jar-input'
# The Manifests folder holds what players generate or are given, as well as what ships, so it's kept through a rebuild.
$keptManifests = Join-Path $dist 'manifests-kept'
if (Test-Path $keptManifests) { throw "$keptManifests holds manifests kept from a packaging run that stopped. Move them back into $appFolder\Manifests, then package again. Nothing has been changed." }
# Converted audio is the player's own files, kept through a rebuild the same way. A rebuild must not delete them.
$keptAudio = Join-Path $dist 'converted-audio-kept'
if (Test-Path $keptAudio) { throw "$keptAudio holds converted audio kept from a packaging run that stopped. Move it back into `"$appFolder\Converted Audio Files`", then package again. Nothing has been changed." }
# Unfinished wizards are the player's too: which question each is on. Kept the same way.
$keptWizards = Join-Path $dist 'wizards-kept'
if (Test-Path $keptWizards) { throw "$keptWizards holds wizard progress kept from a packaging run that stopped. Move it back into `"$appFolder\Wizards`", then package again. Nothing has been changed." }
$appManifests = Join-Path $appFolder 'Manifests'
if (Test-Path $appManifests) { Move-Item $appManifests $keptManifests }
$appAudio = Join-Path $appFolder 'Converted Audio Files'
if (Test-Path $appAudio) { Move-Item $appAudio $keptAudio }
$appWizards = Join-Path $appFolder 'Wizards'
if (Test-Path $appWizards) { Move-Item $appWizards $keptWizards }
foreach ($old in $appFolder, $jarFolder) {
    if (Test-Path $old) { Remove-Item -Recurse -Force $old }
}
New-Item -ItemType Directory -Force $jarFolder | Out-Null

# The libraries go beside toolkit.jar. Only these may be shipped, and they must be the versions fetch-libs.ps1 pins.
$allowedLibs = 'flatlaf-3.7.2.jar', 'flatlaf-fonts-roboto-2.137.jar', 'flatlaf-fonts-roboto-mono-3.000.jar', 'jlayer-1.0.1.jar', 'javasound-vorbis-2.1.0.jar'
foreach ($lib in $allowedLibs) {
    $source = Join-Path $root "lib\$lib"
    if (-not (Test-Path $source)) { throw "lib\$lib is missing. Run scripts\fetch-libs.ps1 first." }
    Copy-Item $source $jarFolder
}

$jar = Join-Path $jarFolder 'toolkit.jar'
& (Join-Path $Jdk 'bin\jar.exe') --create --file $jar -C (Join-Path $out 'core') . -C (Join-Path $out 'gui') . -C (Join-Path $out 'mcp') . -C (Join-Path $out 'cli') .
if ($LASTEXITCODE -ne 0) { throw 'Creating toolkit.jar failed.' }

# Only our own classes may be in the jar. This also keeps out anything that could hold game text, such as
# the handbook's tag data, which quotes vanilla files and is downloaded at run time instead.
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($jar)
$foreign = $zip.Entries | Where-Object { -not $_.FullName.EndsWith('/') -and $_.FullName -notmatch '^syx/.*\.class$' -and $_.FullName -notmatch '^META-INF/' }
$zip.Dispose()
if ($foreign) { throw "toolkit.jar contains files that aren't ours: $($foreign.FullName -join ', ')" }

$versionText = Get-Content (Join-Path $root 'core\src\syx\core\AppVersion.java') -Raw
$phase = [regex]::Match($versionText, 'PHASE\s*=\s*"(\w+)"').Groups[1].Value
$major = [regex]::Match($versionText, 'MAJOR\s*=\s*(\d+)').Groups[1].Value
$minor = [regex]::Match($versionText, 'MINOR\s*=\s*(\d+)').Groups[1].Value
if (-not ($phase -and $major -and $minor)) { throw "Couldn't read the version from AppVersion.java." }

# Both launchers carry real version information (version, vendor, description, copyright), taken from
# AppVersion, instead of jpackage's placeholders. Antivirus heuristics distrust executables without it.
$vendor = 'Syx Toolkit'
$copyright = 'Copyright (C) 2026 Bendigeidfran. Licensed under GPLv3 or later.'
$jlinkOptions = '--strip-native-commands --strip-debug --no-man-pages --no-header-files --compress=zip-6'
# syx.exe is the command line and MCP server. It needs a console launcher for its standard input and output to work.
$launcherProperties = Join-Path $dist 'syx-launcher.properties'
Set-Content -Path $launcherProperties -Encoding ascii -Value @('main-class=syx.cli.Main', 'win-console=true', 'description=Songs of Syx Modding Toolkit command line and MCP server')
& (Join-Path $Jdk 'bin\jpackage.exe') --type app-image --name 'Syx Toolkit' --input $jarFolder --main-jar toolkit.jar --main-class syx.gui.Main --add-launcher "syx=$launcherProperties" --app-version "$major.$minor" --vendor $vendor --description 'Songs of Syx Modding Toolkit' --copyright $copyright --module-path (Join-Path $Jdk 'jmods') --add-modules $modules --jlink-options $jlinkOptions --dest $dist
if ($LASTEXITCODE -ne 0) { throw 'jpackage failed.' }
Remove-Item -Force $launcherProperties
Remove-Item -Recurse -Force $jarFolder
if (-not (Test-Path (Join-Path $appFolder 'syx.exe'))) { throw 'The command line launcher, syx.exe, is missing.' }

# Check what was actually built.
$app = Join-Path $dist 'Syx Toolkit'
$release = Join-Path $app 'runtime\release'
$builtVersion = ReleaseValue $release 'JAVA_VERSION'
if ($builtVersion -notlike '21.*') { throw "The app's runtime is $builtVersion, not Java 21." }
$builtModules = ((ReleaseValue $release 'MODULES') -split '\s+' | Sort-Object) -join ' '
if ($builtModules -ne $expectedModules) { throw "The app's runtime has unexpected modules: $builtModules" }
$gameJar = Get-ChildItem $app -Recurse -File -Filter 'SongsOfSyx*.jar'
if ($gameJar) { throw "The app contains game files: $($gameJar.FullName -join ', ')" }
$expectedJars = (@('toolkit.jar') + $allowedLibs | Sort-Object) -join ' '
$builtJars = ((Get-ChildItem (Join-Path $app 'app') -Filter '*.jar').Name | Sort-Object) -join ' '
if ($builtJars -ne $expectedJars) { throw "The app has unexpected jars: $builtJars" }

# The manifests that ship, which hold names and hashes and no game code. They're pinned in one tracked file,
# and only these exact files go in, so a stray test manifest can't reach a release.
$pinFile = Join-Path $root 'core\src\syx\core\ShippedManifests.java'
$pins = @{}
foreach ($match in [regex]::Matches((Get-Content $pinFile -Raw), '"(SongsOfSyx-[^"]+\.manifest\.json\.gz)",\s*"([0-9a-f]{64})"')) {
    $pins[$match.Groups[1].Value] = $match.Groups[2].Value
}
if ($pins.Count -eq 0) { throw "No manifests are pinned in $pinFile." }
$manifestSource = Join-Path $root 'manifests'
if (-not (Test-Path $manifestSource)) { throw 'manifests\ is missing. Make them with syx.test.DevManifest build.' }
$extra = Get-ChildItem $manifestSource -File | Where-Object { -not $pins.ContainsKey($_.Name) }
if ($extra) { throw "manifests\ holds files that aren't pinned: $($extra.Name -join ', ')" }
$manifestTarget = Join-Path $app 'Manifests'
New-Item -ItemType Directory -Force $manifestTarget | Out-Null
foreach ($name in $pins.Keys) {
    $file = Join-Path $manifestSource $name
    if (-not (Test-Path $file)) { throw "manifests\$name is missing." }
    $hash = (Get-FileHash $file -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($hash -ne $pins[$name]) { throw "manifests\$name doesn't match its pin: $hash" }
    Copy-Item $file $manifestTarget
}

# The libraries are Apache 2.0, which asks for a copy of the licence and a list of what's included.
$zip = [IO.Compression.ZipFile]::OpenRead((Join-Path $root "lib\$($allowedLibs[0])"))
$entry = $zip.GetEntry('META-INF/LICENSE')
$reader = New-Object IO.StreamReader($entry.Open())
$licence = $reader.ReadToEnd()
$reader.Dispose()
$zip.Dispose()
$notices = @(
    'Syx Toolkit includes these third-party libraries.',
    '',
    'FlatLaf 3.7.2, Copyright FormDev Software GmbH. https://www.formdev.com/flatlaf/',
    'FlatLaf Roboto font 2.137, containing the Roboto font by Google. https://github.com/googlefonts/roboto',
    'FlatLaf Roboto Mono font 3.000, containing the Roboto Mono font by Google. https://github.com/googlefonts/RobotoMono',
    'These three are under the Apache License 2.0, which follows.',
    '',
    $licence,
    '',
    'JLayer 1.0.1, Copyright JavaZOOM. Used to read MP3 files. https://www.javazoom.net/javalayer/javalayer.html',
    'GNU Lesser General Public License 2.1. https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html',
    'The jar is shipped beside the app, unmodified, so it can be replaced.',
    '',
    'javasound-vorbis 2.1.0, Copyright Karstian Lee and the Xiph.Org Foundation. Used to write Ogg Vorbis files. https://github.com/Tianscar/javasound-vorbis',
    'GNU Lesser General Public License 2.0. https://www.gnu.org/licenses/old-licenses/lgpl-2.0.html',
    'The Vorbis encoder is also under the Xiph.Org BSD licence. https://www.xiph.org/licenses/bsd/',
    'The jar is shipped beside the app, unmodified, so it can be replaced.'
) -join "`r`n"
[IO.File]::WriteAllText((Join-Path $app 'THIRD-PARTY-NOTICES.txt'), $notices)

# The toolkit's own licence, GPLv3, travels with every copy. LICENSE holds the unchanged GPL text; the copyright
# notice and the Songs of Syx exception are the README's Licence section, which goes first.
$ownLicence = Join-Path $root 'LICENSE'
if (-not (Test-Path $ownLicence)) { throw 'LICENSE is missing from the project root.' }
$readme = [IO.File]::ReadAllText((Join-Path $root 'README.md'))
$licenceSection = [regex]::Match($readme, '(?s)## Licence\s*\r?\n(.*?)(?=\r?\n## |\z)')
if (-not $licenceSection.Success -or $licenceSection.Groups[1].Value -notmatch 'section 7') { throw "README.md has no Licence section with the Songs of Syx exception." }
$notice = ($licenceSection.Groups[1].Value.Trim() -replace '\[([^\]]+)\]\([^)]+\)', '$1') -replace '\r?\n', "`r`n"
$gplText = [IO.File]::ReadAllText($ownLicence) -replace '\r?\n', "`r`n"
[IO.File]::WriteAllText((Join-Path $app 'LICENSE.txt'), $notice + "`r`n`r`n" + ('-' * 76) + "`r`n`r`n" + $gplText)

# The release zip is made now, while the app holds only what was just built: none of the player's
# own manifests, reports, converted audio or wizard progress, which are put back after it.
$releaseZip = Join-Path $dist "Syx-Toolkit-$phase-v$major.$minor.zip"
if (Test-Path $releaseZip) { Remove-Item -Force $releaseZip }
foreach ($own in 'Diff Reports', 'Converted Audio Files', 'Wizards') {
    if (Test-Path (Join-Path $app $own)) { throw "The app contains a $own folder before zipping." }
}
# Entries are added one by one, because Windows PowerShell's CreateFromDirectory writes backslashes, which other unzip tools mishandle.
$archive = [IO.Compression.ZipFile]::Open($releaseZip, [IO.Compression.ZipArchiveMode]::Create)
try {
    $prefix = (Get-Item $app).Parent.FullName.TrimEnd('\') + '\'
    foreach ($file in Get-ChildItem $app -Recurse -File -Force) {
        $name = $file.FullName.Substring($prefix.Length).Replace('\', '/')
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName, $name, [IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally {
    $archive.Dispose()
}

# Put back what was kept. A file with a released one's name must be that file, or it's someone's own and isn't replaced.
if (Test-Path $keptManifests) {
    foreach ($file in Get-ChildItem $keptManifests -File) {
        $target = Join-Path $manifestTarget $file.Name
        if ($pins.ContainsKey($file.Name)) {
            if ((Get-FileHash $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant() -ne $pins[$file.Name]) {
                throw "$($file.FullName) has a released manifest's name but different contents. Rename or move it, then package again. It's still in $keptManifests."
            }
            continue
        }
        Copy-Item $file.FullName $target
    }
    Remove-Item -Recurse -Force $keptManifests
}
foreach ($name in $pins.Keys) {
    if ((Get-FileHash (Join-Path $manifestTarget $name) -Algorithm SHA256).Hash.ToLowerInvariant() -ne $pins[$name]) { throw "The app's $name isn't the pinned file." }
}

# Diff reports are a player's own, saved beside the .exe. A rebuild clears them, and none may ship.
if (Test-Path (Join-Path $app 'Diff Reports')) { throw 'The app contains a Diff Reports folder.' }
# Put the player's converted audio back. Unlike reports, a rebuild must not throw these away.
if (Test-Path $keptAudio) { Move-Item $keptAudio (Join-Path $app 'Converted Audio Files') }
if (Test-Path $keptWizards) { Move-Item $keptWizards (Join-Path $app 'Wizards') }

Write-Output "Packaged to $app"
Write-Output "Release zip: $releaseZip"
