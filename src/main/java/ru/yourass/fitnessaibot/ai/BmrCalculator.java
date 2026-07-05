package ru.yourass.fitnessaibot.ai;

import lombok.Getter;
import org.springframework.stereotype.Component;
import ru.yourass.fitnessaibot.entity.Gender;
import ru.yourass.fitnessaibot.entity.UserProfileEntity;

/**
 * Расчёт энергетических показателей пользователя по его профилю.
 *
 * <p>Используется формула Миффлина-Сан Жеора для базового обмена веществ
 * (BMR, Basal Metabolic Rate):</p>
 * <ul>
 *   <li>мужчины: 10 × вес(кг) + 6.25 × рост(см) − 5 × возраст(лет) + 5</li>
 *   <li>женщины: 10 × вес(кг) + 6.25 × рост(см) − 5 × возраст(лет) − 161</li>
 * </ul>
 *
 * <p>TDEE (Total Daily Energy Expenditure) = BMR × коэффициент активности
 * по шкале Харриса-Бенедикта (1.2 — сидячий, 1.375 — лёгкая активность 1-3
 * р/нед, 1.55 — умеренная 3-5 р/нед, 1.725 — активная 6-7 р/нед,
 * 1.9 — очень активная / спорт ежедневно).</p>
 */
@Component
public class BmrCalculator {

    /** Коэффициенты активности (Harris-Benedict). */
    @Getter
    public enum ActivityLevel {
        SEDENTARY(1.2, "сидячий образ жизни"),
        LIGHT(1.375, "лёгкая активность 1-3 раза в неделю"),
        MODERATE(1.55, "умеренная активность 3-5 раз в неделю"),
        ACTIVE(1.725, "высокая активность 6-7 раз в неделю"),
        VERY_ACTIVE(1.9, "очень высокая активность / спорт ежедневно");

        private final double factor;
        private final String description;

        ActivityLevel(double factor, String description) {
            this.factor = factor;
            this.description = description;
        }

    }

    /** DTO с результатом расчёта. */
    public record BmrResult(double bmrKcal, double tdeeKcal, ActivityLevel activityLevel) {
    }

    /**
     * Считает BMR и TDEE для пользователя. Если хотя бы один из параметров
     * (пол, возраст, рост, вес) неизвестен — бросает
     * {@link IllegalStateException} с понятным сообщением.
     */
    public BmrResult calculate(UserProfileEntity profile, ActivityLevel level) {
        if (profile == null) {
            throw new IllegalStateException("Профиль пользователя отсутствует");
        }
        Gender gender = profile.getGender();
        Integer age = profile.getAge();
        Double heightCm = profile.getHeightCm();
        Double weightKg = profile.getWeightKg();

        if (gender == null) {
            throw new IllegalStateException("Неизвестен пол пользователя");
        }
        if (age == null || age <= 0) {
            throw new IllegalStateException("Неизвестен возраст пользователя");
        }
        if (heightCm == null || heightCm <= 0) {
            throw new IllegalStateException("Неизвестен рост пользователя");
        }
        if (weightKg == null || weightKg <= 0) {
            throw new IllegalStateException("Неизвестен вес пользователя");
        }

        double bmr = 10 * weightKg + 6.25 * heightCm - 5 * age
                + (gender == Gender.MALE ? 5 : -161);
        double tdee = bmr * level.getFactor();
        return new BmrResult(round(bmr), round(tdee), level);
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
