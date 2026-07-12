# FitnessAIBot

Telegram-бот для учёта еды, активности, сна и веса на базе **Spring AI Tool Calling** (OpenAI-совместимый провайдер).

## Переменные окружения

Все секреты и настройки берутся из ENV или из `.env` (Spring Boot подхватывает автоматически через `spring.config.import=optional:file:.env[.properties]`).

| Переменная | Назначение | Дефолт |
|---|---|---|
| `FITNESS_BOT_TOKEN` | токен Telegram-бота от @BotFather | — |
| `FITNESS_BOT_USERNAME` | имя бота (для логов/UI) | `FitnessAIBot` |
| `FITNESS_BOT_ENABLED` | вкл/выкл long-polling | `true` |
| `FITNESS_TELEGRAM_CHANNEL_URL` | ссылка на Telegram-канал проекта; пусто — не показывается в OAuth-callback | — |
| `OPENAI_API_KEY` | ключ OpenAI-совместимого провайдера | — |
| `OPENAI_BASE_URL` | базовый URL провайдера (без `/v1`) | `https://api.openai.com` |
| `OPENAI_MODEL` | модель чата | `gpt-4o-mini` |
| `OPENAI_TEMPERATURE` | температура сэмплинга | `0.2` |
| `DB_URL` | JDBC-URL Postgres | `jdbc:postgresql://localhost:5432/fitnessaibot` |
| `DB_USERNAME` | пользователь БД | `postgres` |
| `DB_PASSWORD` | пароль БД | `postgres` |
| `GOOGLE_HEALTH_CLIENT_ID` | OAuth client id для Google Health | — |
| `GOOGLE_HEALTH_CLIENT_SECRET` | OAuth client secret | — |
| `GOOGLE_HEALTH_REDIRECT_URI` | callback URL (должен быть в Google Console Authorized redirect URIs) | `http://localhost:8080/google-health/oauth/callback` |
| `GOOGLE_HEALTH_LOOKBACK_DAYS` | глубина окна первой синхронизации | `7` |
| `TZ` | тайм-зона JVM (POSIX TZ) — влияет на логи и `recordedAt` | `Europe/Moscow` |

Шаблон — в [`.env.example`](.env.example:1).

## Запуск

```bash
cp .env.example .env          # заполнить FITNESS_BOT_TOKEN и OPENAI_API_KEY
docker compose up --build     # поднимет Postgres + бота одной командой
```

Без Docker:

```bash
./mvnw spring-boot:run        # Postgres должен быть запущен локально
```
