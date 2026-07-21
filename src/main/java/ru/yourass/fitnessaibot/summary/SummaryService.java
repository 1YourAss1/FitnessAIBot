package ru.yourass.fitnessaibot.summary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.yourass.fitnessaibot.ai.FitnessAgent;
import ru.yourass.fitnessaibot.bot.FitnessBot;

/**
 * Формирует и отправляет пользователю автоматические сводки за день и за неделю.
 *
 * <p>Анализ делает {@link FitnessAgent} через read-тул {@code readByTypeAndPeriod}.</p>
 *
 * <p>Если в БД у пользователя нет записей за период, агент сам напишет «записей нет».</p>
 */
@Service
@ConditionalOnProperty(name = "fitness.bot.enabled", havingValue = "true", matchIfMissing = true)
public class SummaryService {

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

    private final FitnessAgent agent;
    private final FitnessBot bot;

    public SummaryService(FitnessAgent agent, FitnessBot bot) {
        this.agent = agent;
        this.bot = bot;
    }

    /** Отправляет суточный итог пользователю. */
    public void sendDailySummary(long telegramUserId) {
        String prompt = """
                Составь краткий итог за СЕГОДНЯШНИЙ календарный день (UTC).

                Шаги:
                1. Для типов FOOD, ACTIVITY, SLEEP вызови readByTypeAndPeriod \
                с окном «сегодня 00:00:00 UTC — сегодня 23:59:59 UTC».
                2. Сопоставь потреблённые и потраченные ккал с tdee.
                3. Выдели наименее полезный прием пищи и посоветуй как его стоило бы заменить.

                Формат ответа (Telegram rich markdown):
                - Не больше 12 строк суммарно.
                - Одна Markdown-таблица: День | ккал еда | ккал акт. | сон.
                - Один-два вывода или рекомендации текстом.
                - Если записей нет — одной фразой «Сегодня записей нет» \
                и подсказка, что можно записать (еда / активность / сон).

                НЕ выдумывай числа — используй только то, что вернули тулы и что есть в profile.
                НЕ сохраняй никаких новых записей — это задача только для чтения.
                """;
        send(telegramUserId, prompt);
    }

    /** Отправляет недельный итог пользователю. */
    public void sendWeeklySummary(long telegramUserId) {
        String prompt = """
                Составь краткий итог за ПОСЛЕДНИЕ 7 ДНЕЙ (UTC-окно: now-7д ... now).

                Шаги:
                1. Для типов FOOD, ACTIVITY, SLEEP, WEIGHT вызови readByTypeAndPeriod \
                за окно «7 дней назад 00:00 UTC — сейчас».
                2. Если в profile задан goal= — сопоставь с динамикой веса за неделю.
                3. Если данных мало (1-2 дня из 7) — упомяни это в выводе.

                Формат ответа (Telegram rich markdown):
                - Заголовок «## Итоги за неделю».
                - Таблица «День | ккал еда | Б/Ж/У | активность | сон» по дням.
                - Свободные ячейки заполняй прочерком «—» только если за день \
                нет ни одной записи соответствующего типа.
                - Сводная строка «Всего / в среднем в день».
                - Один-два вывода (например, про сон или баланс ккал).
                - Не больше 20 строк суммарно.

                НЕ выдумывай числа — используй только то, что вернули тулы и что есть в profile.
                НЕ сохраняй никаких новых записей — это задача только для чтения.
                """;
        send(telegramUserId, prompt);
    }

    private void send(long telegramUserId, String prompt) {
        log.info("Summary: user={} prompt_len={}", telegramUserId, prompt.length());
        String reply;
        try {
            reply = agent.handle(telegramUserId, prompt);
        } catch (Exception ex) {
            log.error("Summary agent failed for user {}", telegramUserId, ex);
            reply = "## ⚠️ Не получилось составить сводку\n\n"
                    + "Ошибка: `" + ex.getMessage() + "`";
        }
        bot.send(telegramUserId, reply);
    }
}