package ru.yourass.fitnessaibot.repository;

import ru.yourass.fitnessaibot.entity.ActivityEntryEntity;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Репозиторий записей о физической активности.
 *
 * <p>Переопределяет сортировку на ASC — для тренировок важна хронология
 * (что было раньше), а не «свежее сверху».</p>
 */
public interface ActivityEntryRepository extends EntryRepository<ActivityEntryEntity> {

    @Override
    List<ActivityEntryEntity> findByTelegramUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(
            long telegramUserId, OffsetDateTime from, OffsetDateTime to);

    /**
     * Тренировки пользователя за период в хронологическом порядке.
     */
    List<ActivityEntryEntity> findByTelegramUserIdAndRecordedAtBetweenOrderByRecordedAtAsc(
            long telegramUserId, OffsetDateTime from, OffsetDateTime to);
}