package me.birdy.users;

import java.util.Locale;

/** Builds SQL LIKE patterns from user input, with {@code !} as the escape character. */
final class LikePattern {

    private LikePattern() {
    }

    /** Returns a pattern matching every lower-cased value that contains {@code text}, taken literally. */
    static String containing(String text) {
        String literal = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
        String escaped = literal.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }
}
