package ru.yourass.fitnessaibot.ai;

import org.springframework.stereotype.Component;
import ru.yourass.fitnessaibot.entity.UserProfileEntity;
import ru.yourass.fitnessaibot.repository.UserProfileRepository;

/**
 * Собирает компактное текстовое представление профиля пользователя,
 * которое подмешивается в user-prompt.
 *
 * <p>Формат строки — перечисляются ТОЛЬКО заполненные поля (пример):</p>
 * <pre>
 * profile: gender=MALE age=30 height=180 weight=80
 * </pre>
 *
 * <p>Пустой или отсутствующий профиль → {@code profile: empty}.</p>
 */
@Component
public class ProfileContextBuilder {

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
            sb.append(formatDouble(d));
        } else {
            sb.append(value);
        }
    }

    private static String formatDouble(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return Long.toString((long) v);
        }
        return Double.toString(v);
    }
}
