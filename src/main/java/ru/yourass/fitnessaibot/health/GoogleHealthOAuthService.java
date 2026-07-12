package ru.yourass.fitnessaibot.health;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.health.v4.GoogleHealthAPIScopes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import ru.yourass.fitnessaibot.entity.UserProfileEntity;
import ru.yourass.fitnessaibot.repository.UserProfileRepository;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * OAuth2-flow для Google Health API на базе стандартного
 * {@link GoogleAuthorizationCodeFlow}. Credential'ы лежат в
 * {@link GoogleHealthCredentialStore} (т.е. в БД).
 *
 * <p>Ключевая особенность — метод {@link #loadCredential(long)} возвращает
 * {@link Credential} со встроенным refresh-токеном: при истечении access_token
 * библиотека Google сама обменяет refresh_token на новый access_token и
 * запишет его обратно в {@link GoogleHealthCredentialStore} через
 * {@code DataStoreCredentialRefreshListener}.</p>
 */
@Service
public class GoogleHealthOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleHealthOAuthService.class);

    /** OAuth-скоупы, под которыми бот запрашивает доступ к Google Health. */
    private static final List<String> SCOPES = List.of(
            GoogleHealthAPIScopes.GOOGLEHEALTH_ACTIVITY_AND_FITNESS_READONLY,
            GoogleHealthAPIScopes.GOOGLEHEALTH_HEALTH_METRICS_AND_MEASUREMENTS_READONLY,
            GoogleHealthAPIScopes.GOOGLEHEALTH_SLEEP_READONLY
    );

    private final GoogleHealthProperties props;
    private final UserProfileRepository userProfileRepository;
    private final GoogleHealthSyncService syncService;
    private final GoogleAuthorizationCodeFlow flow;

    public GoogleHealthOAuthService(GoogleHealthProperties props,
                                    GoogleHealthCredentialStore credentialStore,
                                    UserProfileRepository userProfileRepository,
                                    @Lazy GoogleHealthSyncService syncService)
            throws IOException, GeneralSecurityException {
        this.props = props;
        this.userProfileRepository = userProfileRepository;
        this.syncService = syncService;

        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        this.flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport,
                jsonFactory,
                props.clientId(),
                props.clientSecret(),
                SCOPES)
                .setCredentialDataStore(credentialStore)
                .setAccessType("offline")
                .setApprovalPrompt("force")
                .build();
    }

    /**
     * @param telegramUserId используется как state — он же приходит обратно
     *                        в callback, чтобы связать OAuth-сессию
     *                        с пользователем Telegram.
     */
    public String buildAuthorizeUrl(long telegramUserId) {
        return flow.newAuthorizationUrl()
                .setRedirectUri(props.redirectUri())
                .setState(GoogleHealthCredentialStore.key(telegramUserId))
                .build();
    }

    /**
     * Обменивает authorization code на пару access+refresh token и сохраняет
     * credential в БД. Стандартный flow сам вызывает {@code DataStore.set()},
     * что в нашем случае пишет в {@code user_profiles.google_health_credential}.
     *
     * <p>Сразу после подключения запускает первичную синхронизацию, чтобы
     * пользователь увидел данные без ожидания ближайшего 10:00/22:00 по cron.
     * Ошибка первичной синхронизации не ломает OAuth-flow — она фиксируется
     * в {@code google_health_last_sync_error} и подхватится следующим
     * запуском по расписанию.</p>
     */
    public void handleCallback(long telegramUserId, String code) throws IOException {
        flow.createAndStoreCredential(
                flow.newTokenRequest(code).setRedirectUri(props.redirectUri()).execute(),
                GoogleHealthCredentialStore.key(telegramUserId));
        UserProfileEntity profile = markConnected(telegramUserId);
        log.info("Google Health: connected telegramUserId={}", telegramUserId);
        triggerInitialSync(profile);
    }

    /**
     * Принудительная синхронизация сразу после OAuth. Падение проглатывается
     * и сохраняется в профиль как {@code googleHealthLastSyncError}, чтобы
     * пользователь увидел диагностику через {@code /google_status}.
     */
    private void triggerInitialSync(UserProfileEntity profile) {
        long userId = profile.getTelegramUserId();
        try {
            syncService.sync(profile);
            log.info("Google Health: initial sync ok for user={}", userId);
        } catch (Exception ex) {
            log.warn("Google Health: initial sync failed for user={}: {}", userId, ex.getMessage());
            String msg = ex.getMessage();
            profile.setGoogleHealthLastSyncError(
                    msg != null ? msg.substring(0, Math.min(512, msg.length())) : "Unknown error");
            userProfileRepository.save(profile);
        }
    }

    /**
     * Загружает {@link Credential} для пользователя. Credential уже настроен
     * на автоматический refresh: при истечении access_token библиотека
     * Google сама выполнит refresh-endpoint и запишет обновлённый
     * access_token обратно в БД.
     *
     * @return {@code null}, если у пользователя нет сохранённого credential'а
     * @throws IOException при ошибке чтения из {@link GoogleHealthCredentialStore}
     */
    public Credential loadCredential(long telegramUserId) throws IOException {
        return flow.loadCredential(GoogleHealthCredentialStore.key(telegramUserId));
    }

    public void disconnect(long telegramUserId) {
        try {
            flow.getCredentialDataStore().delete(GoogleHealthCredentialStore.key(telegramUserId));
        } catch (IOException ex) {
            log.warn("Google Health: failed to delete credential: {}", ex.getMessage());
        }
        userProfileRepository.findByTelegramUserId(telegramUserId).ifPresent(p -> {
            p.setGoogleHealthConnectedAt(null);
            p.setGoogleHealthLastSyncAt(null);
            p.setGoogleHealthLastSyncError(null);
            userProfileRepository.save(p);
        });
        log.info("Google Health: disconnected telegramUserId={}", telegramUserId);
    }

    public Optional<ConnectionStatus> statusFor(long telegramUserId) {
        return userProfileRepository.findByTelegramUserIdAndGoogleHealthConnected(telegramUserId)
                .map(p -> new ConnectionStatus(
                        true,
                        p.getGoogleHealthConnectedAt(),
                        p.getGoogleHealthLastSyncAt(),
                        p.getGoogleHealthLastSyncError()))
                .or(() -> userProfileRepository.findByTelegramUserId(telegramUserId)
                        .map(p -> new ConnectionStatus(false, null, null, null)));
    }

    private UserProfileEntity markConnected(long telegramUserId) {
        UserProfileEntity profile = userProfileRepository.findByTelegramUserId(telegramUserId)
                .orElseGet(() -> new UserProfileEntity(telegramUserId, null, null));
        profile.setGoogleHealthConnectedAt(OffsetDateTime.now());
        profile.setGoogleHealthLastSyncError(null);
        return userProfileRepository.save(profile);
    }

    public record ConnectionStatus(
            boolean connected,
            OffsetDateTime connectedAt,
            OffsetDateTime lastSyncAt,
            String lastSyncError
    ) {}
}
