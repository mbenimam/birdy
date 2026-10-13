package me.birdy.posts;

import java.util.Optional;
import org.bson.types.ObjectId;

/** Lenient parsing of MongoDB identifiers received from clients. */
public final class ObjectIds {

    private ObjectIds() {
    }

    /** Returns the identifier, or empty when the text is not a well-formed ObjectId (24 hexadecimal digits). */
    public static Optional<ObjectId> parse(String text) {
        return text != null && ObjectId.isValid(text) ? Optional.of(new ObjectId(text)) : Optional.empty();
    }
}
