package com.gameflix.auth.dto;

/**
 * A simple error body with one message, for example {@code {"message":"Email already registered"}}.
 *
 * <p>Where it sits: built only by {@code GlobalExceptionHandler} when a request fails.
 *
 * <p>Satisfies SPEC.md section 2 (the 400, 401 and 409 bodies) and section 4 ("No stack traces or
 * exception messages in response bodies": the message is always one of our fixed strings).
 *
 * @param message a short, fixed, human-readable description of the problem
 */
public record ErrorResponse(String message) {
}
