package ru.yourass.fitnessaibot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Минимальный профиль пользователя Telegram, который ведёт бот.
 * Хранится ровно одна запись на telegramUserId — там зафиксированы пол,
 * возраст, рост, последний вес и уровень ежедневной активности.
 * Эти данные используются:
 * <ul>
 *   <li>для оценки калорийности активностей (MET × вес × часы),
 *       если пользователь сам явно не указал значения в сообщении;</li>
 *   <li>для расчёта BMR по формуле Миффлина-Сан Жеора
 *       (нужны пол, вес, рост и возраст) и TDEE
 *       (BMR × коэффициент {@link ActivityLevel}).</li>
 * </ul>
 *
 * <p>Также здесь хранится OAuth-credential Google Health API в виде
 * JSON-сериализованного {@code com.google.api.client.auth.oauth2.StoredCredential}.
 * Данные автоматически подтягиваются по расписанию (см.
 * {@link ru.yourass.fitnessaibot.health.GoogleHealthScheduler}).</p>
 */
@Getter
@Entity
@Table(name = "user_profiles")
public class UserProfileEntity {

    /** Идентификатор пользователя Telegram (= chat.id). */
    @Id
    @Column(name = "telegram_user_id")
    @Setter
    private Long telegramUserId;

    /** Пол пользователя. {@code null}, пока пользователь не сообщил. */
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 16)
    @Setter
    private Gender gender;

    /** Возраст пользователя в полных годах. {@code null}, пока не сообщался. */
    @Column(name = "age")
    @Setter
    private Integer age;

    /** Рост пользователя в сантиметрах. {@code null}, пока не сообщался. */
    @Column(name = "height_cm")
    @Setter
    private Double heightCm;

    /** Текущий вес пользователя в килограммах. Может быть null, если ещё не сообщался. */
    @Column(name = "weight_kg")
    @Setter
    private Double weightKg;

    /**
     * Целевой вес в килограммах. {@code null}, если у пользователя сейчас
     * нет цели по весу — тогда в аналитике игнорируем.
     */
    @Column(name = "goal_weight_kg")
    @Setter
    private Double goalWeightKg;

    /**
     * Уровень ежедневной активности по шкале Харриса-Бенедикта. Используется
     * для перевода BMR → TDEE ({@link ActivityLevel#getFactor()}).
     * {@code null}, если пользователь ещё не указал — тогда
     * {@link ru.yourass.fitnessaibot.ai.ProfileContextBuilder} возьмёт
     * дефолт {@link ActivityLevel#MODERATE}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "activity_level", length = 16)
    @Setter
    private ActivityLevel activityLevel;

    // ==================== Google Health / OAuth credential ====================

    /**
     * JSON-сериализованный {@link com.google.api.client.auth.oauth2.StoredCredential}
     * (access_token + refresh_token + expirationTimeMillis). Заполнен,
     * если пользователь прошёл OAuth-flow. Пусто или {@code null} —
     * аккаунт не подключён.
     */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "google_health_credential", columnDefinition = "TEXT")
    @Setter
    private String googleHealthCredential;

    /** Когда пользователь последний раз нажал «разрешить» в Google OAuth. */
    @Column(name = "gh_connected_at")
    @Setter
    private OffsetDateTime googleHealthConnectedAt;

    /** Успешная синхронизация последний раз прошла в этот момент (UTC). */
    @Column(name = "gh_last_sync_at")
    @Setter
    private OffsetDateTime googleHealthLastSyncAt;

    /** Сообщение об ошибке последней синхронизации (для отображения пользователю). */
    @Column(name = "gh_last_sync_error", length = 1024)
    @Setter
    private String googleHealthLastSyncError;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public UserProfileEntity() {
    }

    public UserProfileEntity(Long telegramUserId, Gender gender, Double weightKg) {
        this.telegramUserId = telegramUserId;
        this.gender = gender;
        this.weightKg = weightKg;
    }

    public UserProfileEntity(Long telegramUserId, Gender gender, Integer age,
                             Double heightCm, Double weightKg) {
        this.telegramUserId = telegramUserId;
        this.gender = gender;
        this.age = age;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
    }

    public UserProfileEntity(Long telegramUserId, Gender gender, Integer age,
                             Double heightCm, Double weightKg, Double goalWeightKg) {
        this(telegramUserId, gender, age, heightCm, weightKg);
        this.goalWeightKg = goalWeightKg;
    }

    public UserProfileEntity(Long telegramUserId, Gender gender, Integer age,
                             Double heightCm, Double weightKg, Double goalWeightKg,
                             ActivityLevel activityLevel) {
        this(telegramUserId, gender, age, heightCm, weightKg, goalWeightKg);
        this.activityLevel = activityLevel;
    }

    /**
     * Проверяет, что известны все четыре параметра, нужные для расчёта BMR
     * по формуле Миффлина-Сан Жеора: пол, возраст, рост и вес.
     */
    public boolean hasBmrInputs() {
        return gender != null && age != null && heightCm != null && weightKg != null
                && age > 0 && heightCm > 0 && weightKg > 0;
    }
}
