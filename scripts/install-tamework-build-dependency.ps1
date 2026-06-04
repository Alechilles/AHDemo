param(
    [string] $ModsDirectory = "C:/Users/22ale/AppData/Roaming/Hytale/install/release/package/game/latest/Server/mods",
    [int] $MajorVersion = 2,
    [string] $VersionAlias = "2.x"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $ModsDirectory -PathType Container)) {
    throw "Tamework mods directory does not exist: $ModsDirectory"
}

$candidates = Get-ChildItem -LiteralPath $ModsDirectory -File -Filter "Alec's Tamework! v$MajorVersion*.jar" |
    ForEach-Object {
        if ($_.BaseName -match "^Alec's Tamework! v(?<version>\d+\.\d+\.\d+)$") {
            [pscustomobject]@{
                File = $_
                Version = [version] $Matches.version
            }
        }
    } |
    Where-Object { $null -ne $_ -and $_.Version.Major -eq $MajorVersion } |
    Sort-Object -Property Version -Descending

if (-not $candidates) {
    throw "No Tamework v$MajorVersion.x jar found in $ModsDirectory"
}

$selected = $candidates[0].File
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$mavenWrapper = Join-Path $repoRoot "mvnw.cmd"

if (-not (Test-Path -LiteralPath $mavenWrapper -PathType Leaf)) {
    throw "Maven wrapper not found: $mavenWrapper"
}

& $mavenWrapper -N org.apache.maven.plugins:maven-install-plugin:3.1.2:install-file `
    "-Dfile=$($selected.FullName)" `
    "-DgroupId=com.alechilles" `
    "-DartifactId=alecs-tamework" `
    "-Dversion=$VersionAlias" `
    "-Dpackaging=jar"

if ($LASTEXITCODE -ne 0) {
    throw "Failed to install $($selected.Name) as alecs-tamework:$VersionAlias"
}

Write-Host "Installed $($selected.Name) as com.alechilles:alecs-tamework:$VersionAlias"
