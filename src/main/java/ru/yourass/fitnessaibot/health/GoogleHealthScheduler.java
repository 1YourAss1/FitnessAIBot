package ru.yourass.fitnessaibot.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.yourass.fitnessaibot.entity.UserProfileEntity;
import ru.yourass.fitnessaibot.repository.UserProfileRepository;

import java.util.List;

/**
 * Раз в сутки в 10:00 и 22:00 (часовой пояс JVM) запускает
 * синхронизацию Google Health → локальный журнал для всех пользователей,
 * которые ранее подключили аккаунт.
 */
@Component
public class GoogleHealthScheduler {

    private static final Logger log = LoggerFactory.getLogger(GoogleHealthScheduler.class);

    private final UserProfileRepository userProfileRepository;
    private final GoogleHealthSyncService syncService;

    public GoogleHealthScheduler(UserProfileRepository userProfileRepository,
                                 GoogleHealthSyncService syncService) {
        this.userProfileRepository = userProfileRepository;
        this.syncService = syncService;
    }

    @Scheduled(cron = "0 0 10,22 * * *")
    public void syncAllUsers() {
        List<UserProfileEntity> profiles = userProfileRepository.findByGoogleHealthCredentialNotNull();
        if (profiles.isEmpty()) {
            log.debug("Google Health scheduler: no connected users");
            return;
        }
        log.info("Google Health scheduler: syncing {} user(s)", profiles.size());
        for (UserProfileEntity profile : profiles) {
            syncUser(profile);
        }
    }

    private void syncUser(UserProfileEntity profile) {
        long userId = profile.getTelegramUserId();
        try {
            syncService.sync(profile);
        } catch (Exception ex) {
            log.warn("Google Health sync failed for user={}: {}", userId, ex.getMessage());
            String msg = ex.getMessage();
            profile.setGoogleHealthLastSyncError(
                    msg != null ? msg.substring(0, Math.min(512, msg.length())) : "Unknown error");
            userProfileRepository.save(profile);
        }
    }
}
