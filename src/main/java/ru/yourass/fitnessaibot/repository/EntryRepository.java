package ru.yourass.fitnessaibot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import ru.yourass.fitnessaibot.entity.EntryEntity;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Базовый репозиторий для всех типов записей журнала
 */
@NoRepositoryBean
public interface EntryRepository<T extends EntryEntity> extends JpaRepository<T, Long> {

    /**
     * Записи пользователя за период, отсортированные по убыванию времени
     */
    List<T> findByTelegramUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(
            long telegramUserId, OffsetDateTime from, OffsetDateTime to);
}