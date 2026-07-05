package ru.yourass.fitnessaibot.ai;

import org.junit.jupiter.api.Test;
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
        UserProfileEntity p = new UserProfileEntity(1L, Gender.MALE, 30, 180.0, 80.0);
        // 180.0 → "180", 80.0 → "80"; целые дубли не печатаем с ".0".
        assertThat(ProfileContextBuilder.render(p))
                .isEqualTo("profile: gender=MALE age=30 height=180 weight=80");
    }

    @Test
    void render_fractionalHeightAndWeight() {
        UserProfileEntity p = new UserProfileEntity(2L, Gender.FEMALE, 25, 165.5, 62.3);
        assertThat(ProfileContextBuilder.render(p))
                .isEqualTo("profile: gender=FEMALE age=25 height=165.5 weight=62.3");
    }

    @Test
    void render_partialProfile_skipsMissingFields() {
        // Известен пол и рост, остальное нет — выводим только известные поля. Это даёт
        // модели однозначный сигнал «это уже знаем, остальное нужно спросить».
        UserProfileEntity p = new UserProfileEntity(3L, Gender.MALE, null, 180.0, null);
        assertThat(ProfileContextBuilder.render(p))
                .isEqualTo("profile: gender=MALE height=180");
    }

    @Test
    void render_fullProfileWithGoal_includesGoal() {
        UserProfileEntity p = new UserProfileEntity(
                4L, Gender.MALE, 30, 180.0, 85.0, 78.0);
        assertThat(ProfileContextBuilder.render(p))
                .isEqualTo("profile: gender=MALE age=30 height=180 weight=85 goal=78");
    }

    @Test
    void render_fullProfileWithoutGoal_omitsGoalField() {
        // Без цели профиль не должен содержать goal= — это сигнал модели,
        // что цели по весу сейчас нет.
        UserProfileEntity p = new UserProfileEntity(
                5L, Gender.MALE, 30, 180.0, 85.0);
        assertThat(ProfileContextBuilder.render(p))
                .isEqualTo("profile: gender=MALE age=30 height=180 weight=85");
    }

    @Test
    void render_fullProfileWithActivity_includesActivity() {
        UserProfileEntity p = new UserProfileEntity(
                6L, Gender.MALE, 30, 180.0, 80.0, 78.0,
                BmrCalculator.ActivityLevel.ACTIVE);
        assertThat(ProfileContextBuilder.render(p))
                .isEqualTo(
                        "profile: gender=MALE age=30 height=180 weight=80 "
                                + "goal=78 activity=ACTIVE");
    }

    @Test
    void render_profileWithoutActivity_omitsActivityField() {
        // Без явной активности в профиле поле activity= отсутствует —
        // модель должна понять, что уровень активности не задан и при
        // BMR/TDEE-вопросах надо либо спросить, либо взять дефолт MODERATE.
        UserProfileEntity p = new UserProfileEntity(
                7L, Gender.MALE, 30, 180.0, 80.0);
        assertThat(ProfileContextBuilder.render(p))
                .isEqualTo("profile: gender=MALE age=30 height=180 weight=80");
    }
}
