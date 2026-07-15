package ru.yourass.fitnessaibot.entity;

import lombok.Getter;

/**
 * Уровень ежедневной активности пользователя по шкале Харриса-Бенедикта.
 *
 * <p>Используется в {@link UserProfileEntity} для расчёта TDEE:
 * {@code TDEE = BMR × factor}. Коэффициент отражает примерный суточный
 * расход энергии поверх базового обмена.</p>
 */
@Getter
public enum ActivityLevel {
    SEDENTARY(1.2),
    LIGHT(1.375),
    MODERATE(1.55),
    ACTIVE(1.725),
    VERY_ACTIVE(1.9);

    private final double factor;

    ActivityLevel(double factor) {
        this.factor = factor;
    }
}