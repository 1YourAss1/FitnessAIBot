package ru.yourass.fitnessaibot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Запись журнала о физической активности.
 *
 * <p>JOINED-наследник {@link EntryEntity}, хранится в таблице
 * {@code activity_entries} с FK {@code entry_id → entries.id}.</p>
 */
@Entity
@Table(name = "activity_entries")
@PrimaryKeyJoinColumn(name = "entry_id")
@Getter
@Setter
@NoArgsConstructor
public class ActivityEntryEntity extends EntryEntity {

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "calories_burned")
    private Integer caloriesBurned;

    public ActivityEntryEntity(long telegramUserId, String name,
                               int durationMinutes, int caloriesBurned) {
        this.setTelegramUserId(telegramUserId);
        this.setName(name);
        this.durationMinutes = durationMinutes;
        this.caloriesBurned = caloriesBurned;
    }

    /**
     * Длительность активности в человеческом формате: {@code 90} минут →
     * {@code "1 час 30 минут"}, {@code 60} минут → {@code "1 час"}.
     */
    public String getFormattedDuration() {
        if (durationMinutes == null) return null;
        return RussianPlural.formatMinutes(durationMinutes);
    }

    @Override
    public String type() {
        return "ACTIVITY";
    }
}