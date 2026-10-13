package me.birdy.posts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectIdsTest {

    @Test
    void aWellFormedIdIsParsed() {
        ObjectId id = new ObjectId();

        assertEquals(Optional.of(id), ObjectIds.parse(id.toHexString()));
        assertEquals(Optional.of(id), ObjectIds.parse(id.toHexString().toUpperCase()));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "123", "not-an-object-id", "0123456789abcdef0123456", "0123456789abcdef012345678",
            "0123456789abcdef0123456g", "../../etc/passwd", "{\"$ne\":null}"})
    void anythingElseIsEmptyInsteadOfAnException(String text) {
        assertEquals(Optional.empty(), ObjectIds.parse(text));
    }
}
