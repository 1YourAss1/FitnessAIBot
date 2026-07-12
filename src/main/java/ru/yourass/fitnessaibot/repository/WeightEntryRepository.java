package ru.yourass.fitnessaibot.repository;

import ru.yourass.fitnessaibot.entity.WeightEntryEntity;

import java.util.Optional;

/**
 * Репозиторий записей о весе.
 */
public interface WeightEntryRepository extends EntryRepository<WeightEntryEntity> {

    /**
     * Самая свежая запись о весе для пользователя. Используется после синхронизации
     * для поддержания {@code UserProfileEntity.weightKg} в актуальном состоянии.
     */
    Optional<WeightEntryEntity> findFirstByTelegramUserIdOrderByRecordedAtDesc(long telegramUserId);
}