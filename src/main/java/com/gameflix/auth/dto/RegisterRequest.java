package com.gameflix.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The JSON body of {@code POST /api/auth/register}, for example
 * {@code {"username":"debra_s","email":"debra@example.com","password":"correct-horse-battery"}}.
 *
 * <p>Where it sits: Spring turns the request JSON into this record before
 * {@code AuthController.register} runs; {@code @Valid} there checks the rules below. Any broken
 * rule becomes a 400 response from {@code GlobalExceptionHandler}.
 *
 * <p>A record is a short way to write a class whose only job is to hold fixed values.
 *
 * <p>Satisfies SPEC.md section 2 ("Validation") and the 400 case of the register endpoint.
 *
 * @param username 3 to 30 characters: letters, digits, underscore or hyphen, starting with a
 *                 letter or digit (SRS REQ-ACC-001 to 003)
 * @param email    a valid email address of at most 254 characters
 * @param password 12 to 64 characters; never trimmed or changed
 */
public record RegisterRequest(
        // @NotBlank: must be present and not just spaces. @Pattern: must match the regular expression.
        // Reading the pattern: ^ start, [A-Za-z0-9] one letter or digit, [A-Za-z0-9_-]{2,29}
        // then 2 to 29 more letters, digits, underscores or hyphens, $ end. Total: 3 to 30 characters.
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{2,29}$")
        String username,

        // @NotBlank is needed as well as @Email, because @Email alone accepts an empty string.
        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        // SECURITY: minimum 12 is the SRS rule (REQ-ACC-006). Maximum 64 because bcrypt ignores
        // everything after 72 bytes, so a longer password would not be fully protected.
        @NotBlank
        @Size(min = 12, max = 64)
        String password) {
}
