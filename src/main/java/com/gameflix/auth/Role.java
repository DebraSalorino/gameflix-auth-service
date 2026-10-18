package com.gameflix.auth;

/**
 * The permission level of a user account.
 *
 * <p>Where it sits: stored on every {@link User} row (database column {@code role}) and copied
 * into the {@code role} claim of the login token by {@link TokenService}.
 *
 * <p>Satisfies SPEC.md section 3 (column {@code role}, {@code enum Role { USER, ADMIN }}).
 * Every new registration gets {@code USER}; {@code ADMIN} exists for later modules.
 */
public enum Role {
    USER,
    ADMIN
}
