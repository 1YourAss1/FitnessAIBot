package ru.yourass.fitnessaibot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Запись журнала о весе пользователя (кг).
 *
 * <p>JOINED-наследник {@link EntryEntity}, хранится в таблице
 * {@code weight_entries} с FK {@code entry_id → entries.id}.</p>
 */
@Entity
@Table(name = "weight_entries")
@PrimaryKeyJoinColumn(name = "entry_id")
@Getter
@Setter
@NoArgsConstructor
public class WeightEntryEntity extends EntryEntity {

    @Column(nullable = false)
    private Double kilograms;

    public WeightEntryEntity(long telegramUserId, double kilograms) {
        this.setTelegramUserId(telegramUserId);
        this.setName("Вес");
        this.kilograms = kilograms;
    }

    @Override
    public String type() {
        return "WEIGHT";
    }
}