package com.gameflix.auth.dto;

import com.gameflix.auth.User;

import java.time.Instant;

/**
 * The public view of an account, returned by {@code POST /api/auth/register} (201) and
 * {@code GET /api/users/me} (200), for example
 * {@code {"id":1,"username":"debra_s","email":"debra@example.com","role":"USER","createdAt":"2026-10-18T20:15:00Z"}}.
 *
 * <p>Where it sits: the service builds it from a {@link User} and the controller returns it.
 *
 * <p>Satisfies SPEC.md section 2 ("Never return the User entity from a controller. No response
 * type has a password field.").
 *
 * @param id        the database id
 * @param username  the username
 * @param email     the normalized email
 * @param role      the role name, "USER" or "ADMIN"
 * @param createdAt when the account was created (UTC), written as an ISO-8601 string
 */
// SECURITY: there is deliberately no password field, so the hash can never be sent to a client.
public record UserResponse(Long id, String username, String email, String role, Instant createdAt) {

    /**
     * Copies the safe fields of a {@link User} into a response.
     *
     * @param user a saved user
     * @return the response record, without the password hash
     */
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.getCreatedAt());
    }
}
