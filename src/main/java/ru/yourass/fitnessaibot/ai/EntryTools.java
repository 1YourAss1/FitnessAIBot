package ru.yourass.fitnessaibot.ai;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.yourass.fitnessaibot.entity.ActivityEntryEntity;
import ru.yourass.fitnessaibot.entity.ActivityLevel;
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
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Набор инструментов (tools) для фитнес-агента.
 */
@Component
@RequiredArgsConstructor
public class EntryTools {

    public static final String CTX_TELEGRAM_USER_ID = "telegramUserId";
    public static final String CTX_SOURCE_MESSAGE = "sourceMessage";

    private static final String FOOD = "FOOD";
    private static final String ACTIVITY = "ACTIVITY";
    private static final String SLEEP = "SLEEP";
    private static final String WEIGHT = "WEIGHT";

    /** Формат сообщения для ненайденной записи: {@code "Запись %s id=%d не найдена"}. */
    private static final String ENTRY_NOT_FOUND_FORMAT = "Запись %s id=%d не найдена";
    /** Формат сообщения для чужой записи: {@code "Запись id=%d принадлежит другому пользователю"}. */
    private static final String ENTRY_FOREIGN_OWNER_FORMAT = "Запись id=%d принадлежит другому пользователю";

    private static final Logger log = LoggerFactory.getLogger(EntryTools.class);
    private static final double DEFAULT_WEIGHT_KG = 70.0;

    private final FoodEntryRepository foodRepository;
    private final ActivityEntryRepository activityRepository;
    private final SleepEntryRepository sleepRepository;
    private final WeightEntryRepository weightRepository;
    private final UserProfileRepository userProfileRepository;

    @Tool(description = "Сохраняет запись о приёме пищи. Вызывай, когда пользователь сообщает что съел.")
    @Transactional
    public FoodEntryEntity saveFood(
            @ToolParam(description = "Краткое название блюда/продукта") String name,
            @ToolParam(description = "Калорийность порции, ккал — целое число") Integer calories,
            @ToolParam(description = "Белки, грамм — целое число") Integer proteinGrams,
            @ToolParam(description = "Жиры, грамм — целое число") Integer fatGrams,
            @ToolParam(description = "Углеводы, грамм — целое число") Integer carbsGrams,
            @ToolParam(description = "Дата записи в формате 'YYYY-MM-DD' (UTC-день). "
                    + "null или пустая строка — сегодня по UTC.",
                    required = false) String recordedAt,
            ToolContext toolContext) {
        Long telegramUserId = telegramUserIdFromContext(toolContext);
        requireNonNull(calories, "calories");
        requireNonNull(proteinGrams, "proteinGrams");
        requireNonNull(fatGrams, "fatGrams");
        requireNonNull(carbsGrams, "carbsGrams");
        FoodEntryEntity entity = new FoodEntryEntity(telegramUserId, safeName(name),
                calories, proteinGrams, fatGrams, carbsGrams);
        applyRecordedAt(entity, recordedAt);
        applySourceMessage(entity, toolContext);
        FoodEntryEntity saved = foodRepository.save(entity);
        log.info("Tool saveFood: user={} id={} '{}' {}kkal at={}",
                telegramUserId, saved.getId(), saved.getName(), calories, saved.getRecordedAt());
        return saved;
    }

    @Tool(description = "Сохраняет запись о физической активности. Вызывай, когда пользователь сообщает "
            + "о тренировке, прогулке, беге, велосипеде, плавании и т.п.")
    @Transactional
    public ActivityEntryEntity saveActivity(
            @ToolParam(description = "Название активности") String name,
            @ToolParam(description = "Длительность, минуты — целое число") Integer durationMinutes,
            @ToolParam(description = "Сожжённые калории, ккал — целое число") Integer caloriesBurned,
            @ToolParam(description = "Дата записи в формате 'YYYY-MM-DD' (UTC-день). "
                    + "null или пустая строка — сегодня по UTC.",
                    required = false) String recordedAt,
            ToolContext toolContext) {
        Long telegramUserId = telegramUserIdFromContext(toolContext);
        requireNonNull(durationMinutes, "durationMinutes");
        requireNonNull(caloriesBurned, "caloriesBurned");
        ActivityEntryEntity entity = new ActivityEntryEntity(telegramUserId, safeName(name),
                durationMinutes, caloriesBurned);
        applyRecordedAt(entity, recordedAt);
        applySourceMessage(entity, toolContext);
        ActivityEntryEntity saved = activityRepository.save(entity);
        log.info("Tool saveActivity: user={} id={} '{}' {}min {}kkal at={}",
                telegramUserId, saved.getId(), saved.getName(), durationMinutes,
                caloriesBurned, saved.getRecordedAt());
        return saved;
    }

