# Одна команда для Windows PowerShell: .\scripts\start.ps1
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
  throw 'Docker не найден. Установите и запустите Docker Desktop.'
}

function New-RandomHex([int]$bytes) {
  $buffer = New-Object byte[] $bytes
  [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($buffer)
  -join ($buffer | ForEach-Object { $_.ToString('x2') })
}

$hadEnv = Test-Path '.env'
# В Windows PowerShell 5.1 stderr внешней команды при 'Stop' превращается в ошибку — временно ослабляем.
$ErrorActionPreference = 'Continue'
docker volume inspect quiet-clinic_quiet-clinic-postgres *> $null
$volumeExists = ($LASTEXITCODE -eq 0)
$ErrorActionPreference = 'Stop'

if (-not $hadEnv) {
  $lines = (Get-Content '.env.example' -Encoding UTF8) `
    -replace '^POSTGRES_PASSWORD=.*', "POSTGRES_PASSWORD=$(New-RandomHex 24)" `
    -replace '^CLINIC_KMS_KEY=.*', "CLINIC_KMS_KEY=$(New-RandomHex 32)"
  # WriteAllLines пишет UTF-8 без BOM: docker compose читает такой файл корректно.
  [System.IO.File]::WriteAllLines((Join-Path (Get-Location) '.env'), $lines)
  Write-Host 'Создан .env со случайным паролем БД и ключом шифрования. Сохраните его копию в надёжном месте.'
}

if (-not $hadEnv -and $volumeExists) {
  Write-Host ''
  Write-Host 'Найдена база от прошлых запусков (том quiet-clinic_quiet-clinic-postgres).' -ForegroundColor Yellow
  Write-Host 'Её пароль и ключ шифрования не совпадают с только что созданным .env, backend не подключится.'
  Write-Host '  • Данные не нужны:  docker compose down -v   и снова .\scripts\start.ps1'
  Write-Host '  • Данные нужны:     впишите в .env старые POSTGRES_PASSWORD и CLINIC_KMS_KEY'
  exit 1
}

Write-Host 'Сборка и запуск (первый раз — несколько минут)...'
docker compose up -d --build --wait --wait-timeout 300
if ($LASTEXITCODE -ne 0) {
  Write-Host ''
  Write-Host 'Запуск не удался. Последние строки логов backend:' -ForegroundColor Red
  docker compose ps -a
  docker compose logs --no-color --tail 80 backend
  exit 1
}

$match = Select-String -Path '.env' -Pattern '^APP_PORT=(\d+)'
$port = if ($match) { $match.Matches[0].Groups[1].Value } else { '8080' }
Write-Host ''
Write-Host "Готово: http://localhost:$port"
Write-Host 'Логи:      docker compose logs -f backend'
Write-Host 'Остановка: docker compose down'
