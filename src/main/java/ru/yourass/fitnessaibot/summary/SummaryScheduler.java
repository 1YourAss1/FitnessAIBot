package ru.yourass.fitnessaibot.summary;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.yourass.fitnessaibot.entity.UserProfileEntity;
import ru.yourass.fitnessaibot.repository.UserProfileRepository;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Рассылает пользователям автоматические сводки:
 * <ul>
 *   <li>каждый день в 23:30 — краткий итог за день;</li>
 *   <li>каждое воскресенье в 20:00 — краткий итог за неделю.</li>
 * </ul>
 *
 * <p>Сводки формирует {@link SummaryService} через {@link ru.yourass.fitnessaibot.ai.FitnessAgent}.
 * Рассылка идёт параллельно с ограниченным пулом потоков, чтобы не блокировать
 * ни long-poll Telegram-бота, ни другие cron-задачи.</p>
 */
@Component
@ConditionalOnProperty(name = "fitness.bot.enabled", havingValue = "true", matchIfMissing = true)
public class SummaryScheduler {

    private static final Logger log = LoggerFactory.getLogger(SummaryScheduler.class);

    /** Число параллельных запросов к LLM. Достаточно для десятков пользователей. */
    private static final int PARALLELISM = 4;

    private final UserProfileRepository userProfileRepository;
    private final SummaryService summaryService;
    private final ExecutorService executor;

    public SummaryScheduler(UserProfileRepository userProfileRepository,
                            SummaryService summaryService) {
        this.userProfileRepository = userProfileRepository;
        this.summaryService = summaryService;
        this.executor = Executors.newFixedThreadPool(PARALLELISM, r -> {
            Thread t = new Thread(r, "summary-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException _) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /** Краткий итог за день — 23:30. */
    @Scheduled(cron = "0 30 23 * * *")
    public void endOfDay() {
        broadcast(SummaryKind.DAILY);
    }

    /** Краткий итог за неделю — воскресенье 20:00. */
    @Scheduled(cron = "0 0 20 * * SUN")
    public void endOfWeek() {
        broadcast(SummaryKind.WEEKLY);
    }

    private void broadcast(SummaryKind kind) {
        List<UserProfileEntity> profiles = userProfileRepository.findAll();
        if (profiles.isEmpty()) {
            log.debug("Summary scheduler ({}): no users", kind);
            return;
        }
        log.info("Summary scheduler ({}): broadcasting to {} user(s)", kind, profiles.size());
        for (UserProfileEntity profile : profiles) {
            long userId = profile.getTelegramUserId();
            executor.submit(() -> {
                try {
                    switch (kind) {
                        case DAILY -> summaryService.sendDailySummary(userId);
                        case WEEKLY -> summaryService.sendWeeklySummary(userId);
                    }
                } catch (Exception ex) {
                    log.warn("Summary ({}): failed for user={}: {}",
                            kind, userId, ex.getMessage());
                }
            });
        }
    }

    private enum SummaryKind {
        DAILY, WEEKLY
    }
}