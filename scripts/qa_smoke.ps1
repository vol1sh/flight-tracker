$ErrorActionPreference = 'Continue'

if (-not (Test-Path '.\docker-compose.yml')) {
    Write-Error 'Перейдите в корень репозитория (там должен быть docker-compose.yml).'
    exit 1
}
if (-not (Get-Command 'docker' -ErrorAction SilentlyContinue)) {
    Write-Error 'Команда docker не найдена. Запустите Docker Desktop.'
    exit 1
}
if (-not (Get-Command 'curl.exe' -ErrorAction SilentlyContinue)) {
    Write-Error 'Команда curl.exe не найдена.'
    exit 1
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$evidenceDir = Join-Path (Get-Location).Path 'scripts'
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null
$reportPath = Join-Path $evidenceDir "smoke-$stamp.md"
$sha = ((git rev-parse HEAD 2>$null) -join '').Trim()
if (-not $sha) { $sha = 'unknown' }
$script:rows = @()

function Add-QaResult {
    param([string]$Id, [string]$Status, [string]$Description)
    $safe = $Description.Replace('|', '/').Replace("`r", ' ').Replace("`n", ' ')
    $script:rows += "| $Id | $Status | $safe |"
    Write-Host ("{0}: {1} — {2}" -f $Id, $Status, $Description)
}
function Check-Http {
    param([string]$Id, [string]$Url, [int]$Expected)
    # Сохраняем только HTTP-код; не выводим JSON с возможными приватными полями.
    $codeText = ((& curl.exe --silent --output NUL --write-out '%{http_code}' --max-time 15 $Url 2>$null) -join '').Trim()
    $actual = 0
    [void][int]::TryParse($codeText, [ref]$actual)
    $status = if ($actual -eq $Expected) { 'PASS' } else { 'FAIL' }
    Add-QaResult $Id $status "Ожидалось HTTP $Expected; получено HTTP $actual"
}

Write-Host "Flight Tracker QA smoke — SHA $sha"
Write-Host 'Проверка контейнеров (оценить healthchecks вручную):'
docker compose ps
Add-QaResult 'QA-ENV-01' 'MANUAL' 'Оценить docker compose ps: postgres/redis healthy, backend/frontend доступны'

Check-Http 'QA-ENV-02' 'http://localhost:8080/actuator/health' 200
Check-Http 'QA-ENV-03' 'http://localhost:3000/' 200
Check-Http 'QA-ENV-04' 'http://localhost:3000/api/v1/system/ping' 200
Check-Http 'QA-SEARCH-03' 'http://localhost:8080/api/v1/flights/SU100' 400
Check-Http 'QA-SEARCH-04' 'http://localhost:8080/api/v1/flights/SU100?date=27-10-2026' 400
Check-Http 'QA-SEARCH-05' 'http://localhost:8080/api/v1/flights/INVALID?date=2026-10-27' 400

# Проверка DEMO-A только на чтение; отсутствие заранее подготовленной записи — BLOCKED.
$demoUrl = 'http://localhost:8080/api/v1/flights/ZZ9999?date=2026-10-27'
try {
    $flight = Invoke-RestMethod -Method Get -Uri $demoUrl -TimeoutSec 15 -ErrorAction Stop
    $identityCorrect = ($flight.flightIata -eq 'ZZ9999') -and
        (("$($flight.flightDate)").StartsWith('2026-10-27')) -and
        ($flight.status -eq 'LANDED')
    if ($identityCorrect) {
        Add-QaResult 'QA-FALLBACK-03' 'PASS' 'DEMO-A: получена запись ZZ9999/2026-10-27 со статусом LANDED. Источник DB дополнительно подтвердить в логах.'
        Add-QaResult 'QA-SEARCH-01' 'MANUAL' 'HTTP-путь работает, теперь подтвердить UI+Network+SQL вручную.'
    } else {
        Add-QaResult 'QA-FALLBACK-03' 'FAIL' 'HTTP 200, но номер/дата/статус не совпадают с DEMO-A.'
        Add-QaResult 'QA-SEARCH-01' 'MANUAL' 'Сопоставить API ответ, UI и подготовленную QA-запись.'
    }
} catch {
    Add-QaResult 'QA-FALLBACK-03' 'BLOCKED' 'DEMO-A недоступен: сначала проверить, существует ли запись в тестовой БД.'
    Add-QaResult 'QA-SEARCH-01' 'BLOCKED' 'Отсутствует подтверждённый положительный ответ для DEMO-A.'
}

Write-Host 'Redis PING:'
$redisPing = ((docker compose exec -T redis redis-cli PING 2>$null) -join '').Trim()
if ($redisPing -eq 'PONG') {
    Add-QaResult 'QA-CACHE-01' 'MANUAL' 'Redis PONG; дополнительно проверить TTL существующего ключа (1..180 сек).'
} else {
    Add-QaResult 'QA-CACHE-01' 'FAIL' 'Redis PING не вернул PONG.'
}

$md = @(
    '# Отчёт первичной QA-проверки Flight Tracker',
    '',
    "- Дата: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')",
    "- Git SHA: $sha",
    '- Режим: безопасный smoke (только чтение, без внешних API вызовов напрямую)',
    '- Этот скрипт НЕ покрывает все 34 теста из MVP_TEST_MATRIX.md.',
    '',
    '| Тест | Статус | Фактический результат / следующий шаг |',
    '|---|---|---|'
) + $script:rows
$md | Set-Content -Path $reportPath -Encoding UTF8
Write-Host "`nОтчёт создан: $reportPath"
Write-Host 'Остальные кейсы прогоняются через JUnit, Vitest, DevTools и GitHub согласно инструкции.'

