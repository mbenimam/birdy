package me.birdy.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConstraintViolationMapperTest {

    record Inner(@NotBlank String name) {
    }

    record Outer(@NotNull @Size(min = 3) String title, @Valid Inner inner) {
    }

    @Test
    void aValidObjectHasNoFields() {
        assertEquals(Map.of(), Validation.problems(new Outer("title", new Inner("name"))));
    }

    @Test
    void eachInvalidPropertyIsReportedUnderItsOwnName() {
        assertEquals(Map.of("title", "must not be null"), Validation.problems(new Outer(null, new Inner("name"))));
    }

    @Test
    void nestedPropertiesAreReportedUnderTheirLastPathSegment() {
        assertEquals(
                Map.of("title", "size must be between 3 and 2147483647", "name", "must not be blank"),
                Validation.problems(new Outer("ab", new Inner(" "))));
    }

    record Twice(@NotBlank @Size(min = 3) String value) {
    }

    @Test
    void aPropertyBreakingSeveralRulesIsReportedOnceAndDeterministically() {
        assertEquals(Map.of("value", "must not be blank"), Validation.problems(new Twice(" ")));
    }
}
