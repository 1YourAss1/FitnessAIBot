package ru.yourass.fitnessaibot.ai;

import org.springframework.stereotype.Component;
import ru.yourass.fitnessaibot.entity.ActivityLevel;
import ru.yourass.fitnessaibot.entity.Gender;
import ru.yourass.fitnessaibot.entity.UserProfileEntity;
import ru.yourass.fitnessaibot.repository.UserProfileRepository;

/**
 * Собирает компактное текстовое представление профиля пользователя,
 * которое подмешивается в user-prompt. Также считает базовый обмен (BMR)
 * и суточный расход (TDEE) по формуле Миффлина-Сан Жеора и дописывает их
 * в строку профиля.
 *
 * <p>Формат строки — перечисляются ТОЛЬКО заполненные поля (пример):</p>
 * <pre>
 * profile: gender=MALE age=30 height=180 weight=80 bmr=1780 tdee=2759
 * </pre>
 *
 * <p>Пустой или отсутствующий профиль → {@code profile: empty}.</p>
 *
 * <p>Если профиль содержит все четыре поля для формулы (пол, возраст, рост, вес),
 * сюда же дописываются готовые {@code bmr=} и {@code tdee=}. TDEE считается с
 * уровнем активности из профиля, а если он не задан — с дефолтом
 * {@link ActivityLevel#MODERATE}.</p>
 */
@Component
public class ProfileContextBuilder {

    /** Уровень активности по умолчанию, когда в профиле поле activityLevel=null. */
    private static final ActivityLevel DEFAULT_ACTIVITY_LEVEL = ActivityLevel.MODERATE;

    private final UserProfileRepository userProfileRepository;

    public ProfileContextBuilder(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    /** Возвращает строку для подстановки в префикс user-prompt. */
    public String build(long telegramUserId) {
        UserProfileEntity profile = userProfileRepository
                .findByTelegramUserId(telegramUserId)
                .orElse(null);
        return render(profile);
    }

    static String render(UserProfileEntity profile) {
        if (profile == null) {
            return "profile: empty";
        }
        StringBuilder sb = new StringBuilder("profile:");
        appendIfPresent(sb, "gender", profile.getGender() == null ? null : profile.getGender().name());
        appendIfPresent(sb, "age", profile.getAge());
        appendIfPresent(sb, "height", profile.getHeightCm());
        appendIfPresent(sb, "weight", profile.getWeightKg());
        appendIfPresent(sb, "goal", profile.getGoalWeightKg());
        appendIfPresent(sb, "activity", profile.getActivityLevel() == null ? null : profile.getActivityLevel().name());
        appendBmrTdee(sb, profile);
        String result = sb.toString();
        if ("profile:".equals(result)) {
            return "profile: empty";
        }
        return result.strip();
    }

    private static void appendIfPresent(StringBuilder sb, String key, Object value) {
        if (value == null) {
            return;
        }
        sb.append(' ').append(key).append('=');
        if (value instanceof Double d) {
            // Целочисленные double печатаем без ".0" — 80.0 → "80", 80.5 → "80.5".
            sb.append(d == Math.floor(d) && !Double.isInfinite(d) ? Long.toString(d.longValue()) : d);
        } else {
            sb.append(value);
        }
    }

    /**
     * Считает BMR/TDEE и дописывает их в строку профиля, если есть все
     * четыре параметра. Если какого-то не хватает — просто ничего не
     * добавляем (это сигнал модели «профиль неполный, заполни»).
     */
    private static void appendBmrTdee(StringBuilder sb, UserProfileEntity profile) {
        if (profile == null || !profile.hasBmrInputs()) {
            return;
        }
        Gender gender = profile.getGender();
        int age = profile.getAge();
        double heightCm = profile.getHeightCm();
        double weightKg = profile.getWeightKg();

        double bmr = 10 * weightKg + 6.25 * heightCm - 5 * age
                + (gender == Gender.MALE ? 5 : -161);

        ActivityLevel level = profile.getActivityLevel() != null
                ? profile.getActivityLevel()
                : DEFAULT_ACTIVITY_LEVEL;
        double tdee = bmr * level.getFactor();

        appendIfPresent(sb, "bmr",Math.round(bmr * 10.0) / 10.0);
        appendIfPresent(sb, "tdee", Math.round(tdee * 10.0) / 10.0);
    }
}