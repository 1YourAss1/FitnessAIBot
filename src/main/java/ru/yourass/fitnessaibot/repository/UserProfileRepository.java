package ru.yourass.fitnessaibot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.yourass.fitnessaibot.entity.UserProfileEntity;

import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfileEntity, Long> {

    default Optional<UserProfileEntity> findByTelegramUserId(long telegramUserId) {
        return findById(telegramUserId);
    }
}