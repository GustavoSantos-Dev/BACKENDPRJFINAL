param([switch]$CheckOnly, [string]$AdminUser, [Security.SecureString]$AdminPassword)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$configPath = Join-Path $projectRoot '.env'
$config = @{}
foreach ($line in Get-Content -LiteralPath $configPath -Encoding UTF8) {
    if ($line.Trim() -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
        $value = $Matches[2].Trim()
        $key = $Matches[1]
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $config[$key] = $value
    }
}
if (!$config['DB_URL'] -or !$config['DB_URL'].StartsWith('jdbc:mysql://')) { throw 'Configure DB_URL para MySQL no .env.' }
$connection = [Uri]$config['DB_URL'].Substring(5)
if ($connection.Host -notin @('localhost','127.0.0.1','::1','[::1]')) { throw 'Este assistente configura somente MySQL local.' }
$database = [Uri]::UnescapeDataString($connection.AbsolutePath.Trim('/'))
$appUser = $config['DB_USER']
$appPassword = $config['DB_PASSWORD']
if ($database -notmatch '^[A-Za-z][A-Za-z0-9_]{0,63}$' -or $database -in @('mysql','sys','performance_schema','information_schema')) { throw 'Nome de banco da aplicacao invalido.' }
if ($appUser -notmatch '^[A-Za-z0-9_]{1,32}$' -or $appUser -eq 'root') { throw 'Use uma conta exclusiva da aplicacao, como production, em DB_USER.' }
if (!$appPassword -or $appPassword -match '^(CONFIGURE_|CHANGE_ME)') { throw 'Configure DB_PASSWORD no .env.' }
$mysql = (Get-Command mysql.exe -ErrorAction SilentlyContinue).Source
if (!$mysql) { $mysql = Join-Path $env:ProgramFiles 'MySQL\MySQL Server 8.0\bin\mysql.exe' }
if (!(Test-Path -LiteralPath $mysql)) { throw 'mysql.exe nao encontrado; instale o cliente MySQL 8.0 ou adicione sua pasta bin ao PATH.' }
$portNumber = if ($connection.Port -gt 0) { $connection.Port } else { 3306 }
Write-Host "Banco do projeto: $database | Usuario: $appUser | Porta: $portNumber"
if ($CheckOnly) { Write-Host 'Configuracao e sintaxe do assistente validadas.'; return }
Write-Host 'Entre com o administrador do MySQL. A senha nao sera exibida nem gravada.'
if (!$adminUser) { $adminUser = Read-Host 'Usuario administrador (Enter para root)' }
if (!$adminUser) { $adminUser = 'root' }
if ($null -eq $adminPassword) { $adminPassword = Read-Host "Senha MySQL de $adminUser" -AsSecureString }
$oldMysqlPassword = [Environment]::GetEnvironmentVariable('MYSQL_PWD','Process')
# NO_BACKSLASH_ESCAPES torna a citacao abaixo independente do modo padrao do servidor.
$escapedPassword = $appPassword.Replace("'", "''")
$sql = @"
SET SESSION sql_mode = 'NO_BACKSLASH_ESCAPES';
CREATE DATABASE IF NOT EXISTS $database CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
CREATE USER IF NOT EXISTS '$appUser'@'localhost' IDENTIFIED BY '$escapedPassword';
ALTER USER '$appUser'@'localhost' IDENTIFIED BY '$escapedPassword';
GRANT ALL PRIVILEGES ON $database.* TO '$appUser'@'localhost';
"@
try {
    $env:MYSQL_PWD = [Net.NetworkCredential]::new('', $adminPassword).Password
    $sql | & $mysql --no-defaults --protocol=TCP --host=$($connection.Host) --port=$portNumber --user=$adminUser --default-character-set=utf8mb4 --connect-timeout=5 --batch
    if ($LASTEXITCODE -ne 0) { throw 'O MySQL recusou a configuracao. Confira o usuario/senha do administrador e a mensagem acima.' }
    $env:MYSQL_PWD = $appPassword
    & $mysql --no-defaults --protocol=TCP --host=$($connection.Host) --port=$portNumber --user=$appUser --database=$database --connect-timeout=5 --batch '--execute=SELECT 1 AS connection_ok;'
    if ($LASTEXITCODE -ne 0) { throw 'Nao foi possivel confirmar a conexao da aplicacao.' }
} finally {
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $oldMysqlPassword, 'Process')
    $adminPassword.Dispose()
    $escapedPassword = $null
    $sql = $null
}
Write-Host 'Conexao confirmada. Iniciando a API...'
# O assistente usa o arquivo atual como fonte; evita configuracoes antigas neste terminal.
foreach ($key in $config.Keys) { [Environment]::SetEnvironmentVariable($key, $config[$key], 'Process') }
& (Join-Path $PSScriptRoot 'run-local.ps1')