package com.gameflix.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Reads and writes {@link User} rows in the {@code users} table.
 *
 * <p>Where it sits: controller → service → <b>repository</b> → database. Spring Data JPA writes
 * the implementation of this interface at startup, so there is no class to write by hand.
 * Inherited methods used by this project: {@code saveAndFlush} (register) and {@code findById}
 * ({@code /api/users/me}).
 *
 * <p>Satisfies SPEC.md section 3 ("UserRepository extends JpaRepository&lt;User, Long&gt; with
 * findByEmail").
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds the account with this email. Spring builds the query from the method name
     * ({@code SELECT ... FROM users WHERE email = ?}).
     *
     * @param email the email, already trimmed and lower-cased
     * @return the matching user, or an empty Optional if no account uses this email
     */
    Optional<User> findByEmail(String email);
}
