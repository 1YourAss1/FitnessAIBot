package ru.yourass.fitnessaibot.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.BotCommand;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ChatAction;
import com.pengrad.telegrambot.model.request.richmessages.InputRichMessage;
import com.pengrad.telegrambot.request.SendChatAction;
import com.pengrad.telegrambot.request.SetMyCommands;
import com.pengrad.telegrambot.request.richmessages.SendRichMessage;
import com.pengrad.telegrambot.request.richmessages.SendRichMessageDraft;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.yourass.fitnessaibot.ai.FitnessAgent;
import ru.yourass.fitnessaibot.bot.command.Command;
import ru.yourass.fitnessaibot.config.BotProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(name = "fitness.bot.enabled", havingValue = "true", matchIfMissing = true)
public class FitnessBot {

    private static final Logger log = LoggerFactory.getLogger(FitnessBot.class);

    /** Минимальный интервал между отправками драфта, чтобы не упереться в rate-limit Telegram. */
    private static final long DRAFT_MIN_INTERVAL_MS = 700L;
    /** Счётчик для генерации уникальных draft_id в пределах чата. */
    private static final AtomicLong DRAFT_SEQ = new AtomicLong();

    private final BotProperties botProperties;
    private final FitnessAgent agent;
    private final List<Command> commands;
    private final Map<String, Command> commandsByName;
    private final TelegramBot telegramBot;
    private final ExecutorService handlers = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "fitness-bot-handler");
        t.setDaemon(true);
        return t;
    });

    public FitnessBot(BotProperties botProperties,
                      FitnessAgent agent,
                      List<Command> commands) {
        this.botProperties = botProperties;
        this.agent = agent;
        this.commands = commands;
        this.telegramBot = new TelegramBot(botProperties.token());

        Map<String, Command> byName = new HashMap<>();
        for (Command c : commands) {
            register(byName, c.name(), c);
            for (String alias : c.aliases()) {
                register(byName, alias, c);
            }
        }
        this.commandsByName = byName;
    }

    private static void register(Map<String, Command> map, String name, Command candidate) {
        Command previous = map.putIfAbsent(name.toLowerCase(), candidate);
        if (previous != null && previous != candidate) {
            log.warn("Bot command name collision: '{}' already bound to {}, ignoring {}",
                    name, previous.getClass().getSimpleName(), candidate.getClass().getSimpleName());
        }
    }

    @PostConstruct
    public void start() {
        log.info("Registering Telegram bot @{} (enabled=true)", botProperties.username());

        BotCommand[] menu = commands.stream()
                .map(c -> new BotCommand(c.name(), c.description()))
                .toArray(BotCommand[]::new);
        try {
            telegramBot.execute(new SetMyCommands(menu));
        } catch (Exception e) {
            log.warn("Failed to register bot commands: {}", e.getMessage());
        }

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
        if (message.chat().type() != Chat.Type.Private) {
            return; // бот работает только в личных сообщениях
        }

        long chatId = message.chat().id();
        String text = message.text();
        if (text == null || text.isBlank()) {
            send(chatId, """
                    ## ℹ️ Пустое сообщение

                    Пришли, пожалуйста, текстовое сообщение.
                    Например: *«в обед съел котлетки с пюрешкой»*.
                    """);
            return;
        }

        Command command = commandsByName.get(text.substring(1).toLowerCase());
        if (command != null) {
            sendTyping(chatId);
            send(chatId, command.handle(chatId));
            return;
        }

        log.info("Processing message from {}: {}", chatId, text);
        if (botProperties.streamReplyEnabled()) {
            streamAgentReply(chatId, message.messageId(), text);
        } else {
            sendDraft(chatId, message.messageId(), "⏳ Думаю…");
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
    }

    /**
     * Стримит ответ агента в чат через {@code sendRichMessageDraft}.
     *
     * <p>Пользователь видит:</p>
     * <ol>
     *   <li>сразу — драфт «⏳ Думаю…»</li>
     *   <li>когда пошёл первый токен — placeholder стирается,
     *       начинается «растущий» markdown-драфт;</li>
     *   <li>по завершении — Telegram фиксирует последний драфт
     *       как обычное сообщение.</li>
     * </ol>

     */
    private void streamAgentReply(long chatId, int sourceMessageId, String userText) {
        StringBuffer buf = new StringBuffer();
        AtomicLong lastSentAt = new AtomicLong(0L);

        int draftId = sendDraft(chatId, sourceMessageId, "⏳ Думаю…");
        sendTyping(chatId);
        lastSentAt.set(System.currentTimeMillis());

        agent.stream(chatId, userText)
                .doOnError(ex -> {
                    log.error("Agent stream failed for user {}", chatId, ex);
                    buf.setLength(0);
                    buf.append("## ⚠️ Ошибка обработки\n\nНе получилось обработать сообщение: `")
                            .append(ex.getMessage() == null ? "unknown" : ex.getMessage())
                            .append('`');
                    send(chatId, buf.toString());
                })
                .doFinally(sig -> {
                    if (!buf.isEmpty()) {
                        send(chatId, buf.toString());
                    }
                    log.info("Stream finished for {} ({}): len={} signal={}",
                            chatId, draftId, buf.length(), sig);
                })
                .subscribe(chunk -> {
                    if (chunk.isEmpty()) {
                        return;
                    }
                    if (buf.indexOf("⏳") == 0) {
                        buf.setLength(0);
                    }
                    buf.append(chunk);
                    long now = System.currentTimeMillis();
                    long prev = lastSentAt.get();
                    if (now - prev < DRAFT_MIN_INTERVAL_MS) {
                        return;
                    }
                    if (lastSentAt.compareAndSet(prev, now)) {
                        sendDraft(chatId, draftId, buf);
                    }
                });
    }

    private void sendDraft(long chatId, int draftId, CharSequence text) {
        try {
            telegramBot.execute(new SendRichMessageDraft(chatId, draftId,
                    new InputRichMessage().markdown(text.toString())));
        } catch (Exception e) {
            log.warn("sendRichMessageDraft failed for {}: {}", chatId, e.getMessage());
        }
    }

    private int sendDraft(long chatId, int messageId, String text) {
        int draftId = Math.abs(Objects.hash(chatId, messageId, DRAFT_SEQ.incrementAndGet()));
        try {
            telegramBot.execute(new SendRichMessageDraft(chatId, draftId,
                    new InputRichMessage().markdown(text)));
            return draftId;
        } catch (Exception e) {
            log.warn("sendRichMessageDraft failed for {}: {}", chatId, e.getMessage());
            return -1;
        }
    }

    public void send(long chatId, String markdown) {
        try {
            InputRichMessage rich = new InputRichMessage().markdown(markdown);
            telegramBot.execute(new SendRichMessage(chatId, rich));
        } catch (Exception e) {
            log.error("Failed to send rich Telegram message to {}", chatId, e);
        }
    }

    public void sendTyping(long chatId) {
        try {
            telegramBot.execute(new SendChatAction(chatId, ChatAction.typing));
        } catch (Exception e) {
            log.warn("Failed to send typing action to {}: {}", chatId, e.getMessage());
        }
    }
}
