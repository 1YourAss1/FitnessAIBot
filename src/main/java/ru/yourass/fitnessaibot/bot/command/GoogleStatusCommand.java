package ru.yourass.fitnessaibot.bot.command;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.yourass.fitnessaibot.bot.BotTime;
import ru.yourass.fitnessaibot.health.GoogleHealthOAuthService;

@Component
@ConditionalOnProperty(name = "fitness.bot.enabled", havingValue = "true", matchIfMissing = true)
public class GoogleStatusCommand implements Command {

    private final GoogleHealthOAuthService googleHealthOAuth;

    public GoogleStatusCommand(GoogleHealthOAuthService googleHealthOAuth) {
        this.googleHealthOAuth = googleHealthOAuth;
    }

    @Override
    public String name() {
        return "google_status";
    }

    @Override
    public String description() {
        return "Статус подключения";
    }

    @Override
    public String handle(long chatId) {
        return googleHealthOAuth.statusFor(chatId)
                .map(GoogleStatusCommand::render)
                .orElse("""
                        ## ℹ️ Статус

                        Профиль пользователя не найден.
                        """);
    }

    private static String render(GoogleHealthOAuthService.ConnectionStatus status) {
        StringBuilder sb = new StringBuilder();
        sb.append(status.connected()
                ? "## ✅ Google Health подключён\n\n"
                : "## ❌ Google Health не подключён\n\nИспользуй /google_connect.\n");
        if (status.connected() && (
                status.connectedAt() != null
                        || status.lastSyncAt() != null
                        || status.lastSyncError() != null)) {
            sb.append("\n| Поле | Значение |\n");
            sb.append("|:-----|:---------|\n");
            if (status.connectedAt() != null) {
                sb.append("| **Подключён** | ").append(BotTime.format(status.connectedAt())).append(" |\n");
            }
            if (status.lastSyncAt() != null) {
                sb.append("| **Последняя синхронизация** | ").append(BotTime.format(status.lastSyncAt())).append(" |\n");
            }
            if (status.lastSyncError() != null) {
                sb.append("| **Ошибка последней синхронизации** | `")
                        .append(status.lastSyncError()).append("` |\n");
            }
        }
        return sb.toString();
    }
}
