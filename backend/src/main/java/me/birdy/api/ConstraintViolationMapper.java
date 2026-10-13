package me.birdy.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Turns Bean Validation failures into {@code 400 validation_failed} with one entry per invalid field. */
@Provider
public class ConstraintViolationMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        return ErrorResponses.validation("Request validation failed", fields(exception.getConstraintViolations()));
    }

    /** Maps each violation to its field name (the last node of the property path) and message. */
    public static Map<String, String> fields(Set<? extends ConstraintViolation<?>> violations) {
        Map<String, String> fields = new TreeMap<>();
        for (ConstraintViolation<?> violation : violations) {
            // When a field breaks several rules, keep the first message in alphabetical order to stay deterministic.
            fields.merge(fieldName(violation.getPropertyPath()), violation.getMessage(),
                    (first, second) -> first.compareTo(second) <= 0 ? first : second);
        }
        return fields;
    }

    private static String fieldName(Path path) {
        String name = "";
        for (Path.Node node : path) {
            if (node.getName() != null) {
                name = node.getName();
            }
        }
        return name;
    }
}
