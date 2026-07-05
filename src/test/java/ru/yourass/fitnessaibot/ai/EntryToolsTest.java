package ru.yourass.fitnessaibot.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import ru.yourass.fitnessaibot.entity.ActivityEntryEntity;
import ru.yourass.fitnessaibot.entity.EntryEntity;
import ru.yourass.fitnessaibot.entity.FoodEntryEntity;
import ru.yourass.fitnessaibot.entity.Gender;
import ru.yourass.fitnessaibot.entity.SleepEntryEntity;
import ru.yourass.fitnessaibot.entity.UserProfileEntity;
import ru.yourass.fitnessaibot.entity.WeightEntryEntity;
import ru.yourass.fitnessaibot.repository.ActivityEntryRepository;
import ru.yourass.fitnessaibot.repository.FoodEntryRepository;
import ru.yourass.fitnessaibot.repository.SleepEntryRepository;
import ru.yourass.fitnessaibot.repository.UserProfileRepository;
import ru.yourass.fitnessaibot.repository.WeightEntryRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Интеграционные тесты тул-методов: проверяют, что write-тулы реально
 * пишут в БД и обновляют профиль веса, а read-тулы — корректно возвращают
 * записи. Без вызовов LLM.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "fitness.bot.enabled=false",
        "fitness.bot.username=test-bot",
        "fitness.bot.token=000000:TEST",
        "spring.ai.openai.api-key=sk-test"
})
class EntryToolsTest {

    @Autowired
    EntryTools tools;

    @Autowired
    FoodEntryRepository foodRepository;
    @Autowired
    ActivityEntryRepository activityRepository;
    @Autowired
    SleepEntryRepository sleepRepository;
    @Autowired
    WeightEntryRepository weightRepository;

    @Autowired
    UserProfileRepository userProfileRepository;

    private static final long USER_A = 111_111L;
    private static final long USER_B = 222_222L;

    /** Контекст с исходным сообщением — то, что в проде кладёт FitnessAgent. */
    private static ToolContext ctxOf(String text) {
        return new ToolContext(Map.of(EntryTools.CTX_SOURCE_MESSAGE, text));
    }

    /** Очищает все записи тестового пользователя во всех таблицах. */
    private void clearUser(long userId) {
        OffsetDateTime from = OffsetDateTime.parse("1970-01-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2999-12-31T23:59:59Z");
        foodRepository.findByTelegramUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(userId, from, to)
                .forEach(e -> foodRepository.deleteById(e.getId()));
        activityRepository.findByTelegramUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(userId, from, to)
                .forEach(e -> activityRepository.deleteById(e.getId()));
        sleepRepository.findByTelegramUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(userId, from, to)
                .forEach(e -> sleepRepository.deleteById(e.getId()));
        weightRepository.findByTelegramUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(userId, from, to)
                .forEach(e -> weightRepository.deleteById(e.getId()));
    }

    @Test
    void saveFood_persistsEntry_andStoresSourceMessage() {
        String src = "в обед съел макароны с котлетой";
        FoodEntryEntity saved = tools.saveFood(USER_A,
                "Макароны с котлетой", 650, 35, 20, 80, null, ctxOf(src));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTelegramUserId()).isEqualTo(USER_A);
        assertThat(saved.getName()).isEqualTo("Макароны с котлетой");
        assertThat(saved.getCalories()).isEqualTo(650);
        assertThat(saved.getProteinGrams()).isEqualTo(35);
        assertThat(saved.getSourceMessage()).isEqualTo(src);
        // recordedAt ставится сервером при сохранении (UTC, текущий момент).
        assertThat(saved.getRecordedAt()).isNotNull();

