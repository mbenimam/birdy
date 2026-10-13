package me.birdy.api;

import jakarta.validation.Validator;
import java.util.Map;
import java.util.Set;

/** Runs Bean Validation on request records the way the API reports it: field name to message. */
public final class Validation {

    private static final Validator VALIDATOR =
            jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();

    private Validation() {
    }

    public static Map<String, String> problems(Object request) {
        return ConstraintViolationMapper.fields(VALIDATOR.validate(request));
    }

    public static Set<String> invalidFields(Object request) {
        return problems(request).keySet();
    }
}
