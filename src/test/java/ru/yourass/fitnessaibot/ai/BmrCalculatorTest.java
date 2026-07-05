package ru.yourass.fitnessaibot.ai;

import org.junit.jupiter.api.Test;
import ru.yourass.fitnessaibot.ai.BmrCalculator.ActivityLevel;
import ru.yourass.fitnessaibot.ai.BmrCalculator.BmrResult;
import ru.yourass.fitnessaibot.entity.Gender;
import ru.yourass.fitnessaibot.entity.UserProfileEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class BmrCalculatorTest {

    private final BmrCalculator calc = new BmrCalculator();

    @Test
    void bmr_male_formula() {
        // Mifflin-St Jeor: 10*80 + 6.25*180 - 5*30 + 5 = 800 + 1125 - 150 + 5 = 1780
        UserProfileEntity p = new UserProfileEntity(1L, Gender.MALE, 30, 180.0, 80.0);
        BmrResult r = calc.calculate(p, ActivityLevel.MODERATE);
        assertThat(r.bmrKcal()).isEqualTo(1780.0, within(0.05));
        // 1780 * 1.55 = 2759
        assertThat(r.tdeeKcal()).isEqualTo(2759.0, within(0.05));
        assertThat(r.activityLevel()).isEqualTo(ActivityLevel.MODERATE);
    }

    @Test
    void bmr_female_formula() {
        // 10*60 + 6.25*165 - 5*25 - 161 = 600 + 1031.25 - 125 - 161 = 1345.25
        UserProfileEntity p = new UserProfileEntity(2L, Gender.FEMALE, 25, 165.0, 60.0);
        BmrResult r = calc.calculate(p, ActivityLevel.SEDENTARY);
        assertThat(r.bmrKcal()).isEqualTo(1345.3, within(0.05));
        // 1345.25 * 1.2 = 1614.3
        assertThat(r.tdeeKcal()).isEqualTo(1614.3, within(0.05));
    }

    @Test
    void tdee_scalesWithActivity() {
        UserProfileEntity p = new UserProfileEntity(3L, Gender.MALE, 30, 180.0, 80.0);
        double sedentary = calc.calculate(p, ActivityLevel.SEDENTARY).tdeeKcal();
        double active = calc.calculate(p, ActivityLevel.ACTIVE).tdeeKcal();
        assertThat(active).isGreaterThan(sedentary);
    }

    @Test
    void missingGender_throws() {
        UserProfileEntity p = new UserProfileEntity(4L, null, 30, 180.0, 80.0);
        assertThatThrownBy(() -> calc.calculate(p, ActivityLevel.SEDENTARY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("пол");
    }

    @Test
    void missingAge_throws() {
        UserProfileEntity p = new UserProfileEntity(5L, Gender.MALE, null, 180.0, 80.0);
        assertThatThrownBy(() -> calc.calculate(p, ActivityLevel.SEDENTARY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("возраст");
    }

    @Test
    void missingHeight_throws() {
        UserProfileEntity p = new UserProfileEntity(6L, Gender.MALE, 30, null, 80.0);
        assertThatThrownBy(() -> calc.calculate(p, ActivityLevel.SEDENTARY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("рост");
    }

    @Test
    void missingWeight_throws() {
        UserProfileEntity p = new UserProfileEntity(7L, Gender.MALE, 30, 180.0, null);
        assertThatThrownBy(() -> calc.calculate(p, ActivityLevel.SEDENTARY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("вес");
    }
}
