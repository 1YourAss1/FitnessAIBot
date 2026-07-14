package ru.yourass.fitnessaibot.bot.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.yourass.fitnessaibot.health.GoogleHealthOAuthService;

@Component
@ConditionalOnProperty(name = "fitness.bot.enabled", havingValue = "true", matchIfMissing = true)
public class GoogleDisconnectCommand implements Command {

    private static final Logger log = LoggerFactory.getLogger(GoogleDisconnectCommand.class);

    private final GoogleHealthOAuthService googleHealthOAuth;

    public GoogleDisconnectCommand(GoogleHealthOAuthService googleHealthOAuth) {
        this.googleHealthOAuth = googleHealthOAuth;
    }

    @Override
    public String name() {
        return "google_disconnect";
    }

    @Override
    public String description() {
        return "Отвязать Google Health";
    }

    @Override
    public String handle(long chatId) {
        try {
            googleHealthOAuth.disconnect(chatId);
            return """
                    ## ✅ Готово

                    Google-аккаунт отвязан. Синхронизация данных прекращена.
                    """;
        } catch (Exception ex) {
            log.warn("Disconnect google failed for {}", chatId, ex);
            return """
                    ## ⚠️ Ошибка

                    Не получилось отвязать Google.
                    """;
        }
    }
}
