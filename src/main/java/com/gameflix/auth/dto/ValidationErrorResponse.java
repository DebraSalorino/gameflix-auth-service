package com.gameflix.auth.dto;

import java.util.Map;

/**
 * The 400 body for a request that broke a validation rule, for example
 * {@code {"message":"Invalid input","errors":{"password":"size must be between 12 and 64"}}}.
 *
 * <p>Where it sits: built only by {@code GlobalExceptionHandler}.
 *
 * <p>Satisfies SPEC.md section 2 (register 400 body) and section 4 ("MethodArgumentNotValidException
 * → 400 with field errors map").
 *
 * @param message always "Invalid input"
 * @param errors  field name → what is wrong with that field
 */
public record ValidationErrorResponse(String message, Map<String, String> errors) {
}
