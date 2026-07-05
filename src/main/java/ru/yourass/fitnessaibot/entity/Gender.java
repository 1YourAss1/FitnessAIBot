package ru.yourass.fitnessaibot.entity;

/**
 * Пол пользователя Telegram.
 *
 * <p>Используется в {@link UserProfileEntity} и нужен агенту для более
 * точной оценки калорийности (например, разные MET-коэффициенты или
 * поправки на состав тела).</p>
 */
public enum Gender {
    MALE,
    FEMALE
}