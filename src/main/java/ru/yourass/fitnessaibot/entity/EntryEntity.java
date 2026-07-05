package ru.yourass.fitnessaibot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Базовая JPA-сущность записи журнала.
 *
 * <p>Стратегия {@link InheritanceType#JOINED}: общие поля хранятся в таблице
 * {@code entries}, специфичные для каждого типа — в {@code food_entries},
 * {@code activity_entries}, {@code sleep_entries}, {@code weight_entries}
 * с FK на {@code entries.id}.</p>
 */
@Entity
@Table(name = "entries", indexes = {
        @Index(name = "ix_entries_user_time", columnList = "telegram_user_id, recorded_at")
})
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@Setter
@NoArgsConstructor
public abstract class EntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_user_id", nullable = false)
    private long telegramUserId;

    @Column(length = 256)
    private String name;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private OffsetDateTime recordedAt;

    @Column(name = "source_message", length = 4096)
    private String sourceMessage;

    /** Имя конкретного подкласса (для логов и switch в маппере). */
    public abstract String type();
}