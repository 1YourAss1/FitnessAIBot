package ru.yourass.fitnessaibot.health;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.Strings;
import com.google.api.services.health.v4.GoogleHealthAPI;
import com.google.api.services.health.v4.model.DataPoint;
import com.google.api.services.health.v4.model.ListDataPointsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yourass.fitnessaibot.entity.UserProfileEntity;
import ru.yourass.fitnessaibot.health.sync.DataTypeSync;
import ru.yourass.fitnessaibot.repository.UserProfileRepository;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Фоновая синхронизация Google Health → БД.
 *
 * <p>Итерирует зарегистрированные {@link DataTypeSync}-стратегии
 * (Spring собирает все бины в список). Для каждой стратегии:
 * <ol>
 *   <li>Строит filter по времени (incremental или lookbackDays);</li>
 *   <li>Загружает все DataPoint'ы с пагинацией через {@code nextPageToken};</li>
 *   <li>Сохраняет каждый датапоинт.</li>
 * </ol>
 */
@Service
public class GoogleHealthSyncService {

    private static final Logger log = LoggerFactory.getLogger(GoogleHealthSyncService.class);

    private static final String APPLICATION_NAME = "FitnessAI";

    private final GoogleHealthOAuthService oauth;
    private final UserProfileRepository userProfileRepository;
    private final GoogleHealthProperties props;
    private final HttpTransport httpTransport;
    private final JsonFactory jsonFactory;
    private final List<DataTypeSync> syncs;

    public GoogleHealthSyncService(GoogleHealthOAuthService oauth,
                                   UserProfileRepository userProfileRepository,
                                   GoogleHealthProperties props,
                                   List<DataTypeSync> syncs) throws GeneralSecurityException, IOException {
        this.oauth = oauth;
        this.userProfileRepository = userProfileRepository;
        this.props = props;
        this.syncs = syncs;
        this.httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        this.jsonFactory = GsonFactory.getDefaultInstance();
    }

    @Transactional
    public SyncResult sync(UserProfileEntity profile) throws IOException {
        long userId = profile.getTelegramUserId();
        var credential = oauth.loadCredential(userId);
        if (credential == null) {
            throw new IllegalStateException("Google Health не подключён для пользователя " + userId);
        }
        GoogleHealthAPI api = new GoogleHealthAPI.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();

        Instant lastSync = profile.getGoogleHealthLastSyncAt() != null
                ? profile.getGoogleHealthLastSyncAt().toInstant()
                : Instant.now().minus(Duration.ofDays(props.lookbackDays()));

        SyncResult syncResult = new SyncResult();
        for (DataTypeSync dataTypeSync : syncs) {
            String value = dataTypeSync.formatFilterTimestamp(lastSync);
            String filter = dataTypeSync.dataTypeForFilter() + "." + dataTypeSync.filterTimeField()
                    + " >= \"" + value + "\"";
            int imported = syncDataType(userId, api, dataTypeSync, filter, profile);
            syncResult.add(dataTypeSync.dataTypePath(), imported);
        }

        profile.setGoogleHealthLastSyncAt(OffsetDateTime.now(ZoneId.systemDefault()));
        profile.setGoogleHealthLastSyncError(null);
        userProfileRepository.save(profile);
        log.info("Google Health sync: user={} {}", userId, syncResult);
        return syncResult;
    }

    private int syncDataType(long userId, GoogleHealthAPI api, DataTypeSync dataTypeSync,
                             String filter, UserProfileEntity profile) throws IOException {
        List<DataPoint> points = getDataPointList(api, dataTypeSync.dataTypePath(), filter, dataTypeSync.pageSize());
        int imported = 0;
        for (DataPoint dp : points) {
            if (dataTypeSync.saveDataPoint(userId, dp, profile)) imported++;
        }
        return imported;
    }

    /** Собирает все DataPoint'ы по данному типу, проходя пагинацию через {@code nextPageToken}. */
    private List<DataPoint> getDataPointList(GoogleHealthAPI api, String parent,
                                             String filter, int pageSize) throws IOException {
        List<DataPoint> all = new ArrayList<>();
        String pageToken = null;
        do {
            var req = api.users().dataTypes().dataPoints().list(parent)
                    .setPageSize(pageSize);
            if (filter != null) {
                req.setFilter(filter);
            }
            if (!Strings.isNullOrEmpty(pageToken)) {
                req.setPageToken(pageToken);
            }
            ListDataPointsResponse resp = req.execute();
            if (resp.getDataPoints() != null) {
                all.addAll(resp.getDataPoints());
            }
            pageToken = resp.getNextPageToken();
        } while (!Strings.isNullOrEmpty(pageToken));
        return all;
    }

    /**
     * Сводка по импорту за один запуск {@link #sync(UserProfileEntity)}.
     * Счётчики индексируются по {@code dataTypePath}; в логе выводятся
     * все типы, у которых {@code imported > 0}.
     */
    public static final class SyncResult {
        private final Map<String, Integer> imported = new HashMap<>();

        public void add(String dataTypePath, int n) {
            imported.merge(dataTypePath, n, Integer::sum);
        }

        public boolean isEmpty() {
            return size() == 0;
        }

        public int size() {
            return imported.values().stream().mapToInt(Integer::intValue).sum();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            imported.forEach((path, count) -> {
                if (count > 0) {
                    if (!sb.isEmpty()) sb.append(' ');
                    sb.append(path).append('=').append(count);
                }
            });
            return sb.toString();
        }
    }
}