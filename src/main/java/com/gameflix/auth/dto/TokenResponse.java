package com.gameflix.auth.dto;

/**
 * The JSON body of a successful login (200), for example
 * {@code {"accessToken":"<jwt>","tokenType":"Bearer","expiresIn":900}}.
 *
 * <p>Where it sits: {@code TokenService} builds it and {@code AuthController.login} returns it.
 *
 * <p>Satisfies SPEC.md section 2 (login success body) and section 4 ("expiresIn in the login
 * response = ttlSeconds").
 *
 * @param accessToken the signed JWT (JSON Web Token: a signed string that proves who the caller is)
 * @param tokenType   always "Bearer": the client sends {@code Authorization: Bearer <token>}
 * @param expiresIn   how many seconds the token stays valid
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
