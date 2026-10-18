package com.gameflix.auth;

import com.gameflix.auth.dto.LoginRequest;
import com.gameflix.auth.dto.RegisterRequest;
import com.gameflix.auth.dto.TokenResponse;
import com.gameflix.auth.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two public endpoints: {@code POST /api/auth/register} and {@code POST /api/auth/login}.
 *
 * <p>Where it sits: <b>controller</b> → {@link AuthService} → repository → database. It only
 * turns HTTP into method calls and back; the rules live in the service, and every error response
 * is built by {@link GlobalExceptionHandler}.
 *
 * <p>Satisfies SPEC.md section 2 (register: 201 / 400 / 409; login: 200 / 401).
 */
// @RestController: each method's return value is written to the response as JSON.
@RestController
// @RequestMapping: every path in this class starts with /api/auth.
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    /** @param authService registers users and checks logins */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Registers a new account.
     *
     * @param request the JSON body (username, email, password)
     * @return 201 Created with the new user (no password).
     *         Errors: 400 if the input breaks a rule or the JSON is malformed;
     *         409 if the email or username is already registered.
     */
    // @PostMapping: this method handles POST /api/auth/register.
    // @Valid: check the rules on RegisterRequest before this method runs (failure → 400).
    // @RequestBody: build the RegisterRequest from the request's JSON body.
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse createdUser = authService.register(request);
        // ResponseEntity: the whole HTTP response (status code + body). Spring writes the body as JSON.
        // 201 (not 200) because a new resource was created.
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    /**
     * Logs in with email and password.
     *
     * @param request the JSON body (email, password)
     * @return 200 OK with the signed token.
     *         Errors: 400 if a field is missing or the JSON is malformed;
     *         401 "Invalid email or password" for an unknown email or a wrong password.
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse token = authService.login(request);
        return ResponseEntity.ok(token);
    }
}
