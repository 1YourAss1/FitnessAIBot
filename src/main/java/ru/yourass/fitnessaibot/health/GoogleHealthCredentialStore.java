package ru.yourass.fitnessaibot.health;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.util.store.AbstractDataStore;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.DataStoreFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.yourass.fitnessaibot.entity.UserProfileEntity;
import ru.yourass.fitnessaibot.repository.UserProfileRepository;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Хранилище OAuth-credential'ов Google Health в таблице {@code user_profiles}
 * (TEXT-колонка {@code google_health_credential}).
 *
 * <p>Реализует {@link DataStore}<{@link StoredCredential}> напрямую, чтобы
 * стандартный {@code GoogleAuthorizationCodeFlow} мог сохранять и читать
 * credential'ы через наш JPA-слой. При {@link DataStore#set set} /
 * {@link DataStore#delete delete} / {@link DataStore#get get} библиотека
 * Google сама навешивает {@code DataStoreCredentialRefreshListener},
 * поэтому обновление access_token через refresh_token и сохранение
 * нового access_token обратно в БД работает без ручной логики.</p>
 *
 * <p>Ключ credential'а — строка вида {@code "user:<telegramUserId>"}.</p>
 */
@Component
public class GoogleHealthCredentialStore extends AbstractDataStore<StoredCredential> {

    static final String KEY_PREFIX = "user:";
    private static final ObjectMapper MAPPER = new ObjectMapper().setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

    /**
     * Заглушка для {@link #getDataStoreFactory()}: фабрика не нужна,
     * потому что {@code GoogleAuthorizationCodeFlow} получает этот
     * {@link DataStore} напрямую через {@code setCredentialDataStore}.
     */
    private static final DataStoreFactory NOOP_FACTORY = new DataStoreFactory() {
        @Override
        public <V extends Serializable> DataStore<V> getDataStore(String id) {
            throw new UnsupportedOperationException(
                    "GoogleHealthCredentialStore is a single DataStore, factory is not used");
        }
    };

    private final UserProfileRepository userProfileRepository;

    public GoogleHealthCredentialStore(UserProfileRepository userProfileRepository) {
        super(NOOP_FACTORY, StoredCredential.DEFAULT_DATA_STORE_ID);
        this.userProfileRepository = userProfileRepository;
    }

    /** Ключ credential'а в DataStore для конкретного telegramUserId. */
    static String key(long telegramUserId) {
        return KEY_PREFIX + telegramUserId;
    }

    private static long parseUserId(String key) {
        if (key == null || !key.startsWith(KEY_PREFIX)) {
            throw new IllegalArgumentException("Bad credential key: " + key);
        }
        return Long.parseLong(key.substring(KEY_PREFIX.length()));
    }

    @Override
    @Transactional(readOnly = true)
    public StoredCredential get(String key) {
        return userProfileRepository.findByTelegramUserId(parseUserId(key))
                .map(UserProfileEntity::getGoogleHealthCredential)
                .filter(s -> !s.isBlank())
                .map(GoogleHealthCredentialStore::deserialize)
                .orElse(null);
    }

    @Override
    @Transactional
    public DataStore<StoredCredential> set(String key, StoredCredential value) {
        long telegramUserId = parseUserId(key);
        UserProfileEntity profile = userProfileRepository.findByTelegramUserId(telegramUserId)
                .orElseGet(() -> new UserProfileEntity(telegramUserId, null, null));
        profile.setGoogleHealthCredential(serialize(value));
        userProfileRepository.save(profile);
        return this;
    }

    @Override
    @Transactional
    public DataStore<StoredCredential> delete(String key) {
        userProfileRepository.findByTelegramUserId(parseUserId(key))
                .ifPresent(p -> {
                    p.setGoogleHealthCredential(null);
                    userProfileRepository.save(p);
                });
        return this;
    }

    @Override
    public Collection<StoredCredential> values() {
        return List.of();
    }

    @Override
    public DataStore<StoredCredential> clear() {
        return this;
    }

    @Override
    public Set<String> keySet() {
        return Set.of();
    }

    private static String serialize(StoredCredential c) {
        try {
            return MAPPER.writeValueAsString(c);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize StoredCredential", ex);
        }
    }

    private static StoredCredential deserialize(String json) {
        try {
            return MAPPER.readValue(json, StoredCredential.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Corrupted StoredCredential JSON", ex);
        }
    }
}
