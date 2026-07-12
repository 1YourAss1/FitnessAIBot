package ru.yourass.fitnessaibot.bot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.yourass.fitnessaibot.config.BotProperties;
import ru.yourass.fitnessaibot.entity.Gender;
import ru.yourass.fitnessaibot.entity.UserProfileEntity;
import ru.yourass.fitnessaibot.health.GoogleHealthOAuthService;
import ru.yourass.fitnessaibot.health.GoogleHealthSyncService;
import ru.yourass.fitnessaibot.repository.UserProfileRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Чистый unit-тест {@link FitnessBot#hasBmrProfile(long)} с моком
 * {@link UserProfileRepository}. Бот создаётся напрямую через конструктор —
 * Spring-контекст не поднимаем, {@code TelegramBot} остаётся {@code null}
 * (метод {@code hasBmrProfile} Telegram-клиент не использует).
 */
@ExtendWith(MockitoExtension.class)
class FitnessBotTest {

    @Mock
    private UserProfileRepository repo;

    @Mock
    private GoogleHealthOAuthService googleHealthOAuth;

    @Mock
    private GoogleHealthSyncService googleHealthSync;

    private FitnessBot bot;

    @BeforeEach
    void setUp() {
        bot = new FitnessBot(
                new BotProperties("test-bot", "000000:TEST", null),
                /* agent */ null,
                repo,
                googleHealthOAuth,
                googleHealthSync
        );
    }

    @Test
    void hasBmrProfile_falseWhenProfileMissing() {
        when(repo.findByTelegramUserId(1L)).thenReturn(Optional.empty());
        assertThat(bot.hasBmrProfile(1L)).isFalse();
    }

    @Test
    void hasBmrProfile_falseWhenOnlySomeFieldsPresent() {
        when(repo.findByTelegramUserId(2L)).thenReturn(Optional.of(
                new UserProfileEntity(2L, Gender.MALE, null, 180.0, 80.0)));
        assertThat(bot.hasBmrProfile(2L)).isFalse();
    }

    @Test
    void hasBmrProfile_trueWhenAllFieldsPresent() {
        when(repo.findByTelegramUserId(3L)).thenReturn(Optional.of(
                new UserProfileEntity(3L, Gender.MALE, 30, 180.0, 80.0)));
        assertThat(bot.hasBmrProfile(3L)).isTrue();
    }

    @Test
    void hasGoalWeight_falseWhenAbsent() {
        when(repo.findByTelegramUserId(4L)).thenReturn(Optional.of(
                new UserProfileEntity(4L, Gender.MALE, 30, 180.0, 80.0)));
        assertThat(bot.hasGoalWeight(4L)).isFalse();
    }

    @Test
    void hasGoalWeight_trueWhenSet() {
        when(repo.findByTelegramUserId(5L)).thenReturn(Optional.of(
                new UserProfileEntity(5L, Gender.MALE, 30, 180.0, 85.0, 75.0)));
        assertThat(bot.hasGoalWeight(5L)).isTrue();
    }

    @Test
    void hasGoalWeight_falseWhenProfileMissing() {
        when(repo.findByTelegramUserId(6L)).thenReturn(Optional.empty());
        assertThat(bot.hasGoalWeight(6L)).isFalse();
    }
}
