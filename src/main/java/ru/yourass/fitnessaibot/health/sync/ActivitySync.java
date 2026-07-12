package ru.yourass.fitnessaibot.health.sync;

import com.google.api.services.health.v4.model.DataPoint;
import com.google.api.services.health.v4.model.Exercise;
import com.google.api.services.health.v4.model.SessionTimeInterval;
import org.springframework.stereotype.Component;
import ru.yourass.fitnessaibot.entity.ActivityEntryEntity;
import ru.yourass.fitnessaibot.entity.UserProfileEntity;
import ru.yourass.fitnessaibot.repository.ActivityEntryRepository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Импорт тренировок из Google Health: {@code users/me/dataTypes/exercise}.
 *
 * <p>Сюда попадают реальные тренировки с Pixel Watch / Fitbit / Garmin и т.п.
 * Каждая сессия = отдельная запись, dedupe по уникальному
 * {@link DataPoint#getName()} (Google гарантирует уникальность).</p>
 */
@Component
public class ActivitySync extends DataTypeSync {

    private final ActivityEntryRepository activityRepository;

    public ActivitySync(ActivityEntryRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    @Override public String dataTypePath() { return "users/me/dataTypes/exercise"; }
    @Override public String dataTypeForFilter() { return "exercise"; }
    @Override public String filterTimeField() { return "interval.civil_start_time"; }
    @Override public int pageSize() { return 1000; }

    /**
     * {@code interval.civil_start_time} имеет тип {@code CivilDateTime} — без таймзоны,
     * без наносекунд. Отправляем в TZ JVM (читается из {@code TZ} env, по умолчанию
     * {@code Europe/Moscow}), формат {@code yyyy-MM-ddTHH:mm:ss}.
     */
    private static final DateTimeFormatter CIVIL_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneId.systemDefault());

    @Override
    public String formatFilterTimestamp(Instant ts) {
        return CIVIL_FORMATTER.format(ts);
    }

    @Override
    public boolean saveDataPoint(long userId, DataPoint dataPoint, UserProfileEntity profile) {
        // Достаём Exercise и базовые проверки (есть interval и старт)
        Exercise e = dataPoint.getExercise();
        if (e == null) return false;

        SessionTimeInterval interval = e.getInterval();
        if (interval == null) return false;
        OffsetDateTime startAt = parseStart(interval);
        if (startAt == null) return false;

        // Длительность из ISO-8601 activeDuration (например "PT1800S" = 30 мин)
        int duration = exerciseDurationMinutes(e.getActiveDuration());
        if (duration <= 0) return false;

        // Калории из metricsSummary; если часы не прислали — записываем 0
        Double kcal = e.getMetricsSummary() == null ? null : e.getMetricsSummary().getCaloriesKcal();
        int caloriesBurned = (kcal != null && kcal > 0) ? (int) Math.round(kcal) : 0;

        // Имя активности: displayName от Google, иначе fallback
        String name = e.getDisplayName();
        if (name == null || name.isBlank()) name = "Активность (Google Health)";

        // Ищем похожую ручную запись за тот же день (±20% длительности — калории могут расходиться)
        OffsetDateTime[] bounds = dayBounds(startAt);
        ActivityEntryEntity similar = activityRepository
                .findByTelegramUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(
                        userId, bounds[0], bounds[1])
                .stream()
                .filter(x -> !isFromGoogle(x))
                .filter(x -> {
                    Integer dm = x.getDurationMinutes();
                    return dm != null && within(dm.doubleValue(), duration, 0.20);
                })
                .findFirst()
                .orElse(null);

        // Нашли похожую ручную — перезаписываем её данными с датчиков (Google точнее)
        if (similar != null) {
            similar.setName(name);
            similar.setDurationMinutes(duration);
            similar.setCaloriesBurned(caloriesBurned);
            similar.setRecordedAt(startAt);
            similar.setSourceMessage(SOURCE_PREFIX + dataPoint.getName());
            activityRepository.save(similar);
            return true;
        }

        // Похожей ручной нет — dedup по sourceMessage (защита от дубля самого Google) и создаём новую
        String source = dataPoint.getName();
        if (source == null || source.isBlank()) return false;
        if (activityRepository.existsByTelegramUserIdAndSourceMessage(userId, source)) return false;

        ActivityEntryEntity entity = new ActivityEntryEntity(
                userId, name, duration, caloriesBurned);
        entity.setRecordedAt(startAt);
        entity.setSourceMessage(SOURCE_PREFIX + source);
        activityRepository.save(entity);
        return true;
    }
}