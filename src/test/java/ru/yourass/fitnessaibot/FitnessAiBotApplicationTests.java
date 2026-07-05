package ru.yourass.fitnessaibot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke-тест: проверяет, что контекст Spring поднимается с тестовыми
 * фиктивными ключами. Реальные внешние вызовы (OpenAI, Telegram) не делаем —
 * Telegram-бот отключаем через fitness.bot.enabled=false.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "fitness.bot.enabled=false",
        "fitness.bot.username=test-bot",
        "fitness.bot.token=000000:TEST",
        "spring.ai.openai.api-key=sk-test"
})
class FitnessAiBotApplicationTests {

    @Test
    void contextLoads() {
    }
}
