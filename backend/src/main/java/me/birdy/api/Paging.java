package me.birdy.api;

/** Parsing of the {@code limit} query parameter shared by the list endpoints. */
public final class Paging {

    private Paging() {
    }

    /**
     * Returns the page size to use: {@code defaultLimit} when the parameter is absent, the given value capped at
     * {@code maxLimit} otherwise.
     *
     * @throws ApiException {@code 400} when the value is not a positive integer
     */
    public static int limit(String raw, int defaultLimit, int maxLimit) {
        if (raw == null || raw.isBlank()) {
            return defaultLimit;
        }
        int requested;
        try {
            requested = Integer.parseInt(raw.strip());
        } catch (NumberFormatException e) {
            throw ApiException.invalid("limit", "must be a positive integer");
        }
        if (requested < 1) {
            throw ApiException.invalid("limit", "must be a positive integer");
        }
        return Math.min(requested, maxLimit);
    }
}
