package ru.yourass.fitnessaibot.health;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Настройки интеграции с Google Health API.
 *
 * <p>Все поля читаются из переменных окружения (см. {@code .env.example}):
 * <pre>
 *   GOOGLE_HEALTH_CLIENT_ID     OAuth client id (https://console.cloud.google.com)
 *   GOOGLE_HEALTH_CLIENT_SECRET OAuth client secret
 *   GOOGLE_HEALTH_REDIRECT_URI  https://your-host/google-health/oauth/callback
 *   GOOGLE_HEALTH_LOOKBACK_DAYS глубина окна первой синхронизации (дни)
 * </pre>
 *
 * <p>Скоупы OAuth соответствуют набору сущностей, которые мы сохраняем (вес, активность, сон).</p>
 *
 * <p>Расписание синхронизации захардкожено в {@link GoogleHealthScheduler}
 * (2 раза в сутки: 10:00 и 22:00 Europe/Moscow).</p>
 */
@Validated
@ConfigurationProperties(prefix = "google.health")
public record GoogleHealthProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        int lookbackDays
) {

    public GoogleHealthProperties {
        if (lookbackDays <= 0) {
            lookbackDays = 7;
        }
    }
}
