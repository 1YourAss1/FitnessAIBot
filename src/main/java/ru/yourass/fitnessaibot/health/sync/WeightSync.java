package ru.yourass.fitnessaibot.health.sync;

import com.google.api.services.health.v4.model.DataPoint;
import com.google.api.services.health.v4.model.Weight;
import org.springframework.stereotype.Component;
import ru.yourass.fitnessaibot.entity.UserProfileEntity;
import ru.yourass.fitnessaibot.entity.WeightEntryEntity;
import ru.yourass.fitnessaibot.repository.WeightEntryRepository;

import java.time.OffsetDateTime;

/**
 * Импорт веса из Google Health: {@code users/me/dataTypes/weight}.
 * Одна запись на день, UPSERT: если у пользователя уже есть запись за этот
 * день — обновляем её; иначе создаём новую.
 */
@Component
public class WeightSync extends DataTypeSync {

    private final WeightEntryRepository weightRepository;

    public WeightSync(WeightEntryRepository weightRepository) {
        this.weightRepository = weightRepository;
    }

    @Override public String dataTypePath() { return "users/me/dataTypes/weight"; }
    @Override public String dataTypeForFilter() { return "weight"; }
    @Override public String filterTimeField() { return "sample_time.physical_time"; }
    @Override public int pageSize() { return 1000; }

    @Override
    public boolean saveDataPoint(long userId, DataPoint dataPoint, UserProfileEntity profile) {
        // Достаём вес и базовые проверки (граммы, разумный диапазон кг)
        Weight w = dataPoint.getWeight();
        if (w == null || w.getWeightGrams() == null) return false;
        double kg = w.getWeightGrams() / 1000.0;
        if (kg <= 0 || kg > 500) return false;

        // Парсим время измерения из API
        OffsetDateTime recordedAt = parseOffset(w.getSampleTime());
        if (recordedAt == null) return false;

        // UPSERT по дню: одна запись на календарный день — если уже есть, обновим
        OffsetDateTime[] bounds = dayBounds(recordedAt);
        WeightEntryEntity entity = weightRepository
                .findByTelegramUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(
                        userId, bounds[0], bounds[1])
                .stream().findFirst().orElse(null);

        if (entity == null) {
            entity = new WeightEntryEntity(userId, kg);
            entity.setName("Вес (Google Health)");
        } else {
            entity.setKilograms(kg);
        }
        entity.setRecordedAt(recordedAt);
        entity.setSourceMessage(SOURCE_PREFIX + dataPoint.getName());
        weightRepository.save(entity);

        // Держим profile.weightKg = kg самой свежей записи (ручной или Google)
        weightRepository.findFirstByTelegramUserIdOrderByRecordedAtDesc(userId)
                .ifPresent(latest -> {
                    if (profile.getWeightKg() == null
                            || !profile.getWeightKg().equals(latest.getKilograms())) {
                        profile.setWeightKg(latest.getKilograms());
                    }
                });
        return true;
    }
}