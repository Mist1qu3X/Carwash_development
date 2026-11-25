#!/usr/bin/env bash
# Запуск PostgreSQL и backend (вместе с React-интерфейсом) через Docker Compose (macOS / Linux).
# Использование из корня проекта: ./backend/start-compose.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ENV_FILE="$PROJECT_ROOT/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Не найден $ENV_FILE. Скопируйте .env.example в .env и заполните значения." >&2
  exit 1
fi
if ! docker info >/dev/null 2>&1; then
  echo "Docker не запущен. Запустите Docker Desktop и повторите." >&2
  exit 1
fi

docker compose --env-file "$ENV_FILE" -f "$SCRIPT_DIR/docker-compose.yml" up -d --build --wait

PORT="$(grep -E '^APP_PORT=' "$ENV_FILE" | cut -d= -f2 || true)"
PORT="${PORT:-8080}"
echo
echo "Система запущена:"
echo "  Web-интерфейс  http://localhost:$PORT/"
echo "  Swagger        http://localhost:$PORT/swagger-ui.html (администратор, HTTP Basic)"
echo "  Health         http://localhost:$PORT/actuator/health"
