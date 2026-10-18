package com.gameflix.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * One registered account, stored as one row of the MySQL table {@code users}.
 *
 * <p>Where it sits in the request flow: controller → service → repository → <b>database</b>.
 * {@link AuthService} creates a {@code User} and {@link UserRepository} saves and loads it.
 * Controllers never return this class; they return {@code dto.UserResponse} instead, so the
 * password hash can never leak into a response.
 *
 * <p>Satisfies SPEC.md section 3 (the {@code users} table) and the assignment requirements
 * "MySQL via Spring Data JPA", "password column sized for the hash (VARCHAR(255))" and
 * "email uniqueness by database constraint".
 */
// @Entity: tells JPA (Java Persistence API) that this class maps to a database table.
@Entity
// @Table: names the table and declares the two unique constraints. The database itself
// enforces them, and their names let GlobalExceptionHandler tell which one was violated.
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_username", columnNames = "username"),
                @UniqueConstraint(name = "uk_users_email", columnNames = "email")
        })
public class User {

    // @Id: this field is the primary key.
    // @GeneratedValue(IDENTITY): MySQL's AUTO_INCREMENT picks the number when the row is inserted.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @Column: column details. VARCHAR(30) matches the 30-character maximum in the username rule.
    @Column(name = "username", nullable = false, length = 30)
    private String username;

    // 254 characters is the longest valid email address.
    @Column(name = "email", nullable = false, length = 254)
    private String email;

    // SECURITY: holds the encoded bcrypt hash ("{bcrypt}$2a$12$...", 68 characters), never the
    // plaintext password. VARCHAR(255) leaves room; CHAR(60) would cut the hash short.
    @Column(name = "password", nullable = false, length = 255)
    private String password;

    // @Enumerated(STRING): store the enum's name ("USER"), not its position number, so reordering
    // the enum later can't silently change anyone's role. Every new account starts as USER.
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role = Role.USER;

    // When the account was created, in UTC. updatable = false: never changed after the insert.
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Empty constructor that JPA requires so it can build a {@code User} when loading a row.
     * {@code protected} so application code uses the constructor below instead.
     */
    protected User() {
    }

    /**
     * Creates a new, not-yet-saved account. The id and createdAt are filled in when it is saved.
     *
     * @param username     the validated username
     * @param email        the email, already trimmed and lower-cased
     * @param passwordHash the encoded password hash (never the plaintext password)
     */
    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.password = passwordHash;
    }

    /**
     * Sets {@code createdAt} just before the row is first inserted.
     * Setting it here means no caller can forget it or supply a wrong value.
     */
    // @PrePersist: JPA calls this method automatically right before the INSERT.
    @PrePersist
    void onCreate() {
        // Instant is always UTC, which matches serverTimezone=UTC in the JDBC URL.
        // Cut the time down to microseconds: MySQL's DATETIME(6) column only stores 6 decimal
        // places, while Instant.now() can carry nanoseconds. Without this, the register response
        // (built from this object) and /api/users/me (read back from the database) would show
        // slightly different createdAt values for the same account.
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** @return the database id, or null before the user is saved */
    public Long getId() {
        return id;
    }

    /** @return the username */
    public String getUsername() {
        return username;
    }

    /** @return the normalized (trimmed, lower-case) email */
    public String getEmail() {
        return email;
    }

    /** @return the encoded password hash; used only by AuthService to check a login */
    public String getPassword() {
        return password;
    }

    /** @return the account's role */
    public Role getRole() {
        return role;
    }

    /** @return when the account was created (UTC) */
    public Instant getCreatedAt() {
        return createdAt;
    }
}
