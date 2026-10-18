package com.gameflix.auth;

/**
 * Thrown by {@link AuthService#login} when the email is unknown or the password is wrong.
 *
 * <p>Where it sits: service → (exception) → {@link GlobalExceptionHandler}, which turns it into
 * 401 {@code {"message":"Invalid email or password"}}.
 *
 * <p>Satisfies SPEC.md section 4 ("A custom InvalidCredentialsException → 401") and the assignment
 * requirement "generic login failure".
 */
// SECURITY: one exception for both failure reasons, so the caller can never tell which one happened.
public class InvalidCredentialsException extends RuntimeException {

    /** Creates the exception with a fixed message. The message is never sent to the client. */
    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
