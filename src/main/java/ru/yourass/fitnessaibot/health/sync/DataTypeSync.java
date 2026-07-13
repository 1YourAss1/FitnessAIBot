package ru.yourass.fitnessaibot.health.sync;

import com.google.api.services.health.v4.model.DataPoint;
import com.google.api.services.health.v4.model.ObservationSampleTime;
import com.google.api.services.health.v4.model.ObservationTimeInterval;
import com.google.api.services.health.v4.model.SessionTimeInterval;
import lombok.extern.slf4j.Slf4j;
import ru.yourass.fitnessaibot.entity.EntryEntity;
import ru.yourass.fitnessaibot.entity.UserProfileEntity;
import ru.yourass.fitnessaibot.health.GoogleHealthSyncService;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * База для стратегий синхронизации одного типа данных Google Health →
 * БД. Каждая реализация — отдельный {@code @Component},
 * который {@link GoogleHealthSyncService} итерирует через
 * {@code List<DataTypeSync>}.
 *
 * <p>Содержит общие хелперы для парсинга timestamp-полей из моделей
 * Google Health API и построения стабильного {@code sourceMessage} для
 * дедупликации.</p>
 */
@Slf4j
public abstract class DataTypeSync {

    /** Префикс {@code sourceMessage} для записей, импортированных из Google Health. */
    protected static final String SOURCE_PREFIX = "GoogleHealth:";

    /** URL-путь в kebab-case, например {@code "users/me/dataTypes/weight"}. */
    public abstract String dataTypePath();

    /** Имя типа для filter в snake_case, например {@code "weight"}. */
    public abstract String dataTypeForFilter();

    /**
     * Поле времени для filter
     */
    public abstract String filterTimeField();

    /** Максимум записей на страницу: 1000 для weight/activity, 25 для sleep. */
    public abstract int pageSize();

    /**
     * Сохранить {@link DataPoint} в локальный журнал.
     *
     * @return {@code true}, если запись добавлена; {@code false}, если дубль
     *         или данные невалидны
     */
    public abstract boolean saveDataPoint(long userId, DataPoint dataPoint, UserProfileEntity profile);

    // ==================== helpers ====================

    /**
     * Формат значения фильтра для данного data type.
     * По умолчанию — ISO-8601 ({@code Instant#toString()}), что подходит
     * для полей {@code start_time}, {@code end_time}, {@code create_time},
     * {@code update_time}, {@code interval.start_time} и т.п.
     *
     * <p>Для полей типа {@code CivilDateTime} (например,
     * {@code interval.civil_start_time}) формат должен быть локальным
     * без таймзоны и без наносекунд — реализации переопределяют.</p>
     */
    public String formatFilterTimestamp(Instant ts) {
        return ts.toString();
    }

    /** Parse {@code physical_time} из {@link ObservationSampleTime}. */
    static OffsetDateTime parseOffset(ObservationSampleTime sampleTime) {
        return sampleTime == null || sampleTime.getPhysicalTime() == null
                ? null : OffsetDateTime.parse(sampleTime.getPhysicalTime());
    }

    /** Parse {@code start_time} из {@link ObservationTimeInterval}. */
    static OffsetDateTime parseOffset(ObservationTimeInterval interval) {
        return interval == null || interval.getStartTime() == null
                ? null : OffsetDateTime.parse(interval.getStartTime());
    }

    /** Parse {@code end_time} из {@link ObservationTimeInterval}. */
    static OffsetDateTime parseEndOffset(ObservationTimeInterval interval) {
        return interval == null || interval.getEndTime() == null
                ? null : OffsetDateTime.parse(interval.getEndTime());
    }

    /** Parse {@code end_time} из {@link SessionTimeInterval}. */
    static OffsetDateTime parseEnd(SessionTimeInterval interval) {
        return interval == null || interval.getEndTime() == null
                ? null : OffsetDateTime.parse(interval.getEndTime());
    }

    /** Parse {@code start_time} из {@link SessionTimeInterval}. */
    static OffsetDateTime parseStart(SessionTimeInterval interval) {
        return interval == null || interval.getStartTime() == null
                ? null : OffsetDateTime.parse(interval.getStartTime());
    }

    /** Запись создана/обновлена через Google Health Sync. */
    protected static boolean isFromGoogle(EntryEntity e) {
        String src = e.getSourceMessage();
        return src != null && src.startsWith(SOURCE_PREFIX);
    }

    /** Границы календарного дня (UTC) для поиска похожих записей: {@code [dayStart, dayEnd]}. */
    protected static OffsetDateTime[] dayBounds(OffsetDateTime when) {
        LocalDate day = when.toLocalDate();
        return new OffsetDateTime[]{
                day.atStartOfDay().atOffset(ZoneOffset.UTC),
                day.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC).minusNanos(1)
        };
    }

    /**
     * {@code true}, если {@code actual} отличается от {@code expected} не более чем
     * на {@code tolerance * 100}% (или на абсолютную дельту, если ожидание = 0).
     */
    protected static boolean within(double actual, double expected, double tolerance) {
        if (expected == 0.0) return Math.abs(actual) <= tolerance;
        return Math.abs(actual - expected) <= Math.abs(expected) * tolerance;
    }

    /**
     * Длительность в минутах из ISO-8601 {@code activeDuration} (например {@code PT1800S} = 30 мин).
     *
     * @return минуты (минимум 1) или 0 если строка пустая/невалидная
     */
    protected static int exerciseDurationMinutes(String activeDuration) {
        if (activeDuration == null || activeDuration.isBlank()) return 0;
        try {
            String formattedDuration = activeDuration.trim().toUpperCase();
            if (!formattedDuration.startsWith("P")) {
                formattedDuration = "PT" + formattedDuration;
            }
            long seconds = Duration.parse(formattedDuration).getSeconds();
            return seconds > 0 ? Math.max(1, (int) Math.round(seconds / 60.0)) : 0;
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return 0;
        }
    }

}