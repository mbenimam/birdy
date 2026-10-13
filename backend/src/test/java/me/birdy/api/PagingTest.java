package me.birdy.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PagingTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void anAbsentLimitFallsBackToTheDefault(String raw) {
        assertEquals(20, Paging.limit(raw, 20, 50));
    }

    @Test
    void aLimitWithinBoundsIsUsedAsIs() {
        assertEquals(1, Paging.limit("1", 20, 50));
        assertEquals(35, Paging.limit(" 35 ", 20, 50));
        assertEquals(50, Paging.limit("50", 20, 50));
    }

    @Test
    void aLimitAboveTheMaximumIsCapped() {
        assertEquals(50, Paging.limit("51", 20, 50));
        assertEquals(50, Paging.limit("2147483647", 20, 50));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5", "99999999999"})
    void aLimitThatIsNotAPositiveIntegerIsAValidationFailure(String raw) {
        ApiException failure = assertThrows(ApiException.class, () -> Paging.limit(raw, 20, 50));

        assertEquals(ErrorCode.VALIDATION_FAILED, failure.code());
        assertEquals(Map.of("limit", "must be a positive integer"), failure.fields());
    }
}
