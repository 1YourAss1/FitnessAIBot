package ru.yourass.fitnessaibot.health.sync;

import com.google.api.services.health.v4.model.DataPoint;
import com.google.api.services.health.v4.model.SessionTimeInterval;
import com.google.api.services.health.v4.model.Sleep;
import org.springframework.stereotype.Component;
import ru.yourass.fitnessaibot.entity.SleepEntryEntity;
import ru.yourass.fitnessaibot.entity.UserProfileEntity;
import ru.yourass.fitnessaibot.repository.SleepEntryRepository;

import java.time.OffsetDateTime;

/**
 * Импорт сна из Google Health: {@code users/me/dataTypes/sleep}.
 * Длительность из {@code Sleep.summary.minutesAsleep} (Google сам считает
 * с учётом стадий).
 */
@Component
public class SleepSync extends DataTypeSync {

    private final SleepEntryRepository sleepRepository;

    public SleepSync(SleepEntryRepository sleepRepository) {
        this.sleepRepository = sleepRepository;
    }

    @Override public String dataTypePath() { return "users/me/dataTypes/sleep"; }
    @Override public String dataTypeForFilter() { return "sleep"; }
    @Override public String filterTimeField() { return "interval.end_time"; }
    @Override public int pageSize() { return 25; }

    @Override
    public boolean saveDataPoint(long userId, DataPoint dataPoint, UserProfileEntity profile) {
        // Валидация: summary с minutesAsleep > 0 и есть интервал сессии
        Sleep s = dataPoint.getSleep();
        if (s == null || s.getSummary() == null) return false;
        Long minutesAsleep = s.getSummary().getMinutesAsleep();
        if (minutesAsleep == null || minutesAsleep <= 0) return false;

        // Конец сессии сна — это и есть recordedAt
        SessionTimeInterval interval = s.getInterval();
        if (interval == null) return false;
        OffsetDateTime endAt = parseEnd(interval);
        if (endAt == null) return false;

        // Ищем ручную запись сна за тот же день с похожей длительностью (±1 час)
        double hours = minutesAsleep / 60.0;
        OffsetDateTime[] bounds = dayBounds(endAt);
        SleepEntryEntity similar = sleepRepository
                .findByTelegramUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(
                        userId, bounds[0], bounds[1])
                .stream()
                .filter(e -> !isFromGoogle(e))
                .filter(e -> e.getHours() != null && Math.abs(e.getHours() - hours) <= 1.0)
                .findFirst()
                .orElse(null);

        // Нашли похожую ручную — перезаписываем её данными с Google
        if (similar != null) {
            similar.setHours(hours);
            similar.setRecordedAt(endAt);
            similar.setSourceMessage(SOURCE_PREFIX + dataPoint.getName());
            sleepRepository.save(similar);
            return true;
        }

        // Похожей ручной нет — dedup по sourceMessage и создаём новую
        String source = dataPoint.getName();
        if (source == null || source.isBlank()) return false;
        String fullSource = SOURCE_PREFIX + source;
        if (sleepRepository.existsByTelegramUserIdAndSourceMessage(userId, fullSource)) return false;

        SleepEntryEntity entity = new SleepEntryEntity(userId, hours);
        entity.setRecordedAt(endAt);
        entity.setSourceMessage(fullSource);
        sleepRepository.save(entity);
        return true;
    }
}