    @Tool(description = "Сохраняет запись о сне. Вызывай, когда пользователь сообщает сколько спал. "
            + "Обычно сон логируется за вчерашнюю ночь — если пользователь не указал дату, "
            + "а сейчас утро — записывай за вчерашний день.")
    @Transactional
    public SleepEntryEntity saveSleep(
            @ToolParam(description = "Часы сна — дробное число, например 6.5") Double hours,
            @ToolParam(description = "Дата записи в формате 'YYYY-MM-DD' (UTC-день). "
                    + "null или пустая строка — сегодня по UTC.",
                    required = false) String recordedAt,
            ToolContext toolContext) {
        Long telegramUserId = telegramUserIdFromContext(toolContext);
        requireNonNull(hours, "hours");
        SleepEntryEntity entity = new SleepEntryEntity(telegramUserId, hours);
        applyRecordedAt(entity, recordedAt);
        applySourceMessage(entity, toolContext);
        SleepEntryEntity saved = sleepRepository.save(entity);
        log.info("Tool saveSleep: user={} id={} {}h at={}",
                telegramUserId, saved.getId(), hours, saved.getRecordedAt());
        return saved;
    }

    @Tool(description = "Сохраняет запись о весе пользователя. Вызывай, когда пользователь сообщает свой вес. "
            + "Также обновляет сохранённый вес в профиле — он используется для расчёта калорий при активностях.")
    @Transactional
    public WeightEntryEntity saveWeight(
            @ToolParam(description = "Вес в килограммах — дробное число") Double kilograms,
            @ToolParam(description = "Дата записи в формате 'YYYY-MM-DD' (UTC-день). "
                    + "null или пустая строка — сегодня по UTC.",
                    required = false) String recordedAt,
            ToolContext toolContext) {
        Long telegramUserId = telegramUserIdFromContext(toolContext);
        requireNonNull(kilograms, "kilograms");
        if (kilograms <= 0 || kilograms > 500) {
            throw new IllegalArgumentException("Подозрительное значение веса: " + kilograms);
        }
        WeightEntryEntity entity = new WeightEntryEntity(telegramUserId, kilograms);
        applyRecordedAt(entity, recordedAt);
        applySourceMessage(entity, toolContext);
        WeightEntryEntity saved = weightRepository.save(entity);

        UserProfileEntity profile = userProfileRepository.findByTelegramUserId(telegramUserId)
                .orElseGet(() -> new UserProfileEntity(telegramUserId, null, null));
        profile.setWeightKg(kilograms);
        userProfileRepository.save(profile);

        log.info("Tool saveWeight: user={} id={} {}kg at={}",
                telegramUserId, saved.getId(), kilograms, saved.getRecordedAt());
        return saved;
    }

    @Tool(description = "Возвращает записи конкретного типа за период. "
            + "Вызывай для аналитики: «сколько калорий за неделю», «средний сон за месяц» и т.п.")
    public List<? extends EntryEntity> readByTypeAndPeriod(
            @ToolParam(description = "Тип записи: одно из FOOD, ACTIVITY, SLEEP, WEIGHT") String type,
            @ToolParam(description = "Начало периода, ISO-8601 (например 2026-06-23T00:00:00Z)") String from,
            @ToolParam(description = "Конец периода, ISO-8601 (например 2026-06-30T23:59:59Z)") String to,
            ToolContext toolContext) {
        Long telegramUserId = telegramUserIdFromContext(toolContext);
        String normalizedType = type.trim().toUpperCase();
        OffsetDateTime fromTs = OffsetDateTime.parse(from).withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime toTs = OffsetDateTime.parse(to).withOffsetSameInstant(ZoneOffset.UTC);
        List<? extends EntryEntity> result = switch (normalizedType) {
            case FOOD -> foodRepository.findByTelegramUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(
                    telegramUserId, fromTs, toTs);
            case ACTIVITY -> activityRepository.findByTelegramUserIdAndRecordedAtBetweenOrderByRecordedAtAsc(
                    telegramUserId, fromTs, toTs);
            case SLEEP -> sleepRepository.findByTelegramUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(
                    telegramUserId, fromTs, toTs);
            case WEIGHT -> weightRepository.findByTelegramUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(
                    telegramUserId, fromTs, toTs);
            default -> throw new IllegalArgumentException("Неизвестный тип: " + type);
        };
        log.info("Tool readByTypeAndPeriod: user={} type={} from={} to={} count={}",
                telegramUserId, normalizedType, fromTs, toTs, result.size());
        return result;
    }

