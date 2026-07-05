package ru.yourass.fitnessaibot.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ChatAction;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendChatAction;
import com.pengrad.telegrambot.request.SendMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.yourass.fitnessaibot.ai.FitnessAgent;
import ru.yourass.fitnessaibot.config.BotProperties;
import ru.yourass.fitnessaibot.entity.UserProfileEntity;
import ru.yourass.fitnessaibot.repository.UserProfileRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@ConditionalOnProperty(name = "fitness.bot.enabled", havingValue = "true", matchIfMissing = true)
public class FitnessBot {

    private static final Logger log = LoggerFactory.getLogger(FitnessBot.class);

    private final BotProperties botProperties;
    private final FitnessAgent agent;
    private final UserProfileRepository userProfileRepository;
    private final TelegramBot telegramBot;
    private final ExecutorService handlers = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "fitness-bot-handler");
        t.setDaemon(true);
        return t;
    });

    public FitnessBot(BotProperties botProperties,
                      FitnessAgent agent,
                      UserProfileRepository userProfileRepository) {
        this.botProperties = botProperties;
        this.agent = agent;
        this.userProfileRepository = userProfileRepository;
        this.telegramBot = new TelegramBot(botProperties.token());
    }

    /** true, если у пользователя заполнены все 4 поля, нужные для BMR/TDEE. */
    boolean hasBmrProfile(long telegramUserId) {
        return userProfileRepository.findByTelegramUserId(telegramUserId)
                .map(UserProfileEntity::hasBmrInputs)
                .orElse(false);
    }

    /** true, если у пользователя задана цель по весу. */
    boolean hasGoalWeight(long telegramUserId) {
        return userProfileRepository.findByTelegramUserId(telegramUserId)
                .map(p -> p.getGoalWeightKg() != null && p.getGoalWeightKg() > 0)
                .orElse(false);
    }

    /**
     * true, если у пользователя задан уровень ежедневной активности —
     * иначе BMR/TDEE будет считаться с дефолтом MODERATE.
     */
    boolean hasActivityInProfile(long telegramUserId) {
        return userProfileRepository.findByTelegramUserId(telegramUserId)
                .map(p -> p.getActivityLevel() != null)
                .orElse(false);
    }

    @PostConstruct
    public void start() {
        log.info("Registering Telegram bot @{} (enabled=true)", botProperties.username());
        telegramBot.setUpdatesListener(updates -> {
            for (Update u : updates) {
                handlers.submit(() -> handleUpdate(u));
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        }, this::onException);
    }

    @PreDestroy
    public void stop() {
        log.info("Stopping Telegram bot");
        try {
            telegramBot.removeGetUpdatesListener();
        } catch (Exception e) {
            log.warn("Error removing Telegram update listener: {}", e.getMessage());
        }
        handlers.shutdown();
    }

    private void onException(Exception ex) {
        log.error("Telegram polling exception", ex);
    }

    private void handleUpdate(Update update) {
        Message message = update.message();
        if (message == null) {
            return; // пропускаем отредактированные сообщения, callback-и и т.п.
        }

        long chatId = message.chat().id();
        String text = message.text();
        if (text == null || text.isBlank()) {
            send(chatId, """
                    Пришли, пожалуйста, текстовое сообщение.
                    Например: "в обед съел котлетки с пюрешкой".
                    """);
            return;
        }

        if (text.startsWith("/start") || text.equalsIgnoreCase("/help")) {
            StringBuilder body = new StringBuilder("""
                    👋 Привет! Я фитнес-ассистент.

                    Просто напиши в свободной форме, например:
                    • "на обед съел макароны с куриной котлетой"
                    • "походил на беговой дорожке 30 минут"
                    • "я сегодня спал 6 с половиной часов"
                    • "вешу 78.4 кг"

                    Я сам определю тип записи, оценю калории/БЖУ и сохраню.
                    А ещё могу ответить на вопросы по твоему журналу —
                    например: "сколько калорий я съел за неделю?".
                    """);
            if (!hasBmrProfile(chatId)) {
                body.append("\n💡 Расскажи ещё о себе (пол, возраст, рост, вес) — "
                        + "я смогу точнее считать калории и BMR/TDEE.");
            }
            if (!hasActivityInProfile(chatId)) {
                body.append("\n\n🚶 Сколько раз в неделю ты тренируешься? "
                        + "Малоподвижный / лёгкий (1-3) / умеренный (3-5) / высокий (6-7) / очень высокий — "
                        + "без этого BMR/TDEE считается «как для среднего», и норма калорий может врать.");
            }
            if (!hasGoalWeight(chatId)) {
                body.append("\n🎯 Если хочешь, можешь задать цель по весу в кг — "
                        + "тогда я буду видеть динамику «до цели осталось N кг».");
            }
            send(chatId, body.toString());
            return;
        }

        log.info("Processing message from {}: {}", chatId, text);
        sendTyping(chatId);
        String reply;
        try {
            reply = agent.handle(chatId, text);
            log.info("Reply for {}: {}", chatId, reply);
        } catch (Exception ex) {
            log.error("Agent failed for user {}", chatId, ex);
            reply = "⚠️ Не получилось обработать сообщение: " + ex.getMessage();
        }
        send(chatId, reply);
    }

    private void send(long chatId, String text) {
        try {
            SendMessage req = new SendMessage(chatId, text).parseMode(ParseMode.Markdown);
            telegramBot.execute(req);
        } catch (Exception e) {
            log.error("Failed to send Telegram message to {}", chatId, e);
        }
    }

    /** Показывает «typing…» в шапке чата на время обработки запроса LLM. */
    private void sendTyping(long chatId) {
        try {
            telegramBot.execute(new SendChatAction(chatId, ChatAction.typing));
        } catch (Exception e) {
            log.warn("Failed to send typing action to {}: {}", chatId, e.getMessage());
        }
    }

    /** Для тестов: возвращает клиента бота. */
    public TelegramBot telegramBot() {
        return telegramBot;
    }
}
