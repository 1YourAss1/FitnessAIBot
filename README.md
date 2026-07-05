# FitnessAIBot

Telegram-бот для учёта еды, активности, сна и веса. Принимает свободный текст
от пользователя, пропускает через **Spring AI Tool Calling** (OpenAI-совместимый
чат-клиент) — модель сама решает, вызвать ли `@Tool save…` для записи или
`@Tool read…` для ответа на вопрос по журналу.

Текущий вес пользователя хранится в `UserProfileEntity` и подставляется агенту
для расчёта калорий по формуле `MET × вес_кг × часы`.

## Переменные окружения

Все секреты и настройки берутся из ENV (или из `.env`, который Spring Boot
подхватывает автоматически через `spring.config.import=optional:file:.env[.properties]`).

| Переменная | Назначение | Дефолт |
|---|---|---|
| `FITNESS_BOT_TOKEN` | токен Telegram-бота от @BotFather | — |
| `FITNESS_BOT_USERNAME` | имя бота (для логов/UI) | `FitnessAIBot` |
| `FITNESS_BOT_ENABLED` | вкл/выкл long-polling | `true` |
| `OPENAI_API_KEY` | ключ OpenAI-совместимого провайдера | — |
| `OPENAI_BASE_URL` | базовый URL провайдера (без `/v1`) | `https://api.openai.com` |
| `OPENAI_MODEL` | модель чата | `gpt-4o-mini` |
| `OPENAI_TEMPERATURE` | температура сэмплинга | `0.2` |
| `DB_URL` | JDBC-URL Postgres | `jdbc:postgresql://localhost:5432/fitnessaibot` |
| `DB_USERNAME` | пользователь БД | `postgres` |
| `DB_PASSWORD` | пароль БД | `postgres` |

Шаблон для копирования — в [`.env.example`](.env.example:1).

## Запуск

```bash
cp .env.example .env          # заполнить FITNESS_BOT_TOKEN и OPENAI_API_KEY
docker compose up --build     # поднимет Postgres + бота одной командой
```

Без Docker:

```bash
./mvnw spring-boot:run        # Postgres должен быть запущен локально
```
