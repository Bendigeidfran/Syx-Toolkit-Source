# Zips a packaged app folder as a release zip, with every entry under "Syx Toolkit/".
# package.ps1 uses it, and so does the release workflow, after the launchers have been signed.
param([Parameter(Mandatory)][string]$App, [Parameter(Mandatory)][string]$Zip)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$appPath = (Resolve-Path $App).Path.TrimEnd('\')
foreach ($launcher in 'Syx Toolkit.exe', 'syx.exe') {
    if (-not (Test-Path (Join-Path $appPath $launcher))) { throw "$appPath has no $launcher, so it isn't a packaged app." }
}
if (Test-Path $Zip) { Remove-Item -Force $Zip }

# Entries are added one by one, because Windows PowerShell's CreateFromDirectory writes backslashes, which other unzip tools mishandle.
$archive = [IO.Compression.ZipFile]::Open($Zip, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in Get-ChildItem $appPath -Recurse -File -Force) {
        $name = 'Syx Toolkit/' + $file.FullName.Substring($appPath.Length + 1).Replace('\', '/')
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName, $name, [IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally {
    $archive.Dispose()
}
