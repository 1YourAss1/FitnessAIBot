package ru.yourass.fitnessaibot.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.BotCommand;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ChatAction;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendChatAction;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SetMyCommands;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.yourass.fitnessaibot.ai.FitnessAgent;
import ru.yourass.fitnessaibot.config.BotProperties;
import ru.yourass.fitnessaibot.entity.UserProfileEntity;
import ru.yourass.fitnessaibot.health.GoogleHealthOAuthService;
import ru.yourass.fitnessaibot.health.GoogleHealthSyncService;
import ru.yourass.fitnessaibot.health.GoogleHealthSyncService.SyncResult;
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
    private final GoogleHealthOAuthService googleHealthOAuth;
    private final GoogleHealthSyncService googleHealthSyncService;
    private final TelegramBot telegramBot;
    private final ExecutorService handlers = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "fitness-bot-handler");
        t.setDaemon(true);
        return t;
    });

    /** Для тестов: возвращает клиента бота. */
    public TelegramBot telegramBot() {
        return telegramBot;
    }

    public FitnessBot(BotProperties botProperties,
                      FitnessAgent agent,
                      UserProfileRepository userProfileRepository,
                      GoogleHealthOAuthService googleHealthOAuth,
                      GoogleHealthSyncService googleHealthSyncService) {
        this.botProperties = botProperties;
        this.agent = agent;
        this.userProfileRepository = userProfileRepository;
        this.googleHealthOAuth = googleHealthOAuth;
        this.googleHealthSyncService = googleHealthSyncService;
        this.telegramBot = new TelegramBot(botProperties.token());
    }

    @PostConstruct
    public void start() {
        log.info("Registering Telegram bot @{} (enabled=true)", botProperties.username());
        registerBotCommands();
        telegramBot.setUpdatesListener(updates -> {
            for (Update u : updates) {
                handlers.submit(() -> handleUpdate(u));
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        }, this::onException);
    }

    /** Регистрирует команды в меню Telegram (кнопка «/» рядом с полем ввода). */
    private void registerBotCommands() {
        BotCommand[] commands = new BotCommand[]{
                new BotCommand("start", "Приветствие и список команд"),
                new BotCommand("help", "То же, что /start"),
                new BotCommand("google_connect", "Подключить Google Health"),
                new BotCommand("google_disconnect", "Отвязать Google Health"),
                new BotCommand("google_status", "Статус подключения"),
                new BotCommand("google_sync", "Принудительная синхронизация")
        };
        try {
            telegramBot.execute(new SetMyCommands(commands));
        } catch (Exception e) {
            log.warn("Failed to register bot commands: {}", e.getMessage());
        }
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
        if (message.chat().type() != Chat.Type.Private) {
            return; // бот работает только в личных сообщениях
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
            handleStart(chatId);
            return;
        }

        if (text.equalsIgnoreCase("/google_connect")) {
            handleConnectGoogle(chatId);
            return;
        }
        if (text.equalsIgnoreCase("/google_disconnect")) {
            handleDisconnectGoogle(chatId);
            return;
        }
        if (text.equalsIgnoreCase("/google_status")) {
            handleGoogleStatus(chatId);
            return;
        }
        if (text.equalsIgnoreCase("/google_sync")) {
            handleGoogleSync(chatId);
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
        String prepared = prepareForMarkdown(text);
        try {
            SendMessage req = new SendMessage(chatId, prepared).parseMode(ParseMode.Markdown);
            telegramBot.execute(req);
        } catch (Exception e) {
            log.error("Failed to send Telegram message to {}", chatId, e);
        }
    }

    /**
     * Прогоняет текст через {@link StringBuilder} и экранирует ВСЕ Markdown-символы
     * ({@code _}, {@code *}, {@code `}, {@code [}) префиксом {@code \}. Это гарантирует,
     * что Telegram-парсер legacy Markdown не упадёт с 400 "can't parse entities"
     * на любом LLM-ответе, в т.ч. с битым форматированием. Минус — теряется
     * {@code *bold*}/{@code _italic_}/{@code `code`}/{@code [ссылка](url)}.
     */
    private static String prepareForMarkdown(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '_' || c == '*' || c == '`' || c == '[') sb.append('\\');
            sb.append(c);
        }
        return sb.toString();
    }

    /** Показывает «typing…» в шапке чата на время обработки запроса LLM. */
    private void sendTyping(long chatId) {
        try {
            telegramBot.execute(new SendChatAction(chatId, ChatAction.typing));
        } catch (Exception e) {
            log.warn("Failed to send typing action to {}: {}", chatId, e.getMessage());
        }
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

    // ==================== Команды ====================

    private void handleStart(long chatId) {
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
            body.append("""
                    
                    💡 Расскажи ещё о себе (пол, возраст, рост, вес) —
                    я смогу точнее считать калории и BMR/TDEE.""");
        }
        if (!hasActivityInProfile(chatId)) {
            body.append("""

                    🚶 Сколько раз в неделю ты тренируешься?
                    Малоподвижный / лёгкий (1-3) / умеренный (3-5) / высокий (6-7) / очень высокий —
                    без этого BMR/TDEE считается «как для среднего», и норма калорий может врать.
                    """);
        }
        if (!hasGoalWeight(chatId)) {
            body.append("""

                    🎯 Если хочешь, можешь задать цель по весу в кг —
                    тогда я буду видеть динамику «до цели осталось N кг».
                    """);
        }
        body.append("""
    
                    🔗 Команды Google Health:
                    /google_connect — подключить Google-аккаунт
                    /google_disconnect — отвязать Google-аккаунт
                    /google_status — статус и время последней синхронизации
                    /google_sync — принудительная синхронизация (тянуть новые данные прямо сейчас)
                    """);
        send(chatId, body.toString());
    }

    private void handleConnectGoogle(long chatId) {
        String url;
        try {
            url = googleHealthOAuth.buildAuthorizeUrl(chatId);
        } catch (Exception ex) {
            log.warn("Cannot build authorize url", ex);
            send(chatId, "⚠️ Не удалось сформировать ссылку: " + ex.getMessage());
            return;
        }
        send(chatId, """
                🔗 Подключение Google Health.

                Открой эту ссылку в браузере и нажми «разрешить» в окне Google:
                👉 %s

                После подтверждения данные (вес, активность, сон) будут
                подтягиваться автоматически 2 раза в сутки: в 10:00 и 22:00.
                Проверить состояние: /google_status
                Запустить синхронизацию вручную: /google_sync
                """.formatted(url));
    }

    private void handleDisconnectGoogle(long chatId) {
        try {
            googleHealthOAuth.disconnect(chatId);
            send(chatId, "✅ Google-аккаунт отвязан. Синхронизация данных прекращена.");
        } catch (Exception ex) {
            log.warn("Disconnect google failed for {}", chatId, ex);
            send(chatId, "⚠️ Не получилось отвязать Google: " + ex.getMessage());
        }
    }

    private void handleGoogleStatus(long chatId) {
        googleHealthOAuth.statusFor(chatId).ifPresentOrElse(status -> {
            StringBuilder sb = new StringBuilder();
            sb.append(status.connected()
                    ? "✅ Google Health подключён.\n"
                    : "❌ Google Health не подключён. Используй /google_connect.\n");
            if (status.connectedAt() != null) {
                sb.append("Подключён: ").append(BotTime.format(status.connectedAt())).append("\n");
            }
            if (status.lastSyncAt() != null) {
                sb.append("Последняя синхронизация: ").append(BotTime.format(status.lastSyncAt())).append("\n");
            }
            if (status.lastSyncError() != null) {
                sb.append("Ошибка последней синхронизации: ")
                        .append(status.lastSyncError()).append("\n");
            }
            send(chatId, sb.toString());
        }, () -> send(chatId, "Профиль пользователя не найден."));
    }

    /** Принудительная синхронизация с Google Health — дёргает {@link GoogleHealthSyncService}. */
    private void handleGoogleSync(long chatId) {
        userProfileRepository.findByTelegramUserIdAndGoogleHealthConnected(chatId).ifPresentOrElse(
                profile -> {
                    try {
                        SyncResult result = googleHealthSyncService.sync(profile);
                        String body = result.isEmpty()
                                ? "✅ Синхронизация выполнена. Новых записей нет."
                                : "✅ Синхронизация выполнена. Импортировано записей: " + result.size() + ".";
                        log.info("Manual Google sync for {}: {}", chatId, result);
                        send(chatId, body);
                    } catch (Exception ex) {
                        log.warn("Manual Google sync failed for {}", chatId, ex);
                        send(chatId, "⚠️ Ошибка синхронизации");
                    }
                },
                () -> send(chatId, "❌ Google Health не подключён. Используй /google_connect."));
    }
}
