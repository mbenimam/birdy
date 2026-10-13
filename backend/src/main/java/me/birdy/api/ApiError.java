package me.birdy.api;

import java.util.Map;

/** Error body of the API. {@code fields} is only set (and only serialised) for validation failures. */
public record ApiError(String error, String message, Map<String, String> fields) {
}
