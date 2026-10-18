package com.gameflix.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The JSON body of {@code POST /api/auth/login}, for example
 * {@code {"email":"debra@example.com","password":"correct-horse-battery"}}.
 *
 * <p>Where it sits: Spring turns the request JSON into this record before
 * {@code AuthController.login} runs; {@code @Valid} there checks the rules below.
 *
 * <p>Satisfies SPEC.md section 2 ("Login request: email and password required, no other format
 * rules").
 *
 * @param email    the email the account was registered with
 * @param password the plaintext password to check
 */
// SECURITY: only "required" checks here. Format rules (length, email shape) on login would tell
// an attacker something about valid accounts and add no protection.
public record LoginRequest(
        // @NotBlank: must be present and not just spaces.
        @NotBlank
        String email,

        @NotBlank
        String password) {
}
