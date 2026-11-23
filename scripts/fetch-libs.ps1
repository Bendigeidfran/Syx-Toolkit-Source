# Downloads the libraries the app needs into lib\, which isn't in the repo. Versions and checksums are pinned here.
$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$root = Split-Path $PSScriptRoot -Parent
$lib = Join-Path $root 'lib'
New-Item -ItemType Directory -Force $lib | Out-Null

$base = 'https://repo1.maven.org/maven2/com/formdev'
$jars = @(
    @{ Name = 'flatlaf'; Version = '3.7.2'; Sha256 = '917aff3963c88d797d0fd9b9ccbd70f7681c101df9d11c59e2bc7a3a6c0fabf4' },
    @{ Name = 'flatlaf-fonts-roboto'; Version = '2.137'; Sha256 = 'f46e549e8b553ca670c1ad2b26631ba76d20fd39efcd66dc79ed883fbcf5843e' },
    @{ Name = 'flatlaf-fonts-roboto-mono'; Version = '3.000'; Sha256 = '48165a40978a0d51fe1ed38947a30885edd40a4373d21daa4185f3d9bae3f83a' }
)

foreach ($jar in $jars) {
    $file = "$($jar.Name)-$($jar.Version).jar"
    $target = Join-Path $lib $file
    if ((Test-Path $target) -and (Get-FileHash $target -Algorithm SHA256).Hash -eq $jar.Sha256) {
        Write-Output "$file is already here."
        continue
    }
    $temp = "$target.download"
    Invoke-WebRequest -UseBasicParsing -Uri "$base/$($jar.Name)/$($jar.Version)/$file" -OutFile $temp
    if ((Get-FileHash $temp -Algorithm SHA256).Hash -ne $jar.Sha256) {
        Remove-Item -Force $temp
        throw "$file doesn't match its expected checksum, so it wasn't kept."
    }
    Move-Item -Force $temp $target
    Write-Output "Downloaded $file."
}

# JLayer reads MP3. javasound-vorbis is a self-contained pure-Java Vorbis encoder, with no further jars.
$more = @(
    @{ File = 'jlayer-1.0.1.jar'; Url = 'https://repo1.maven.org/maven2/javazoom/jlayer/1.0.1/jlayer-1.0.1.jar'; Sha256 = '850508c837454a1b06017c32a36876fae516de1e89a829f725fee1e6dcc52000' },
    @{ File = 'javasound-vorbis-2.1.0.jar'; Url = 'https://repo1.maven.org/maven2/com/tianscar/javasound/javasound-vorbis/2.1.0/javasound-vorbis-2.1.0.jar'; Sha256 = 'd5f3b6509f8b5b1cbde21b6ba21a5c1ad1dbaef52b580df0f0c0c969cc7bf86e' }
)

foreach ($jar in $more) {
    $target = Join-Path $lib $jar.File
    if ((Test-Path $target) -and (Get-FileHash $target -Algorithm SHA256).Hash -eq $jar.Sha256) {
        Write-Output "$($jar.File) is already here."
        continue
    }
    $temp = "$target.download"
    Invoke-WebRequest -UseBasicParsing -Uri $jar.Url -OutFile $temp
    if ((Get-FileHash $temp -Algorithm SHA256).Hash -ne $jar.Sha256) {
        Remove-Item -Force $temp
        throw "$($jar.File) doesn't match its expected checksum, so it wasn't kept."
    }
    Move-Item -Force $temp $target
    Write-Output "Downloaded $($jar.File)."
}

# Build tools go in tools\, apart from the libraries, because they're never shipped. rcedit (Electron, MIT) sets
# syx.exe's product name, which jpackage can't, so both launchers carry the project name that code signing checks.
$tools = Join-Path $root 'tools'
New-Item -ItemType Directory -Force $tools | Out-Null
$rcedit = @{ File = 'rcedit-x64.exe'; Url = 'https://github.com/electron/rcedit/releases/download/v2.0.0/rcedit-x64.exe'; Sha256 = '3e7801db1a5edbec91b49a24a094aad776cb4515488ea5a4ca2289c400eade2a' }
$target = Join-Path $tools $rcedit.File
if ((Test-Path $target) -and (Get-FileHash $target -Algorithm SHA256).Hash -eq $rcedit.Sha256) {
    Write-Output "$($rcedit.File) is already here."
} else {
    $temp = "$target.download"
    Invoke-WebRequest -UseBasicParsing -Uri $rcedit.Url -OutFile $temp
    if ((Get-FileHash $temp -Algorithm SHA256).Hash -ne $rcedit.Sha256) {
        Remove-Item -Force $temp
        throw "$($rcedit.File) doesn't match its expected checksum, so it wasn't kept."
    }
    Move-Item -Force $temp $target
    Write-Output "Downloaded $($rcedit.File)."
}
