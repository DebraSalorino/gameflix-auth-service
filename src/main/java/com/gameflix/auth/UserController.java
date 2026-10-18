package com.gameflix.auth;

import com.gameflix.auth.dto.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * The protected endpoint {@code GET /api/users/me}: returns the profile of whoever sent the token.
 *
 * <p>Where it sits: Spring Security has already checked the Bearer token before this controller
 * runs (a missing, tampered or expired token gets 401 without reaching here). Then
 * <b>controller</b> → {@link AuthService} → repository → database.
 *
 * <p>Satisfies SPEC.md section 2 ({@code GET /api/users/me}: 200 / 401) and section 4
 * ("/api/users/me: take @AuthenticationPrincipal Jwt jwt ...").
 */
// @RestController: each method's return value is written to the response as JSON.
@RestController
// @RequestMapping: every path in this class starts with /api/users.
@RequestMapping("/api/users")
public class UserController {

    private final AuthService authService;

    /** @param authService loads users by id */
    public UserController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Returns the caller's own account.
     *
     * @param jwt the already-verified token of the caller
     * @return 200 OK with the caller's user (no password), or 401 with an empty body if the
     *         account in the token no longer exists
     */
    // @GetMapping: this method handles GET /api/users/me.
    // @AuthenticationPrincipal: hands us the verified token that Spring Security stored for this request.
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal Jwt jwt) {
        // The "sub" (subject) claim holds the user id as text; we put it there in TokenService.
        // The signature was already verified, so we can trust this value.
        Long userId = Long.parseLong(jwt.getSubject());

        Optional<UserResponse> currentUser = authService.findUserById(userId);

        // SECURITY: a valid token for a deleted account must not reveal anything; treat it as unauthenticated.
        if (currentUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(currentUser.get());
    }
}
