package ru.yourass.fitnessaibot.health;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleHealthSyncServiceTest {

    private final GoogleHealthSyncService.SyncResult result = new GoogleHealthSyncService.SyncResult();

    @Test
    void freshResultIsEmpty() {
        assertThat(result.isEmpty()).isTrue();
        assertThat(result.size()).isZero();
        assertThat(result.toString()).isEmpty();
    }

    @Test
    void zeroImportsAcrossAllDataTypesIsEmpty() {
        result.add("users/me/dataTypes/weight", 0);
        result.add("users/me/dataTypes/sleep", 0);
        result.add("users/me/dataTypes/exercise", 0);

        assertThat(result.isEmpty()).isTrue();
        assertThat(result.size()).isZero();
        assertThat(result.toString()).isEmpty();
    }

    @Test
    void sizeSumsImportsAcrossDataTypes() {
        result.add("users/me/dataTypes/weight", 3);
        result.add("users/me/dataTypes/sleep", 2);
        result.add("users/me/dataTypes/exercise", 5);

        assertThat(result.isEmpty()).isFalse();
        assertThat(result.size()).isEqualTo(10);
    }

    @Test
    void sameDataTypeMergesViaSum() {
        result.add("users/me/dataTypes/weight", 1);
        result.add("users/me/dataTypes/weight", 2);
        result.add("users/me/dataTypes/weight", 4);

        assertThat(result.size()).isEqualTo(7);
        assertThat(result).hasToString("users/me/dataTypes/weight=7");
    }

    @Test
    void toStringSkipsZeroEntriesAndIsSpaceSeparated() {
        result.add("users/me/dataTypes/weight", 0);
        result.add("users/me/dataTypes/sleep", 4);
        result.add("users/me/dataTypes/exercise", 0);
        result.add("users/me/dataTypes/weight", 2);

        assertThat(result.size()).isEqualTo(6);
        assertThat(result)
                .hasToString("users/me/dataTypes/sleep=4 users/me/dataTypes/weight=2");
    }

    @Test
    void singleImportMakesResultNonEmpty() {
        result.add("users/me/dataTypes/weight", 1);

        assertThat(result.isEmpty()).isFalse();
        assertThat(result.size()).isEqualTo(1);
        assertThat(result).hasToString("users/me/dataTypes/weight=1");
    }
}