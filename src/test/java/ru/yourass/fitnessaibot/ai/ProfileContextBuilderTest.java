package ru.yourass.fitnessaibot.ai;

import org.junit.jupiter.api.Test;
import ru.yourass.fitnessaibot.entity.ActivityLevel;
import ru.yourass.fitnessaibot.entity.Gender;
import ru.yourass.fitnessaibot.entity.UserProfileEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileContextBuilderTest {

    @Test
    void render_nullProfile_returnsEmpty() {
        assertThat(ProfileContextBuilder.render(null)).isEqualTo("profile: empty");
    }

    @Test
    void render_allFieldsMissing_returnsEmpty() {
        // Профиль существует, но ни одно из четырёх полей не задано — модель видит,
        // что у пользователя нет известных данных и можно спросить всё с нуля.
        UserProfileEntity p = new UserProfileEntity(1L, null, null, null, null);
        assertThat(ProfileContextBuilder.render(p)).isEqualTo("profile: empty");
    }

    @Test
    void render_allFields_setAndFormattedNicely() {
        // Mifflin-St Jeor: 10*80 + 6.25*180 - 5*30 + 5 = 1780. Без activityLevel
        // в профиле tdee считается с дефолтом MODERATE (×1.55) = 2759.
        UserProfileEntity p = new UserProfileEntity(1L, Gender.MALE, 30, 180.0, 80.0);
        assertThat(ProfileContextBuilder.render(p))
                .isEqualTo("profile: gender=MALE age=30 height=180 weight=80 bmr=1780 tdee=2759");
    }

    @Test
    void render_fractionalHeightAndWeight() {
        // 10*62.3 + 6.25*165.5 - 5*25 - 161 = 1371.375 → 1371.4 (округлено).
        // TDEE с MODERATE: 1371.4 × 1.55 = 2125.67 (по арифметике),
        // но Double.toString из-за IEEE-754 печатает результат как 2125.6 —
        // самый короткий десятичный, который round-trip'ит обратно к тому же double.
        UserProfileEntity p = new UserProfileEntity(2L, Gender.FEMALE, 25, 165.5, 62.3);
        assertThat(ProfileContextBuilder.render(p))
                .isEqualTo("profile: gender=FEMALE age=25 height=165.5 weight=62.3 bmr=1371.4 tdee=2125.6");
    }

    @Test
    void render_partialProfile_skipsMissingFields() {
        // Известен пол и рост, остальное нет — hasBmrInputs()=false,
        // bmr/tdee НЕ добавляются.
        UserProfileEntity p = new UserProfileEntity(3L, Gender.MALE, null, 180.0, null);
        assertThat(ProfileContextBuilder.render(p))
                .isEqualTo("profile: gender=MALE height=180");
    }

    @Test
    void render_partialProfile_missingWeight_skipsBmrTdee() {
        // Полный по возрасту/росту, но веса нет — hasBmrInputs()=false.
        UserProfileEntity p = new UserProfileEntity(4L, Gender.MALE, 30, 180.0, null);
        assertThat(ProfileContextBuilder.render(p))
                .isEqualTo("profile: gender=MALE age=30 height=180");
    }

    @Test
    void render_fullProfileWithGoal_includesGoal() {
        // BMR=1830, TDEE с дефолтом MODERATE = 2836.5.
        UserProfileEntity p = new UserProfileEntity(
                4L, Gender.MALE, 30, 180.0, 85.0, 78.0);
        assertThat(ProfileContextBuilder.render(p))
                .isEqualTo("profile: gender=MALE age=30 height=180 weight=85 goal=78 bmr=1830 tdee=2836.5");
    }

    @Test
    void render_fullProfileWithoutGoal_omitsGoalField() {
        // Без цели профиль не должен содержать goal= — это сигнал модели,
        // что цели по весу сейчас нет.
        UserProfileEntity p = new UserProfileEntity(
                5L, Gender.MALE, 30, 180.0, 85.0);
        assertThat(ProfileContextBuilder.render(p))
                .isEqualTo("profile: gender=MALE age=30 height=180 weight=85 bmr=1830 tdee=2836.5");
    }

    @Test
    void render_fullProfileWithActivity_includesActivityAndUsesItsFactor() {
        // ACTIVE (×1.725): 1780 × 1.725 = 3070.5.
        UserProfileEntity p = new UserProfileEntity(
                6L, Gender.MALE, 30, 180.0, 80.0, 78.0,
                ActivityLevel.ACTIVE);
        assertThat(ProfileContextBuilder.render(p))
                .isEqualTo(
                        "profile: gender=MALE age=30 height=180 weight=80 "
                                + "goal=78 activity=ACTIVE bmr=1780 tdee=3070.5");
    }

    @Test
    void render_fullProfileWithoutActivity_usesModerateFallback() {
        // activityLevel=null → tdee считается как с MODERATE (×1.55).
        UserProfileEntity p = new UserProfileEntity(
                7L, Gender.MALE, 30, 180.0, 80.0);
        assertThat(ProfileContextBuilder.render(p))
                .isEqualTo("profile: gender=MALE age=30 height=180 weight=80 bmr=1780 tdee=2759");
    }
}