package ru.yourass.fitnessaibot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Запись журнала о приёме пищи.
 *
 * <p>JOINED-наследник {@link EntryEntity}, хранится в таблице
 * {@code food_entries} с FK {@code entry_id → entries.id}.</p>
 */
@Entity
@Table(name = "food_entries")
@PrimaryKeyJoinColumn(name = "entry_id")
@Getter
@Setter
@NoArgsConstructor
public class FoodEntryEntity extends EntryEntity {

    @Column(nullable = false)
    private Integer calories;

    @Column(name = "protein_g")
    private Integer proteinGrams;

    @Column(name = "fat_g")
    private Integer fatGrams;

    @Column(name = "carbs_g")
    private Integer carbsGrams;

    public FoodEntryEntity(long telegramUserId, String name,
                           int calories, int proteinGrams, int fatGrams, int carbsGrams) {
        this.setTelegramUserId(telegramUserId);
        this.setName(name);
        this.calories = calories;
        this.proteinGrams = proteinGrams;
        this.fatGrams = fatGrams;
        this.carbsGrams = carbsGrams;
    }

    @Override
    public String type() {
        return "FOOD";
    }
}