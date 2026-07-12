package ru.yourass.fitnessaibot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.yourass.fitnessaibot.entity.UserProfileEntity;

import java.util.List;
import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfileEntity, Long> {

    default Optional<UserProfileEntity> findByTelegramUserId(long telegramUserId) {
        return findById(telegramUserId);
    }

    /**
     * Все профили с Google Health credential
     */
    List<UserProfileEntity> findByGoogleHealthCredentialNotNull();

    /**
     * Профиль с Google Health credential для конкретного пользователя.
     * Пустой {@code Optional} = пользователь либо не существует, либо не подключил Google.
     */
    default Optional<UserProfileEntity> findByTelegramUserIdAndGoogleHealthConnected(long telegramUserId) {
        return findByTelegramUserId(telegramUserId)
                .filter(p -> p.getGoogleHealthCredential() != null && !p.getGoogleHealthCredential().isBlank());
    }
}