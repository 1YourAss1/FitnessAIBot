package ru.yourass.fitnessaibot.bot.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.yourass.fitnessaibot.health.GoogleHealthOAuthService;

@Component
@ConditionalOnProperty(name = "fitness.bot.enabled", havingValue = "true", matchIfMissing = true)
public class GoogleConnectCommand implements Command {

    private static final Logger log = LoggerFactory.getLogger(GoogleConnectCommand.class);

    private final GoogleHealthOAuthService googleHealthOAuth;

    public GoogleConnectCommand(GoogleHealthOAuthService googleHealthOAuth) {
        this.googleHealthOAuth = googleHealthOAuth;
    }

    @Override
    public String name() {
        return "google_connect";
    }

    @Override
    public String description() {
        return "Подключить Google Health";
    }

    @Override
    public String handle(long chatId) {
        String url;
        try {
            url = googleHealthOAuth.buildAuthorizeUrl(chatId);
        } catch (Exception ex) {
            log.warn("Cannot build authorize url", ex);
            return """
                    ## ⚠️ Ошибка

                    Не удалось сформировать ссылку для подключения Google Health.
                    """;
        }
        return """
                ## 🔗 Подключение Google Health

                Открой эту ссылку в браузере и нажми **«разрешить»** в окне Google:

                👉 [**%s**](%s)

                После подтверждения данные *(вес, активность, сон)* будут подтягиваться автоматически **2 раза в сутки: в 10:00 и 22:00**.

                - **Проверить состояние:** /google_status
                - **Запустить синхронизацию вручную:** /google_sync
                """.formatted(url, url);
    }
}