        FoodEntryEntity fromDb = foodRepository.findById(saved.getId()).orElseThrow();
        assertThat(fromDb.getProteinGrams()).isEqualTo(35);
        assertThat(fromDb.getSourceMessage()).isEqualTo(src);
    }

    @Test
    void saveActivity_persistsEntry_andStoresSourceMessage() {
        String src = "пробежал 30 минут";
        ActivityEntryEntity saved = tools.saveActivity(
                USER_A, "Бег", 30, 280, null, ctxOf(src));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getDurationMinutes()).isEqualTo(30);
        assertThat(saved.getCaloriesBurned()).isEqualTo(280);
        assertThat(saved.getSourceMessage()).isEqualTo(src);
        assertThat(saved.getRecordedAt()).isNotNull();
    }

    @Test
    void saveSleep_persistsEntry_andStoresSourceMessage() {
        String src = "спал 7.5 часов";
        SleepEntryEntity saved = tools.saveSleep(USER_A, 7.5, null, ctxOf(src));
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getHours()).isEqualTo(7.5);
        assertThat(saved.getSourceMessage()).isEqualTo(src);
        assertThat(saved.getRecordedAt()).isNotNull();
    }

    @Test
    void saveWeight_updatesProfile_andStoresSourceMessage() {
        clearUser(USER_A);
        String src = "вешу 82.5 кг";
        WeightEntryEntity saved = tools.saveWeight(USER_A, 82.5, null, ctxOf(src));
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getKilograms()).isEqualTo(82.5);
        assertThat(saved.getSourceMessage()).isEqualTo(src);
        assertThat(saved.getRecordedAt()).isNotNull();

        Double w = userProfileRepository.findByTelegramUserId(USER_A)
                .map(UserProfileEntity::getWeightKg).orElse(null);
        assertThat(w).isEqualTo(82.5);
    }

    @Test
    void saveWeight_rejectsOutOfRange() {
        assertThatThrownBy(() -> tools.saveWeight(USER_A, 0, null, ctxOf("вес 0")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tools.saveWeight(USER_A, 600, null, ctxOf("вес 600")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saveFood_withoutToolContext_leavesSourceMessageNull() {
        // Прямой вызов из теста/админки — без контекста поле остаётся null.
        FoodEntryEntity saved = tools.saveFood(USER_B,
                "Творог", 120, 18, 4, 5, null, null);

        assertThat(saved.getSourceMessage()).isNull();
    }

    @Test
    void readCurrentWeightKg_fallsBackToDefault() {
        double w = tools.readCurrentWeightKg(USER_B);
        assertThat(w).isEqualTo(70.0);
    }

    @Test
    void readByTypeAndPeriod_filters() {
        clearUser(USER_A);

        tools.saveFood(USER_A, "X", 100, 1, 1, 1, null, ctxOf("X"));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<? extends EntryEntity> list = tools.readByTypeAndPeriod(
                USER_A, "FOOD",
                now.minusHours(1).toString(),
                now.plusHours(1).toString());
        assertThat(list).hasSize(1);
        assertThat(list.getFirst()).isInstanceOf(FoodEntryEntity.class);
    }

    @Test
    void saveProfile_createsAndUpdates() {
        clearUser(USER_A);
        userProfileRepository.findByTelegramUserId(USER_A).ifPresent(userProfileRepository::delete);

        UserProfileEntity saved = tools.saveProfile(USER_A,
                "мужской", 30, 180.0, 80.0, 75.0, null);
        assertThat(saved.getGender()).isEqualTo(Gender.MALE);
        assertThat(saved.getAge()).isEqualTo(30);
        assertThat(saved.getHeightCm()).isEqualTo(180.0);
        assertThat(saved.getWeightKg()).isEqualTo(80.0);
        assertThat(saved.getGoalWeightKg()).isEqualTo(75.0);

        // Частичное обновление — только рост и возраст.
        UserProfileEntity updated = tools.saveProfile(USER_A,
                null, 31, 181.0, null, null, null);
        assertThat(updated.getGender()).isEqualTo(Gender.MALE);
        assertThat(updated.getAge()).isEqualTo(31);
        assertThat(updated.getHeightCm()).isEqualTo(181.0);
        assertThat(updated.getWeightKg()).isEqualTo(80.0);
        // Цель, заданная в прошлом вызове, не должна была сброситься.
        assertThat(updated.getGoalWeightKg()).isEqualTo(75.0);
    }

    @Test
    void saveProfile_validatesRanges() {
        clearUser(USER_A);
        userProfileRepository.findByTelegramUserId(USER_A).ifPresent(userProfileRepository::delete);

        assertThatThrownBy(() -> tools.saveProfile(USER_A,
                "MALE", 200, 180.0, 80.0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tools.saveProfile(USER_A,
                "FEMALE", 30, 300.0, 80.0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tools.saveProfile(USER_A,
                "MALE", 30, 180.0, 600.0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tools.saveProfile(USER_A,
                "unknown", 30, 180.0, 80.0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tools.saveProfile(USER_A,
                "MALE", 30, 180.0, 80.0, 600.0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readProfile_returnsEmptyWhenMissing() {
        clearUser(USER_B);
        userProfileRepository.findByTelegramUserId(USER_B).ifPresent(userProfileRepository::delete);

        UserProfileEntity profile = tools.readProfile(USER_B);
        assertThat(profile.getTelegramUserId()).isEqualTo(USER_B);
        assertThat(profile.getGender()).isNull();
        assertThat(profile.getAge()).isNull();
        assertThat(profile.getHeightCm()).isNull();
        assertThat(profile.getWeightKg()).isNull();
    }

    @Test
    void calculateBmrTdee_requiresFullProfile() {
        clearUser(USER_A);
        userProfileRepository.findByTelegramUserId(USER_A).ifPresent(userProfileRepository::delete);

        // Без профиля вообще.
        assertThatThrownBy(() -> tools.calculateBmrTdee(USER_A))
                .isInstanceOf(IllegalStateException.class);

        // Только вес — не хватает остального.
        tools.saveProfile(USER_A, null, null, null, 80.0, null, null);
        assertThatThrownBy(() -> tools.calculateBmrTdee(USER_A))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BMR");
    }

    @Test
    void calculateBmrTdee_worksWithFullProfile_usesStoredActivity() {
        clearUser(USER_A);
        userProfileRepository.findByTelegramUserId(USER_A).ifPresent(userProfileRepository::delete);

        tools.saveProfile(USER_A, "MALE", 30, 180.0, 80.0, null, "ACTIVE");
        BmrCalculator.BmrResult result = tools.calculateBmrTdee(USER_A);
        assertThat(result.bmrKcal()).isEqualTo(1780.0);
        assertThat(result.activityLevel()).isEqualTo(BmrCalculator.ActivityLevel.ACTIVE);
        // 1780 * 1.725 = 3070.5 → округлено до 3070.5
        assertThat(result.tdeeKcal()).isEqualTo(3070.5);
    }

    @Test
    void calculateBmrTdee_fallsBackToDefaultActivity() {
        // Без явного уровня активности в профиле — тул берёт дефолт MODERATE,
        // а модель должна упомянуть это в финальном ответе (см. SYSTEM_PROMPT).
        clearUser(USER_A);
        userProfileRepository.findByTelegramUserId(USER_A).ifPresent(userProfileRepository::delete);
        tools.saveProfile(USER_A, "MALE", 30, 180.0, 80.0, null, null);

        BmrCalculator.BmrResult result = tools.calculateBmrTdee(USER_A);
        assertThat(result.activityLevel()).isEqualTo(BmrCalculator.ActivityLevel.MODERATE);
        // 1780 * 1.55 = 2759
        assertThat(result.tdeeKcal()).isEqualTo(2759.0);
    }

    @Test
    void calculateBmrTdee_acceptsRussianActivityLabels() {
        // saveProfile должен понимать и латиницу, и русские описания уровней.
        clearUser(USER_A);
        userProfileRepository.findByTelegramUserId(USER_A).ifPresent(userProfileRepository::delete);
        UserProfileEntity saved = tools.saveProfile(USER_A, "MALE", 30, 180.0, 80.0, null,
                "высокий");
        assertThat(saved.getActivityLevel()).isEqualTo(BmrCalculator.ActivityLevel.ACTIVE);
    }

    @Test
    void calculateBmrTdee_rejectsUnknownActivityLabel() {
        clearUser(USER_A);
        userProfileRepository.findByTelegramUserId(USER_A).ifPresent(userProfileRepository::delete);

        assertThatThrownBy(() -> tools.saveProfile(USER_A, "MALE", 30, 180.0, 80.0, null,
                "ЧАСТО-АКТИВНО"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Autowired
    private ru.yourass.fitnessaibot.ai.ProfileContextBuilder profileContextBuilder;

    @Test
    void profileContext_emptyWhenNoProfile() {
        clearUser(USER_B);
        userProfileRepository.findByTelegramUserId(USER_B).ifPresent(userProfileRepository::delete);

        assertThat(profileContextBuilder.build(USER_B)).isEqualTo("profile: empty");
    }

    @Test
    void profileContext_includesAlreadySavedFields() {
        clearUser(USER_A);
        userProfileRepository.findByTelegramUserId(USER_A).ifPresent(userProfileRepository::delete);
        tools.saveProfile(USER_A, "мужской", 30, 180.0, 80.0, null, null);

        assertThat(profileContextBuilder.build(USER_A))
                .isEqualTo("profile: gender=MALE age=30 height=180 weight=80");
    }

    @Test
    void profileContext_partialProfile_keepsOnlyKnownFields() {
        clearUser(USER_A);
        userProfileRepository.findByTelegramUserId(USER_A).ifPresent(userProfileRepository::delete);
        tools.saveProfile(USER_A, "MALE", null, 180.0, null, null, null);

        assertThat(profileContextBuilder.build(USER_A))
                .isEqualTo("profile: gender=MALE height=180");
    }

    @Test
    void profileContext_includesActivityWhenKnown() {
        clearUser(USER_A);
        userProfileRepository.findByTelegramUserId(USER_A).ifPresent(userProfileRepository::delete);
        tools.saveProfile(USER_A, "MALE", 30, 180.0, 80.0, 78.0, "ACTIVE");

        assertThat(profileContextBuilder.build(USER_A))
                .isEqualTo("profile: gender=MALE age=30 height=180 weight=80 goal=78 activity=ACTIVE");
    }

    @Test
    void profileContext_omitsActivityWhenUnknown() {
        // Поле activity= не должно появляться в строке, если в профиле пусто —
        // это сигнал модели, что активность пока не задана.
        clearUser(USER_A);
        userProfileRepository.findByTelegramUserId(USER_A).ifPresent(userProfileRepository::delete);
        tools.saveProfile(USER_A, "MALE", 30, 180.0, 80.0, null, null);

        assertThat(profileContextBuilder.build(USER_A))
                .isEqualTo("profile: gender=MALE age=30 height=180 weight=80");
    }

    @Test
    void saveProfile_goal_setsGoalWeight() {
        clearUser(USER_A);
        userProfileRepository.findByTelegramUserId(USER_A).ifPresent(userProfileRepository::delete);
        tools.saveProfile(USER_A, "MALE", 30, 180.0, 85.0, 78.0, null);

        UserProfileEntity reloaded = userProfileRepository.findByTelegramUserId(USER_A).orElseThrow();
        assertThat(reloaded.getGoalWeightKg()).isEqualTo(78.0);
        assertThat(reloaded.getActivityLevel()).isNull();
    }

    @Test
    void saveProfile_activity_setsField() {
        clearUser(USER_A);
        userProfileRepository.findByTelegramUserId(USER_A).ifPresent(userProfileRepository::delete);
        tools.saveProfile(USER_A, "MALE", 30, 180.0, 80.0, null, "MODERATE");

        UserProfileEntity reloaded = userProfileRepository.findByTelegramUserId(USER_A).orElseThrow();
        assertThat(reloaded.getActivityLevel()).isEqualTo(BmrCalculator.ActivityLevel.MODERATE);
    }

    @Test
    void saveProfile_omittingActivity_leavesItUnchanged() {
        // Установили активность, потом обновили профиль без неё — должно остаться.
        clearUser(USER_A);
        userProfileRepository.findByTelegramUserId(USER_A).ifPresent(userProfileRepository::delete);
        tools.saveProfile(USER_A, "MALE", 30, 180.0, 80.0, null, "ACTIVE");
        tools.saveProfile(USER_A, null, null, null, 84.5, null, null);

        UserProfileEntity reloaded = userProfileRepository.findByTelegramUserId(USER_A).orElseThrow();
        assertThat(reloaded.getWeightKg()).isEqualTo(84.5);
        assertThat(reloaded.getActivityLevel()).isEqualTo(BmrCalculator.ActivityLevel.ACTIVE);
    }

    @Test
    void saveProfile_goalOmittedInitially_isNull() {
        // Без явной передачи goalWeightKg — поле остаётся null. Это и есть
        // «у пользователя сейчас нет цели по весу».
        clearUser(USER_A);
        userProfileRepository.findByTelegramUserId(USER_A).ifPresent(userProfileRepository::delete);
        tools.saveProfile(USER_A, "MALE", 30, 180.0, 80.0, null, null);

        UserProfileEntity reloaded = userProfileRepository.findByTelegramUserId(USER_A).orElseThrow();
        assertThat(reloaded.getGoalWeightKg()).isNull();
        assertThat(reloaded.getActivityLevel()).isNull();
    }

    // ------------------------------------------------------------------
    //  Запись за произвольный день (recordedAt)
    // ------------------------------------------------------------------

    @Test
    void saveFood_withRecordedAt_persistsForThatDate() {
        clearUser(USER_A);
        FoodEntryEntity saved = tools.saveFood(USER_A, "Суп", 300, 10, 5, 40,
                "2026-07-01", ctxOf("первого числа ел суп"));

        OffsetDateTime expected = LocalDate.parse("2026-07-01")
                .atTime(12, 0).atOffset(ZoneOffset.UTC);
        assertThat(saved.getRecordedAt()).isEqualTo(expected);

        // Проверяем, что запись попадает в выборку за тот день.
        List<? extends EntryEntity> july = tools.readByTypeAndPeriod(USER_A, "FOOD",
                "2026-07-01T00:00:00Z", "2026-07-01T23:59:59Z");
        assertThat(july).hasSize(1);
        assertThat(july.getFirst().getId()).isEqualTo(saved.getId());
    }

    @Test
    void saveActivity_withRecordedAt_persistsForThatDate() {
        clearUser(USER_A);
        ActivityEntryEntity saved = tools.saveActivity(USER_A, "Бег",
                40, 400, "2026-06-28", ctxOf("бегал в воскресенье"));

        OffsetDateTime expected = LocalDate.parse("2026-06-28")
                .atTime(12, 0).atOffset(ZoneOffset.UTC);
        assertThat(saved.getRecordedAt()).isEqualTo(expected);
    }

    @Test
    void saveSleep_withRecordedAt_persistsForThatDate() {
        clearUser(USER_A);
        SleepEntryEntity saved = tools.saveSleep(USER_A, 8.0,
                "2026-07-04", ctxOf("ночью спал 8 часов"));

        OffsetDateTime expected = LocalDate.parse("2026-07-04")
                .atTime(12, 0).atOffset(ZoneOffset.UTC);
        assertThat(saved.getRecordedAt()).isEqualTo(expected);
    }

    @Test
    void saveWeight_withRecordedAt_persistsForThatDate() {
        clearUser(USER_A);
        WeightEntryEntity saved = tools.saveWeight(USER_A, 81.0,
                "2026-07-02", ctxOf("взвешивался 2 июля"));

        OffsetDateTime expected = LocalDate.parse("2026-07-02")
                .atTime(12, 0).atOffset(ZoneOffset.UTC);
        assertThat(saved.getRecordedAt()).isEqualTo(expected);

        // Профиль всё равно обновляется на актуальный вес.
        assertThat(userProfileRepository.findByTelegramUserId(USER_A)
                .map(UserProfileEntity::getWeightKg).orElse(null)).isEqualTo(81.0);
    }

    @Test
    void saveFood_withBlankRecordedAt_fallsBackToNow() {
        clearUser(USER_A);
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1);
        FoodEntryEntity saved = tools.saveFood(USER_A, "X", 100, 1, 1, 1,
                "   ", ctxOf("сегодня"));
        OffsetDateTime after = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(1);

        assertThat(saved.getRecordedAt()).isBetween(before, after);
    }

    @Test
    void saveFood_withIsoTimestampRecordedAt_parsesAndNormalizesToUtc() {
        clearUser(USER_A);
        // ISO-8601 с московским смещением (+03:00).
        FoodEntryEntity saved = tools.saveFood(USER_A, "X", 100, 1, 1, 1,
                "2026-07-03T23:30:00+03:00", ctxOf("в три ночи мск"));
        // Должно нормализоваться к 2026-07-03T20:30:00Z.
        assertThat(saved.getRecordedAt()).isEqualTo(
                OffsetDateTime.parse("2026-07-03T20:30:00Z"));
    }

    @Test
    void saveFood_withGarbageRecordedAt_throws() {
        clearUser(USER_A);
        assertThatThrownBy(() -> tools.saveFood(USER_A, "X", 100, 1, 1, 1,
                "вчера", ctxOf("вчера")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("YYYY-MM-DD");
        assertThatThrownBy(() -> tools.saveFood(USER_A, "X", 100, 1, 1, 1,
                "2026-13-40", ctxOf("битая дата")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readByTypeAndPeriod_findsEntriesAcrossMultipleDays() {
        clearUser(USER_A);

        // Три записи за разные дни.
        tools.saveFood(USER_A, "A", 100, 1, 1, 1, "2026-06-29", ctxOf("29"));
        tools.saveFood(USER_A, "B", 200, 1, 1, 1, "2026-07-01", ctxOf("1"));
        tools.saveFood(USER_A, "C", 300, 1, 1, 1, "2026-07-03", ctxOf("3"));

        List<? extends EntryEntity> wholeWeek = tools.readByTypeAndPeriod(USER_A, "FOOD",
                "2026-06-29T00:00:00Z", "2026-07-05T23:59:59Z");
        assertThat(wholeWeek).hasSize(3);

        // Только 1 июля.
        List<? extends EntryEntity> july1 = tools.readByTypeAndPeriod(USER_A, "FOOD",
                "2026-07-01T00:00:00Z", "2026-07-01T23:59:59Z");
        assertThat(july1).hasSize(1);
        assertThat(july1.getFirst().getName()).isEqualTo("B");
    }

    // ------------------------------------------------------------------
    //  Поиск за день, обновление и удаление
    // ------------------------------------------------------------------

    @Test
    void findEntriesByTypeAndDate_returnsOnlyThatDay() {
        clearUser(USER_A);
        tools.saveFood(USER_A, "Завтрак", 300, 10, 5, 40,
                "2026-07-01", ctxOf("утром"));
        tools.saveFood(USER_A, "Ужин", 500, 30, 20, 50,
                "2026-07-01", ctxOf("вечером"));
        tools.saveFood(USER_A, "Следующий день", 100, 1, 1, 1,
                "2026-07-02", ctxOf("на след день"));

        List<? extends EntryEntity> july1 = tools.findEntriesByTypeAndDate(
                USER_A, "FOOD", "2026-07-01");
        assertThat(july1).hasSize(2);
        assertThat(july1).extracting(e -> e.getName())
                .containsExactlyInAnyOrder("Завтрак", "Ужин");
    }

    @Test
    void findEntriesByTypeAndDate_blankDate_meansTodayUtc() {
        clearUser(USER_A);
        tools.saveFood(USER_A, "Сегодня", 100, 1, 1, 1, null, ctxOf("сегодня"));

        List<? extends EntryEntity> today = tools.findEntriesByTypeAndDate(
                USER_A, "FOOD", null);
        assertThat(today).hasSize(1);

        List<? extends EntryEntity> blank = tools.findEntriesByTypeAndDate(
                USER_A, "FOOD", "   ");
        assertThat(blank).hasSize(1);
    }

    @Test
    void findEntriesByTypeAndDate_otherTypesAreIsolated() {
        clearUser(USER_A);
        tools.saveFood(USER_A, "Еда", 100, 1, 1, 1, "2026-07-01", ctxOf("еда"));
        tools.saveActivity(USER_A, "Бег", 30, 200, "2026-07-01", ctxOf("бег"));
        tools.saveSleep(USER_A, 7.0, "2026-07-01", ctxOf("сон"));
        tools.saveWeight(USER_A, 80.0, "2026-07-01", ctxOf("вес"));

        assertThat(tools.findEntriesByTypeAndDate(USER_A, "FOOD", "2026-07-01")).hasSize(1);
        assertThat(tools.findEntriesByTypeAndDate(USER_A, "ACTIVITY", "2026-07-01")).hasSize(1);
        assertThat(tools.findEntriesByTypeAndDate(USER_A, "SLEEP", "2026-07-01")).hasSize(1);
        assertThat(tools.findEntriesByTypeAndDate(USER_A, "WEIGHT", "2026-07-01")).hasSize(1);
    }

    @Test
    void updateFood_updatesOnlyProvidedFields() {
        clearUser(USER_A);
        FoodEntryEntity saved = tools.saveFood(USER_A, "Каша", 200, 5, 3, 30,
                "2026-07-01", ctxOf("каша"));

        // Частичное обновление: только calories.
        {
            EntryEntity updated = tools.updateEntry(USER_A, "FOOD", saved.getId(),
                    null, 250, null, null, null, null, null, null, null, null);
            assertThat(((FoodEntryEntity) updated).getCalories()).isEqualTo(250);
            assertThat(((FoodEntryEntity) updated).getName()).isEqualTo("Каша");
            assertThat(((FoodEntryEntity) updated).getProteinGrams()).isEqualTo(5);
        }

        // Меняем имя + жиры.
        EntryEntity updated2 = tools.updateEntry(USER_A, "FOOD", saved.getId(),
                "Овсянка", null, null, 8, null, null, null, null, null, null);
        assertThat(updated2.getName()).isEqualTo("Овсянка");
        assertThat(((FoodEntryEntity) updated2).getFatGrams()).isEqualTo(8);
        assertThat(((FoodEntryEntity) updated2).getCalories()).isEqualTo(250);
    }

    @Test
    void updateActivity_updatesFields() {
        clearUser(USER_A);
        ActivityEntryEntity saved = tools.saveActivity(USER_A, "Бег", 30, 280,
                "2026-07-01", ctxOf("бег"));

        EntryEntity updated = tools.updateEntry(USER_A, "ACTIVITY", saved.getId(),
                null, null, null, null, null, 45, 420, null, null, null);
        assertThat(((ActivityEntryEntity) updated).getDurationMinutes()).isEqualTo(45);
        assertThat(((ActivityEntryEntity) updated).getCaloriesBurned()).isEqualTo(420);
    }

    @Test
    void updateSleep_updatesHours() {
        clearUser(USER_A);
        SleepEntryEntity saved = tools.saveSleep(USER_A, 6.0, "2026-07-01", ctxOf("сон"));

        EntryEntity updated = tools.updateEntry(USER_A, "SLEEP", saved.getId(),
                null, null, null, null, null, null, null, 8.5, null, null);
        assertThat(((SleepEntryEntity) updated).getHours()).isEqualTo(8.5);
    }

    @Test
    void updateWeight_updatesValueAndProfile() {
        clearUser(USER_A);
        WeightEntryEntity saved = tools.saveWeight(USER_A, 80.0,
                "2026-07-01", ctxOf("вес"));

        EntryEntity updated = tools.updateEntry(USER_A, "WEIGHT", saved.getId(),
                null, null, null, null, null, null, null, null, 79.5, null);
        assertThat(((WeightEntryEntity) updated).getKilograms()).isEqualTo(79.5);
        // Профиль обновился на новое значение.
        assertThat(userProfileRepository.findByTelegramUserId(USER_A)
                .map(UserProfileEntity::getWeightKg).orElse(null)).isEqualTo(79.5);
    }

    @Test
    void updateEntry_changesRecordedAt() {
        clearUser(USER_A);
        FoodEntryEntity saved = tools.saveFood(USER_A, "X", 100, 1, 1, 1,
                "2026-07-01", ctxOf("x"));

        OffsetDateTime expected = LocalDate.parse("2026-07-02")
                .atTime(12, 0).atOffset(ZoneOffset.UTC);
        EntryEntity moved = tools.updateEntry(USER_A, "FOOD", saved.getId(),
                null, null, null, null, null, null, null, null, null, "2026-07-02");
        assertThat(moved.getRecordedAt()).isEqualTo(expected);
    }

    @Test
    void updateEntry_rejectsForeignEntry() {
        clearUser(USER_A);
        clearUser(USER_B);
        FoodEntryEntity other = tools.saveFood(USER_B, "Чужое", 100, 1, 1, 1,
                "2026-07-01", ctxOf("x"));

        assertThatThrownBy(() -> tools.updateEntry(USER_A, "FOOD", other.getId(),
                null, 999, null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("другому пользователю");
    }

    @Test
    void updateEntry_rejectsUnknownId() {
        clearUser(USER_A);
        assertThatThrownBy(() -> tools.updateEntry(USER_A, "FOOD", 999_999L,
                null, null, null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не найдена");
    }

    @Test
    void deleteEntry_removesIt() {
        clearUser(USER_A);
        FoodEntryEntity saved = tools.saveFood(USER_A, "Удалить", 100, 1, 1, 1,
                null, ctxOf("x"));

        boolean ok = tools.deleteEntry(USER_A, "FOOD", saved.getId());
        assertThat(ok).isTrue();
        assertThat(foodRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void deleteEntry_returnsFalseWhenMissing() {
        assertThat(tools.deleteEntry(USER_A, "FOOD", 999_999L)).isFalse();
    }

    @Test
    void deleteEntry_rejectsForeignEntry() {
        clearUser(USER_B);
        FoodEntryEntity other = tools.saveFood(USER_B, "Чужое", 100, 1, 1, 1,
                null, ctxOf("x"));

        assertThatThrownBy(() -> tools.deleteEntry(USER_A, "FOOD", other.getId()))
                .isInstanceOf(IllegalArgumentException.class);
        // Чужая запись должна остаться на месте.
        assertThat(foodRepository.findById(other.getId())).isPresent();
    }

    @Test
    void deleteEntriesByTypeAndDate_removesAllForDay() {
        clearUser(USER_A);
        tools.saveFood(USER_A, "Завтрак", 300, 10, 5, 40,
                "2026-07-01", ctxOf("z"));
        tools.saveFood(USER_A, "Ужин", 500, 30, 20, 50,
                "2026-07-01", ctxOf("u"));
        tools.saveFood(USER_A, "Следующий день", 100, 1, 1, 1,
                "2026-07-02", ctxOf("next"));

        int removed = tools.deleteEntriesByTypeAndDate(USER_A, "FOOD", "2026-07-01");
        assertThat(removed).isEqualTo(2);

        // Осталась только запись за следующий день.
        List<? extends EntryEntity> left = tools.findEntriesByTypeAndDate(
                USER_A, "FOOD", "2026-07-02");
        assertThat(left).hasSize(1);
    }

    @Test
    void deleteEntriesByTypeAndDate_blankDateRemovesTodayOnly() {
        clearUser(USER_A);
        tools.saveFood(USER_A, "Сегодня", 100, 1, 1, 1, null, ctxOf("today"));
        tools.saveFood(USER_A, "Вчера", 200, 1, 1, 1,
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(2)
                        .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE),
                ctxOf("вчера"));

        int removed = tools.deleteEntriesByTypeAndDate(USER_A, "FOOD", null);
        assertThat(removed).isEqualTo(1);

        // Запись за вчера должна остаться.
        List<? extends EntryEntity> yesterday = tools.readByTypeAndPeriod(USER_A, "FOOD",
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(3).toString(),
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).toString());
        assertThat(yesterday).isNotEmpty();
    }

    @Test
    void deleteEntriesByTypeAndDate_zeroIfNothingMatches() {
        clearUser(USER_A);
        int removed = tools.deleteEntriesByTypeAndDate(USER_A, "FOOD", "2026-07-01");
        assertThat(removed).isZero();
    }
}