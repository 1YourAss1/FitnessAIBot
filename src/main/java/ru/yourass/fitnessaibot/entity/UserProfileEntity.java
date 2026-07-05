package ru.yourass.fitnessaibot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;
import ru.yourass.fitnessaibot.ai.BmrCalculator;

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
 *       (BMR × коэффициент {@link BmrCalculator.ActivityLevel}).</li>
 * </ul>
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
     * для перевода BMR → TDEE ({@link BmrCalculator.ActivityLevel#getFactor()}).
     * {@code null}, если пользователь ещё не указал — тогда
     * {@link ru.yourass.fitnessaibot.ai.EntryTools#calculateBmrTdee} возьмёт
     * дефолт {@link BmrCalculator.ActivityLevel#MODERATE} и сообщит об этом
     * в результате.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "activity_level", length = 16)
    @Setter
    private BmrCalculator.ActivityLevel activityLevel;

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
                             BmrCalculator.ActivityLevel activityLevel) {
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