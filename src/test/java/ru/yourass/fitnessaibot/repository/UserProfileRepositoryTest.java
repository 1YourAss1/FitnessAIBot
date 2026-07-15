package ru.yourass.fitnessaibot.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.yourass.fitnessaibot.entity.ActivityLevel;
import ru.yourass.fitnessaibot.entity.Gender;
import ru.yourass.fitnessaibot.entity.UserProfileEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Юнит-тесты default-методов {@link UserProfileRepository}: {@code hasBmrProfile},
 * {@code hasGoalWeight}, {@code hasActivityInProfile}. Спринг-контекст не поднимается —
 * через {@code Mockito.mock(..., CALLS_REAL_METHODS)} создаётся мок, у которого
 * default-методы реально выполняются, а {@code findByTelegramUserId} подменяется
 * стабом.
 */
class UserProfileRepositoryTest {

    private UserProfileRepository repo;

    @BeforeEach
    void setUp() {
        repo = mock(UserProfileRepository.class, CALLS_REAL_METHODS);
    }

    // ==================== hasBmrProfile ====================

    @Test
    void hasBmrProfile_falseWhenProfileMissing() {
        when(repo.findByTelegramUserId(1L)).thenReturn(Optional.empty());
        assertThat(repo.hasBmrProfile(1L)).isFalse();
    }

    @Test
    void hasBmrProfile_falseWhenOnlySomeFieldsPresent() {
        when(repo.findByTelegramUserId(2L)).thenReturn(Optional.of(
                new UserProfileEntity(2L, Gender.MALE, null, 180.0, 80.0)));
        assertThat(repo.hasBmrProfile(2L)).isFalse();
    }

    @Test
    void hasBmrProfile_trueWhenAllFieldsPresent() {
        when(repo.findByTelegramUserId(3L)).thenReturn(Optional.of(
                new UserProfileEntity(3L, Gender.MALE, 30, 180.0, 80.0)));
        assertThat(repo.hasBmrProfile(3L)).isTrue();
    }

    // ==================== hasGoalWeight ====================

    @Test
    void hasGoalWeight_falseWhenAbsent() {
        when(repo.findByTelegramUserId(4L)).thenReturn(Optional.of(
                new UserProfileEntity(4L, Gender.MALE, 30, 180.0, 80.0)));
        assertThat(repo.hasGoalWeight(4L)).isFalse();
    }

    @Test
    void hasGoalWeight_trueWhenSet() {
        when(repo.findByTelegramUserId(5L)).thenReturn(Optional.of(
                new UserProfileEntity(5L, Gender.MALE, 30, 180.0, 85.0, 75.0)));
        assertThat(repo.hasGoalWeight(5L)).isTrue();
    }

    @Test
    void hasGoalWeight_falseWhenProfileMissing() {
        when(repo.findByTelegramUserId(6L)).thenReturn(Optional.empty());
        assertThat(repo.hasGoalWeight(6L)).isFalse();
    }

    // ==================== hasActivityInProfile ====================

    @Test
    void hasActivityInProfile_falseWhenAbsent() {
        when(repo.findByTelegramUserId(7L)).thenReturn(Optional.of(
                new UserProfileEntity(7L, Gender.MALE, 30, 180.0, 80.0)));
        assertThat(repo.hasActivityInProfile(7L)).isFalse();
    }

    @Test
    void hasActivityInProfile_trueWhenSet() {
        when(repo.findByTelegramUserId(8L)).thenReturn(Optional.of(
                new UserProfileEntity(8L, Gender.MALE, 30, 180.0, 80.0, null,
                        ActivityLevel.MODERATE)));
        assertThat(repo.hasActivityInProfile(8L)).isTrue();
    }

    @Test
    void hasActivityInProfile_falseWhenProfileMissing() {
        when(repo.findByTelegramUserId(9L)).thenReturn(Optional.empty());
        assertThat(repo.hasActivityInProfile(9L)).isFalse();
    }
}