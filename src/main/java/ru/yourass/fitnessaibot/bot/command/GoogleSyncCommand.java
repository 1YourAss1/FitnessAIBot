package ru.yourass.fitnessaibot.bot.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.yourass.fitnessaibot.health.GoogleHealthSyncService;
import ru.yourass.fitnessaibot.health.GoogleHealthSyncService.SyncResult;
import ru.yourass.fitnessaibot.repository.UserProfileRepository;

@Component
@ConditionalOnProperty(name = "fitness.bot.enabled", havingValue = "true", matchIfMissing = true)
public class GoogleSyncCommand implements Command {

    private static final Logger log = LoggerFactory.getLogger(GoogleSyncCommand.class);

    private final UserProfileRepository userProfileRepository;
    private final GoogleHealthSyncService googleHealthSyncService;

    public GoogleSyncCommand(UserProfileRepository userProfileRepository,
                            GoogleHealthSyncService googleHealthSyncService) {
        this.userProfileRepository = userProfileRepository;
        this.googleHealthSyncService = googleHealthSyncService;
    }

    @Override
    public String name() {
        return "google_sync";
    }

    @Override
    public String description() {
        return "Принудительная синхронизация";
    }

    @Override
    public String handle(long chatId) {
        return userProfileRepository.findByTelegramUserIdAndGoogleHealthConnected(chatId)
                .map(profile -> {
                    try {
                        SyncResult result = googleHealthSyncService.sync(profile);
                        log.info("Manual Google sync for {}: {}", chatId, result);
                        return result.isEmpty()
                                ? "## ✅ Синхронизация выполнена\n\nНовых записей нет."
                                : "## ✅ Синхронизация выполнена\n\nИмпортировано записей: **" + result.size() + "**.";
                    } catch (Exception ex) {
                        log.warn("Manual Google sync failed for {}", chatId, ex);
                        return "## ⚠️ Ошибка синхронизации";
                    }
                })
                .orElse("""
                        ## ❌ Google Health не подключён

                        Используй /google_connect.
                        """);
    }
}
