package ru.yourass.fitnessaibot.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Единый агент-оркестратор фитнес-трекера.
 *
 * <p>Агент сам решает, что делать с сообщением пользователя:</p>
 * <ul>
 *   <li>если сообщение — это данные о еде/активности/сне/весе, агент вызовет
 *       соответствующий write-тул ({@link EntryTools#saveFood} и т.п.);</li>
 *   <li>если пользователь задаёт вопрос про свой журнал («сколько калорий
 *       за неделю», «когда я последний раз бегал»), агент вызовет read-тулы
 *       ({@link EntryTools#readByTypeAndPeriod}) и проанализирует результат;</li>
 *   <li>если вопрос свободный — агент ответит текстом на основе контекста.</li>
 * </ul>
 *
 * <p>Идентификатор пользователя Telegram всегда первым аргументом у каждого
 * тула — это требование продиктовано тем, что модель не знает id иначе как
 * из контекста диалога. Агент сам подставляет этот id в user-prompt
 * (префикс {@code tg:{id}: …}), а описание тула ссылается на «идентификатор
 * пользователя».</p>
 */
@Component
public class FitnessAgent {

    private static final Logger log = LoggerFactory.getLogger(FitnessAgent.class);

    private static final String SYSTEM_PROMPT_RESOURCE = "fitness-agent-system-prompt.txt";
    private static final String SYSTEM_PROMPT = readClasspathResource().strip();
    private static final int MAX_MESSAGES = 20;

    private static String readClasspathResource() {
        ClassPathResource resource = new ClassPathResource(FitnessAgent.SYSTEM_PROMPT_RESOURCE);
        if (!resource.exists()) {
            throw new IllegalStateException(
                    "Не найден classpath-ресурс '" + FitnessAgent.SYSTEM_PROMPT_RESOURCE
                            + "' с системным промптом агента. "
                            + "Создайте его в src/main/resources/.");
        }
        StringBuilder sb = new StringBuilder();
        try (InputStream in = resource.getInputStream();
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            char[] buf = new char[1024];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Не удалось прочитать ресурс '" + FitnessAgent.SYSTEM_PROMPT_RESOURCE + "': " + ex.getMessage(), ex);
        }
        return sb.toString();
    }

    private final ChatClient chatClient;
    private final ProfileContextBuilder profileContextBuilder;

    public FitnessAgent(ChatClient.Builder builder,
                        EntryTools entryTools,
                        ProfileContextBuilder profileContextBuilder) {
        this.profileContextBuilder = profileContextBuilder;

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(MAX_MESSAGES)
                .build();
        MessageChatMemoryAdvisor memoryAdvisor =
                MessageChatMemoryAdvisor.builder(chatMemory).build();

        this.chatClient = builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(entryTools)
                .defaultAdvisors(memoryAdvisor)
                .build();
    }

    /**
     * Обрабатывает сообщение от пользователя.
     *
     * @param telegramUserId идентификатор пользователя в Telegram
     * @param userMessage    произвольный текст сообщения
     * @return ответ, который агент сгенерировал с учётом вызванных тулов
     */
    public String handle(long telegramUserId, String userMessage) {
        log.info("Agent handle: user={} text='{}'", telegramUserId, userMessage);
        String nowUtc = OffsetDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String profileCtx = profileContextBuilder.build(telegramUserId);
        String wrapped = "now=" + nowUtc + " " + profileCtx + ": " + userMessage;
        String raw = chatClient.prompt()
                .user(wrapped)
                .toolContext(Map.of(
                        EntryTools.CTX_TELEGRAM_USER_ID, telegramUserId,
                        EntryTools.CTX_SOURCE_MESSAGE, userMessage))
                .advisors(a -> a.param(
                        org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID,
                        telegramUserId))
                .call()
                .content();
        String cleaned = cleanResponse(raw);
        log.info("Agent reply for {}: {}", telegramUserId, cleaned);
        return cleaned;
    }

    /**
     * Очищает ответ модели от служебных блоков перед отправкой пользователю:
     * удаляет {@code <think>…</think>} блоки рассуждений (chain-of-thought),
     * которые некоторые провайдеры добавляют по умолчанию, и схлопывает
     * whitespace по краям.
     */
    static String cleanResponse(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = Pattern.compile("<think>.*?</think>", Pattern.DOTALL).matcher(raw).replaceAll("").strip();
        return cleaned.isEmpty() ? "" : cleaned;
    }
}