    @Tool(description = "Возвращает текущий сохранённый вес пользователя в килограммах. "
            + "Если вес ещё не сообщался — вернётся 70.0 (значение по умолчанию). "
            + "Вызывай перед расчётом caloriesBurned в saveActivity, если вес не указан в сообщении.")
    public double readCurrentWeightKg(ToolContext toolContext) {
        Long telegramUserId = telegramUserIdFromContext(toolContext);
        double weight = userProfileRepository.findByTelegramUserId(telegramUserId)
                .map(UserProfileEntity::getWeightKg)
                .filter(w -> w > 0)
                .orElse(DEFAULT_WEIGHT_KG);
        log.info("Tool readCurrentWeightKg: user={} weight={}", telegramUserId, weight);
        return weight;
    }

    @Tool(description = "Возвращает записи конкретного типа за КАЛЕНДАРНЫЙ ДЕНЬ (UTC). "
            + "Используй ПЕРЕД сохранением, чтобы проверить, нет ли уже такой записи — "
            + "если пользователь случайно повторяет ввод или хочет исправить уже записанное.")
    public List<? extends EntryEntity> findEntriesByTypeAndDate(
            @ToolParam(description = "Тип записи: FOOD, ACTIVITY, SLEEP, WEIGHT") String type,
            @ToolParam(description = "Дата в формате 'YYYY-MM-DD' (UTC-день). "
                    + "null или пустая строка — сегодня по UTC.",
                    required = false) String date,
            ToolContext toolContext) {
        Long telegramUserId = telegramUserIdFromContext(toolContext);
        String normalizedType = type.trim().toUpperCase();
        LocalDate day = (date == null || date.isBlank())
                ? LocalDate.now(ZoneOffset.UTC)
                : LocalDate.parse(date.trim());
        OffsetDateTime from = day.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime to = day.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC).minusNanos(1);
        List<? extends EntryEntity> result = switch (normalizedType) {
            case FOOD -> foodRepository
                    .findByTelegramUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(telegramUserId, from, to);
            case ACTIVITY -> activityRepository
                    .findByTelegramUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(telegramUserId, from, to);
            case SLEEP -> sleepRepository
                    .findByTelegramUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(telegramUserId, from, to);
            case WEIGHT -> weightRepository
                    .findByTelegramUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(telegramUserId, from, to);
            default -> throw new IllegalArgumentException("Неизвестный тип: " + type);
        };
        log.info("Tool findEntriesByTypeAndDate: user={} type={} date={} count={}",
                telegramUserId, normalizedType, day, result.size());
        return result;
    }

    @Tool(description = "Обновляет ПОЛЯ существующей записи по её id (запись должна принадлежать "
            + "этому telegramUserId). Все параметры полей опциональны — передавай только те, "
            + "которые нужно изменить. Неизвестные id — ошибка. "
            + "Тип записи: FOOD, ACTIVITY, SLEEP, WEIGHT. "
            + "Для FOOD обновляемые поля: name, calories, proteinGrams, fatGrams, carbsGrams. "
            + "Для ACTIVITY: name, durationMinutes, caloriesBurned. "
            + "Для SLEEP: hours. Для WEIGHT: kilograms.")
    @Transactional
    public EntryEntity updateEntry(
            @ToolParam(description = "Тип записи: FOOD, ACTIVITY, SLEEP, WEIGHT") String type,
            @ToolParam(description = "id обновляемой записи") Long id,
            @ToolParam(description = "Новое название (для FOOD/ACTIVITY). null — не менять.",
                    required = false) String name,
            @ToolParam(description = "Новая калорийность (FOOD). null — не менять.",
                    required = false) Integer calories,
            @ToolParam(description = "Новые белки, г (FOOD). null — не менять.",
                    required = false) Integer proteinGrams,
            @ToolParam(description = "Новые жиры, г (FOOD). null — не менять.",
                    required = false) Integer fatGrams,
            @ToolParam(description = "Новые углеводы, г (FOOD). null — не менять.",
                    required = false) Integer carbsGrams,
            @ToolParam(description = "Новая длительность, мин (ACTIVITY). null — не менять.",
                    required = false) Integer durationMinutes,
            @ToolParam(description = "Новые сожжённые ккал (ACTIVITY). null — не менять.",
                    required = false) Integer caloriesBurned,
            @ToolParam(description = "Новые часы сна (SLEEP). null — не менять.",
                    required = false) Double hours,
            @ToolParam(description = "Новый вес, кг (WEIGHT). null — не менять.",
                    required = false) Double kilograms,
            @ToolParam(description = "Новая дата записи в формате 'YYYY-MM-DD' (UTC-день). "
                    + "null — не менять.", required = false) String recordedAt,
            ToolContext toolContext) {
        Long telegramUserId = telegramUserIdFromContext(toolContext);
        requireNonNull(id, "id");
        String normalizedType = type.trim().toUpperCase();
        EntryEntity updated = switch (normalizedType) {
            case FOOD -> {
                FoodEntryEntity e = foodRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException(
                                String.format(ENTRY_NOT_FOUND_FORMAT, FOOD, id)));
                ensureOwner(e, telegramUserId);
                if (name != null) e.setName(safeName(name));
                if (calories != null) e.setCalories(calories);
                if (proteinGrams != null) e.setProteinGrams(proteinGrams);
                if (fatGrams != null) e.setFatGrams(fatGrams);
                if (carbsGrams != null) e.setCarbsGrams(carbsGrams);
                if (recordedAt != null && !recordedAt.isBlank()) {
                    e.setRecordedAt(parseRecordedAt(recordedAt));
                }
                yield foodRepository.save(e);
            }
            case ACTIVITY -> {
                ActivityEntryEntity e = activityRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException(
                                String.format(ENTRY_NOT_FOUND_FORMAT, ACTIVITY, id)));
                ensureOwner(e, telegramUserId);
                if (name != null) e.setName(safeName(name));
                if (durationMinutes != null) e.setDurationMinutes(durationMinutes);
                if (caloriesBurned != null) e.setCaloriesBurned(caloriesBurned);
                if (recordedAt != null && !recordedAt.isBlank()) {
                    e.setRecordedAt(parseRecordedAt(recordedAt));
                }
                yield activityRepository.save(e);
            }
            case SLEEP -> {
                SleepEntryEntity e = sleepRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException(
                                String.format(ENTRY_NOT_FOUND_FORMAT, SLEEP, id)));
                ensureOwner(e, telegramUserId);
                if (hours != null) e.setHours(hours);
                if (recordedAt != null && !recordedAt.isBlank()) {
                    e.setRecordedAt(parseRecordedAt(recordedAt));
                }
                yield sleepRepository.save(e);
            }
            case WEIGHT -> {
                WeightEntryEntity e = weightRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException(
                                String.format(ENTRY_NOT_FOUND_FORMAT, WEIGHT, id)));
                ensureOwner(e, telegramUserId);
                if (kilograms != null) {
                    if (kilograms <= 0 || kilograms > 500) {
                        throw new IllegalArgumentException("Подозрительное значение веса: " + kilograms);
                    }
                    e.setKilograms(kilograms);
                    UserProfileEntity profile = userProfileRepository.findByTelegramUserId(telegramUserId)
                            .orElseGet(() -> new UserProfileEntity(telegramUserId, null, null));
                    profile.setWeightKg(kilograms);
                    userProfileRepository.save(profile);
                }
                if (recordedAt != null && !recordedAt.isBlank()) {
                    e.setRecordedAt(parseRecordedAt(recordedAt));
                }
                yield weightRepository.save(e);
            }
            default -> throw new IllegalArgumentException("Неизвестный тип: " + type);
        };
        log.info("Tool updateEntry: user={} type={} id={}", telegramUserId, normalizedType, id);
        return updated;
    }

    @Tool(description = "Удаляет запись по её id. Запись должна принадлежать этому пользователю "
            + "(telegramUserId), иначе ошибка. Тип записи: FOOD, ACTIVITY, SLEEP, WEIGHT. "
            + "Возвращает true, если запись была удалена; false, если не найдена.")
    @Transactional
    public boolean deleteEntry(
            @ToolParam(description = "Тип записи: FOOD, ACTIVITY, SLEEP, WEIGHT") String type,
            @ToolParam(description = "id удаляемой записи") Long id,
            ToolContext toolContext) {
        Long telegramUserId = telegramUserIdFromContext(toolContext);
        requireNonNull(id, "id");
        String normalizedType = type.trim().toUpperCase();
        boolean deleted = switch (normalizedType) {
            case FOOD -> {
                FoodEntryEntity e = foodRepository.findById(id).orElse(null);
                if (e == null) yield false;
                ensureOwner(e, telegramUserId);
                foodRepository.delete(e);
                yield true;
            }
            case ACTIVITY -> {
                ActivityEntryEntity e = activityRepository.findById(id).orElse(null);
                if (e == null) yield false;
                ensureOwner(e, telegramUserId);
                activityRepository.delete(e);
                yield true;
            }
            case SLEEP -> {
                SleepEntryEntity e = sleepRepository.findById(id).orElse(null);
                if (e == null) yield false;
                ensureOwner(e, telegramUserId);
                sleepRepository.delete(e);
                yield true;
            }
            case WEIGHT -> {
                WeightEntryEntity e = weightRepository.findById(id).orElse(null);
                if (e == null) yield false;
                ensureOwner(e, telegramUserId);
                weightRepository.delete(e);
                yield true;
            }
            default -> throw new IllegalArgumentException("Неизвестный тип: " + type);
        };
        log.info("Tool deleteEntry: user={} type={} id={}deleted={}",
                telegramUserId, normalizedType, id, deleted);
        return deleted;
    }

    @Tool(description = "Удаляет ВСЕ записи указанного типа за КАЛЕНДАРНЫЙ ДЕНЬ (UTC). "
            + "Используй, когда пользователь хочет, например, «стереть всё за вчера по еде». "
            + "Возвращает количество удалённых записей.")
    @Transactional
    public int deleteEntriesByTypeAndDate(
            @ToolParam(description = "Тип записи: FOOD, ACTIVITY, SLEEP, WEIGHT") String type,
            @ToolParam(description = "Дата в формате 'YYYY-MM-DD' (UTC-день). "
                    + "null или пустая строка — сегодня по UTC.",
                    required = false) String date,
            ToolContext toolContext) {
        Long telegramUserId = telegramUserIdFromContext(toolContext);
        String normalizedType = type.trim().toUpperCase();
        LocalDate day = (date == null || date.isBlank())
                ? LocalDate.now(ZoneOffset.UTC)
                : LocalDate.parse(date.trim());
        OffsetDateTime from = day.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime to = day.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC).minusNanos(1);
        List<? extends EntryEntity> list = switch (normalizedType) {
            case FOOD -> foodRepository
                    .findByTelegramUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(telegramUserId, from, to);
            case ACTIVITY -> activityRepository
                    .findByTelegramUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(telegramUserId, from, to);
            case SLEEP -> sleepRepository
                    .findByTelegramUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(telegramUserId, from, to);
            case WEIGHT -> weightRepository
                    .findByTelegramUserIdAndRecordedAtBetweenOrderByRecordedAtDesc(telegramUserId, from, to);
            default -> throw new IllegalArgumentException("Неизвестный тип: " + type);
        };
        int count = list.size();
        for (EntryEntity e : list) {
            switch (normalizedType) {
                case FOOD -> foodRepository.deleteById(e.getId());
                case ACTIVITY -> activityRepository.deleteById(e.getId());
                case SLEEP -> sleepRepository.deleteById(e.getId());
                case WEIGHT -> weightRepository.deleteById(e.getId());
                default -> { /* unreachable */ }
            }
        }
        log.info("Tool deleteEntriesByTypeAndDate: user={} type={} date={} removed={}",
                telegramUserId, normalizedType, day, count);
        return count;
    }

    private static void ensureOwner(EntryEntity entity, long telegramUserId) {
        if (entity.getTelegramUserId() != telegramUserId) {
            throw new IllegalArgumentException(
                    String.format(ENTRY_FOREIGN_OWNER_FORMAT, entity.getId()));
        }
    }

    @Tool(description = "Сохраняет или обновляет параметры профиля пользователя: пол, возраст, рост, "
            + "текущий вес, целевой вес и уровень ежедневной активности. "
            + "Любой из параметров может быть null — тогда он не обновляется. "
            + "Эти данные нужны для более точного расчёта калорий (MET × вес × часы), "
            + "BMR/TDEE по формуле Миффлина-Сан Жеора и для оценки прогресса к цели по весу.")
    @Transactional
    public UserProfileEntity saveProfile(
            @ToolParam(description = "Пол: MALE или FEMALE, либо строка на русском (мужской/женский). null — не менять.",
                    required = false) String gender,
            @ToolParam(description = "Возраст в полных годах, целое число 1-120. null — не менять.",
                    required = false) Integer age,
            @ToolParam(description = "Рост в сантиметрах, дробное число 50-250. null — не менять.",
                    required = false) Double heightCm,
            @ToolParam(description = "Текущий вес в килограммах, дробное число 20-500. null — не менять.",
                    required = false) Double weightKg,
            @ToolParam(description = "Целевой вес в килограммах, дробное число 20-500. "
                    + "null — не менять. Если у пользователя сейчас нет цели по весу, "
                    + "просто НЕ передавай этот параметр.",
                    required = false) Double goalWeightKg,
            @ToolParam(description = "Уровень активности: SEDENTARY, LIGHT, MODERATE, ACTIVE, VERY_ACTIVE. "
                    + "null — не менять. Используется для перевода BMR в TDEE.",
                    required = false) ActivityLevel activityLevel,
            ToolContext toolContext) {
        Long telegramUserId = telegramUserIdFromContext(toolContext);
        Gender parsedGender = parseGender(gender);
        if (age != null && (age < 1 || age > 120)) {
            throw new IllegalArgumentException("Подозрительный возраст: " + age);
        }
        if (heightCm != null && (heightCm < 50 || heightCm > 250)) {
            throw new IllegalArgumentException("Подозрительный рост: " + heightCm);
        }
        if (weightKg != null && (weightKg <= 0 || weightKg > 500)) {
            throw new IllegalArgumentException("Подозрительный вес: " + weightKg);
        }
        if (goalWeightKg != null && (goalWeightKg <= 0 || goalWeightKg > 500)) {
            throw new IllegalArgumentException("Подозрительный целевой вес: " + goalWeightKg);
        }

        UserProfileEntity profile = userProfileRepository.findByTelegramUserId(telegramUserId)
                .orElseGet(() -> new UserProfileEntity(telegramUserId, null, null));
        if (parsedGender != null) {
            profile.setGender(parsedGender);
        }
        if (age != null) {
            profile.setAge(age);
        }
        if (heightCm != null) {
            profile.setHeightCm(heightCm);
        }
        if (weightKg != null) {
            profile.setWeightKg(weightKg);
        }
        if (goalWeightKg != null) {
            profile.setGoalWeightKg(goalWeightKg);
        }
        if (activityLevel != null) {
            profile.setActivityLevel(activityLevel);
        }
        UserProfileEntity saved = userProfileRepository.save(profile);
        log.info("Tool saveProfile: user={} gender={} age={} heightCm={} weightKg={} goalKg={} activity={}",
                telegramUserId, saved.getGender(), saved.getAge(),
                saved.getHeightCm(), saved.getWeightKg(), saved.getGoalWeightKg(),
                saved.getActivityLevel());
        return saved;
    }

    @Tool(description = "Возвращает сохранённый профиль пользователя (пол, возраст, рост, вес). "
            + "Если профиля ещё нет — вернёт пустую запись. Используй, чтобы понять, какие данные "
            + "уже известны, и при необходимости попросить пользователя дополнить их.")
    public UserProfileEntity readProfile(ToolContext toolContext) {
        Long telegramUserId = telegramUserIdFromContext(toolContext);
        return userProfileRepository.findByTelegramUserId(telegramUserId)
                .orElseGet(() -> new UserProfileEntity(telegramUserId, null, null));
    }

    private static String safeName(String name) {
        return (name == null || name.isBlank()) ? "без названия" : name.trim();
    }

    /**
     * Извлекает идентификатор пользователя Telegram из {@link ToolContext}.
     * <p>Агент ({@link FitnessAgent}) кладёт реальный {@code chat.id} в
     * {@link #CTX_TELEGRAM_USER_ID} ДО вызова тула, и модель этот ключ не видит —
     * не может его «забыть» или подменить. Если ключа нет (тул вызван вне
     * Spring AI Tool Calling, например из юнит-теста без контекста) — это
     * серверная ошибка, бросаем {@link IllegalStateException}.</p>
     */
    private static Long telegramUserIdFromContext(ToolContext toolContext) {
        if (toolContext == null) {
            throw new IllegalStateException(
                    "Tool вызван без ToolContext — telegramUserId должен приходить "
                            + "из контекста, заполняемого FitnessAgent.");
        }
        Object raw = toolContext.getContext().get(CTX_TELEGRAM_USER_ID);
        if (raw instanceof Long id) {
            return id;
        }
        throw new IllegalStateException(
                "В ToolContext нет telegramUserId (тип=" + (raw == null ? "null" : raw.getClass().getSimpleName())
                        + "). Это серверная ошибка — проверь, что FitnessAgent.handle() "
                        + "кладёт CTX_TELEGRAM_USER_ID в .toolContext(...).");
    }

    /**
     * Бросает понятный {@link IllegalArgumentException}, если модель не передала
     * обязательный аргумент тул-метода. Без этого Spring AI через MethodHandle
     * упал бы в NPE при попытке распаковать {@code null} в примитив.
     */
    private static void requireNonNull(Object value, String paramName) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Модель не передала обязательный аргумент '" + paramName
                            + "'. Попробуй вызвать тул ещё раз с явным значением.");
        }
    }

    /**
     * Подставляет дату записи в сущность. Поддерживает два формата:
     * <ul>
     *   <li>{@code YYYY-MM-DD} — дата в UTC, время выставляется в 12:00 UTC
     *       (середина дня, чтобы избежать проблем с часовыми поясами при аналитике);</li>
     *   <li>полный ISO-8601 timestamp ({@code OffsetDateTime}) — берётся как есть.</li>
     * </ul>
     * Если строка пустая или {@code null} — ставится «сейчас» (UTC).
     *
     * @throws IllegalArgumentException при нераспознаваемом формате
     */
    private static void applyRecordedAt(EntryEntity entity, String recordedAt) {
        OffsetDateTime ts = parseRecordedAt(recordedAt);
        entity.setRecordedAt(ts);
    }

    /**
     * Парсит переданную моделью дату. Возвращает текущий момент UTC,
     * если на входе {@code null} или пустая строка.
     */
    static OffsetDateTime parseRecordedAt(String raw) {
        if (raw == null || raw.isBlank()) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
        String s = raw.trim();
        // Полный ISO timestamp — берём как есть.
        if (s.length() > 10) {
            try {
                return OffsetDateTime.parse(s).withOffsetSameInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException(
                        "Не удалось распознать дату записи '" + raw
                                + "'. Ожидается 'YYYY-MM-DD' или ISO-8601 timestamp.");
            }
        }
        // Короткая дата YYYY-MM-DD — ставим 12:00 UTC.
        try {
            LocalDate date = LocalDate.parse(s);
            return date.atTime(12, 0).atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(
                    "Не удалось распознать дату записи '" + raw
                            + "'. Ожидается формат 'YYYY-MM-DD', например '2026-07-01'.");
        }
    }

    /**
     * Подставляет в сущность исходный текст Telegram-сообщения, который
     * агент положил в {@link ToolContext}. Если контекста нет (например,
     * тул вызван из юнит-теста напрямую) — поле остаётся {@code null}.
     */
    private static void applySourceMessage(EntryEntity entity, ToolContext toolContext) {
        if (toolContext == null) {
            return;
        }
        Object raw = toolContext.getContext().get(CTX_SOURCE_MESSAGE);
        if (raw instanceof String text && !text.isBlank()) {
            entity.setSourceMessage(text);
        }
    }

    /**
     * Парсит строковое представление пола: MALE/FEMALE (латиницей) или
     * «мужской»/«женский» на русском. Регистр и пробелы игнорируются.
     * Возвращает {@code null}, если входная строка пуста.
     */
    private static Gender parseGender(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim().toLowerCase();
        return switch (s) {
            case "male", "m", "мужской", "муж", "м" -> Gender.MALE;
            case "female", "f", "женский", "жен", "ж" -> Gender.FEMALE;
            default -> throw new IllegalArgumentException(
                    "Не удалось распознать пол: " + raw);
        };
    }
}