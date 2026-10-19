param([switch]$Package)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$configPath = Join-Path $projectRoot '.env'
if (!(Test-Path -LiteralPath $configPath)) {
    Copy-Item -LiteralPath (Join-Path $projectRoot '.env.example') -Destination $configPath
    Write-Host 'Arquivo .env criado. Configure as credenciais e execute novamente.'
    exit 1
}
foreach ($line in Get-Content -LiteralPath $configPath -Encoding UTF8) {
    $entry = $line.Trim()
    if (!$entry -or $entry.StartsWith('#')) { continue }
    if ($entry -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$') { throw 'Linha inválida no .env; use NOME=valor.' }
    $key = $Matches[1]
    $value = $Matches[2].Trim()
    if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
        $value = $value.Substring(1, $value.Length - 2)
    }
    if (![Environment]::GetEnvironmentVariable($key, 'Process')) {
        [Environment]::SetEnvironmentVariable($key, $value, 'Process')
    }
}
foreach ($key in @('DB_URL','DB_USER','DB_PASSWORD','JWT_SECRET')) {
    $value = [Environment]::GetEnvironmentVariable($key, 'Process')
    if (!$value -or $value -match '^(CONFIGURE_|GENERATE_|CHANGE_ME)') { throw "Configure $key no arquivo .env." }
}
if ($env:DB_URL -notlike 'jdbc:mysql://*') { throw 'DB_URL deve usar jdbc:mysql:// para o MySQL 8.0.' }
if ([Text.Encoding]::UTF8.GetByteCount($env:JWT_SECRET) -lt 32) { throw 'JWT_SECRET precisa conter pelo menos 32 bytes.' }
if ($env:BOOTSTRAP_ADMIN_EMAIL -and (!$env:BOOTSTRAP_ADMIN_PASSWORD -or $env:BOOTSTRAP_ADMIN_PASSWORD -match '^CONFIGURE_')) {
    throw 'Configure BOOTSTRAP_ADMIN_PASSWORD ou deixe os dois campos de bootstrap vazios se o administrador já existe.'
}
if (!(Get-Command java -ErrorAction SilentlyContinue)) { throw 'Instale JDK 17 ou superior e configure o PATH.' }
Push-Location -LiteralPath $projectRoot
try {
    if ($Package) {
        & '.\mvnw.cmd' '-DskipTests' package
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        & java -jar '.\target\production-counter-1.0.0.jar'
    } else {
        & '.\mvnw.cmd' spring-boot:run
    }
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
