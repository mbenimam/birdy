package me.birdy.chat;

import java.util.regex.Pattern;

/** Chat rooms are created on demand; any name of 1 to 32 lower-case letters, digits or hyphens designates one. */
final class RoomNames {

    private static final Pattern VALID = Pattern.compile("[a-z0-9-]{1,32}");

    private RoomNames() {
    }

    static boolean isValid(String room) {
        return room != null && VALID.matcher(room).matches();
    }
}
