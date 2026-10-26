#!/usr/bin/env bash
# #56: проверка ключа AviationStack и возможностей текущего тарифа (HTTPS, фильтр flight_date).
# Ключ берётся из переменной AVIATION_API_KEY или из файла .env в корне проекта и НИКОГДА не выводится.
# Делает 3 запроса — они расходуют месячную квоту тарифа.
#
# Запуск из корня проекта (Git Bash / Linux / macOS):
#   bash scripts/check-aviationstack.sh [НОМЕР_РЕЙСА]

set -u

FLIGHT="${1:-SU100}"
KEY="${AVIATION_API_KEY:-}"
if [ -z "$KEY" ] && [ -f .env ]; then
  KEY="$(grep -E '^AVIATION_API_KEY=' .env | head -n1 | cut -d= -f2- | tr -d '\r" ')"
fi
if [ -z "$KEY" ]; then
  echo "ОШИБКА: ключ не найден. Задайте AVIATION_API_KEY или заполните его в .env"
  exit 1
fi

TODAY="$(date -u +%Y-%m-%d)"

# Выполняет запрос и печатает "HTTP_CODE|ERROR_CODE" (ERROR_CODE пустой, если ошибки нет).
probe() {
  local url="$1" body http err
  body="$(curl -sS -m 20 -w '\n%{http_code}' "$url" 2>/dev/null)"
  http="$(printf '%s' "$body" | tail -n1)"
  err="$(printf '%s' "$body" | sed '$d' | grep -o '"code" *: *"[^"]*"' | head -n1 | sed 's/.*"\([^"]*\)"$/\1/')"
  printf '%s|%s' "$http" "$err"
}

verdict() {
  local name="$1" result="$2" http err
  http="${result%%|*}"; err="${result#*|}"
  if [ "$http" = "200" ] && [ -z "$err" ]; then
    echo "  [OK]   $name"
  else
    echo "  [FAIL] $name — HTTP ${http:-нет ответа}${err:+, код ошибки: $err}"
  fi
}

echo "Проверка AviationStack для рейса $FLIGHT (дата $TODAY)..."

R_HTTPS="$(probe "https://api.aviationstack.com/v1/flights?access_key=$KEY&flight_iata=$FLIGHT&limit=1")"
R_HTTP="$(probe "http://api.aviationstack.com/v1/flights?access_key=$KEY&flight_iata=$FLIGHT&limit=1")"
R_DATE="$(probe "https://api.aviationstack.com/v1/flights?access_key=$KEY&flight_iata=$FLIGHT&flight_date=$TODAY&limit=1")"

verdict "HTTPS-запрос (то, что использует приложение)" "$R_HTTPS"
verdict "HTTP-запрос (для сравнения)"                  "$R_HTTP"
verdict "Фильтр flight_date по HTTPS"                  "$R_DATE"

echo
case "${R_HTTPS#*|}${R_HTTP#*|}" in
  *invalid_access_key*|*missing_access_key*|*inactive_user*)
    echo "Итог: ключ недействителен или не активирован — замените его в .env." ;;
  *usage_limit*|*rate_limit*)
    echo "Итог: квота тарифа исчерпана — дождитесь сброса или смените тариф." ;;
  *)
    if [ "${R_HTTPS#*|}" = "https_access_restricted" ]; then
      echo "Итог: ключ рабочий, но тариф НЕ поддерживает HTTPS. Приложение будет отвечать 503."
      echo "      Нужен тариф с HTTPS (или временно AVIATION_API_BASE_URL=http://..., ключ уйдёт открытым текстом)."
    elif [ "${R_DATE#*|}" = "function_access_restricted" ]; then
      echo "Итог: ключ и HTTPS работают; фильтр flight_date на тарифе недоступен."
      echo "      Клиент сам ищет нужную дату в ответе без фильтра; если её там нет — AviationAccessException (не 404)."
    elif [ "${R_HTTPS%%|*}" = "200" ] && [ -z "${R_HTTPS#*|}" ]; then
      echo "Итог: ключ, HTTPS и фильтр по дате работают."
    else
      echo "Итог: не удалось однозначно определить — см. строки выше."
    fi ;;
esac
