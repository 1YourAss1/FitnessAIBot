package ru.yourass.fitnessaibot.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Настройки Telegram-бота.
 *
 * <p>{@code url} — необязательная ссылка на бота.
 * Если задана, на странице OAuth-callback'а Google показывается кнопка-ссылка.
 */
@Validated
@ConfigurationProperties(prefix = "fitness.bot")
public record BotProperties(
        @NotBlank String username,
        @NotBlank String token,
        String url,
        boolean streamReplyEnabled
) {
}
