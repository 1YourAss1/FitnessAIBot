package ru.yourass.fitnessaibot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Запись журнала о продолжительности сна.
 *
 * <p>JOINED-наследник {@link EntryEntity}, хранится в таблице
 * {@code sleep_entries} с FK {@code entry_id → entries.id}.</p>
 */
@Entity
@Table(name = "sleep_entries")
@PrimaryKeyJoinColumn(name = "entry_id")
@Getter
@Setter
@NoArgsConstructor
public class SleepEntryEntity extends EntryEntity {

    @Column(nullable = false)
    private Double hours;

    public SleepEntryEntity(long telegramUserId, double hours) {
        this.setTelegramUserId(telegramUserId);
        this.setName("Сон");
        this.hours = hours;
    }

    @Override
    public String type() {
        return "SLEEP";
    }
}