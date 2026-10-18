package com.gameflix.auth;

import com.gameflix.auth.dto.LoginRequest;
import com.gameflix.auth.dto.RegisterRequest;
import com.gameflix.auth.dto.TokenResponse;
import com.gameflix.auth.dto.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The business rules for registering, logging in and looking up the current user.
 *
 * <p>Where it sits: controller → <b>AuthService</b> → {@link UserRepository} → database.
 * It hashes passwords, checks logins and asks {@link TokenService} for tokens.
 *
 * <p>Satisfies SPEC.md section 3 (409 comes from the database constraint, no @Transactional here)
 * and section 4 ("Password hashing": bcrypt, one matches() per login, dummy hash for unknown emails).
 */
// @Service: tells Spring to create one shared instance of this class and inject it where needed.
// Deliberately NOT @Transactional (SPEC.md section 3): saveAndFlush already runs in its own
// transaction, which rolls back cleanly when the insert breaks a unique constraint.
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    // A real bcrypt hash of a random value nobody knows. Used when the email is not found.
    private final String dummyPasswordHash;

    /**
     * @param userRepository  reads and writes users
     * @param passwordEncoder the bcrypt encoder from {@link SecurityConfig}
     * @param tokenService    creates login tokens
     */
    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;

        // SECURITY: hash a random UUID once at startup. A login for an unknown email is checked
        // against this hash, so it costs the same bcrypt time as a wrong password for a real email.
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Creates a new account.
     *
     * @param request the validated registration data
     * @return the saved user, without the password
     * @throws org.springframework.dao.DataIntegrityViolationException if the email or username is
     *         already taken; {@link GlobalExceptionHandler} turns it into 409
     */
    public UserResponse register(RegisterRequest request) {
        // Store emails in one form so "Debra@Example.com " and "debra@example.com" are the same account.
        String normalizedEmail = normalizeEmail(request.email());

        // SECURITY: store only the bcrypt hash. The password itself is used exactly as typed (never trimmed).
        String passwordHash = passwordEncoder.encode(request.password());

        User newUser = new User(request.username(), normalizedEmail, passwordHash);

        // Insert right away (flush) so a duplicate email or username fails here, inside the
        // repository's own transaction. We do not check "does this email exist?" first: two
        // requests could both pass that check at the same moment. The unique constraint in the
        // database is the only check that cannot be raced, so we let it decide and let the
        // exception travel to GlobalExceptionHandler.
        User savedUser = userRepository.saveAndFlush(newUser);

        return UserResponse.from(savedUser);
    }

    /**
     * Checks an email and password and, if they match, issues a token.
     *
     * @param request the login data
     * @return the signed token and its lifetime
     * @throws InvalidCredentialsException if the email is unknown or the password is wrong (→ 401)
     */
    public TokenResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        Optional<User> foundUser = userRepository.findByEmail(normalizedEmail);

        // SECURITY: run exactly one bcrypt check whether or not the email exists. If we returned
        // early for an unknown email, that response would be measurably faster, and an attacker
        // could use the timing to find out which emails are registered.
        if (foundUser.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new InvalidCredentialsException();
        }

        User user = foundUser.get();
        boolean passwordMatches = passwordEncoder.matches(request.password(), user.getPassword());
        if (!passwordMatches) {
            // SECURITY: same exception (and so the same 401 message) as the unknown-email case.
            throw new InvalidCredentialsException();
        }

        return tokenService.createToken(user);
    }

    /**
     * Loads the account whose id is in a verified token.
     *
     * @param userId the id from the token's {@code sub} claim
     * @return the user, or an empty Optional if the account no longer exists
     */
    public Optional<UserResponse> findUserById(Long userId) {
        Optional<User> foundUser = userRepository.findById(userId);
        if (foundUser.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(UserResponse.from(foundUser.get()));
    }

    /**
     * Puts an email into the one form we store and search by.
     *
     * @param email the email as the client sent it
     * @return the email without surrounding spaces, in lower case
     */
    private String normalizeEmail(String email) {
        // Locale.ROOT: lower-case the same way on every computer (some languages change letters
        // differently, for example the Turkish dotless i).
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
