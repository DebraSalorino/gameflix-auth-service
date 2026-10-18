package com.gameflix.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The 12 automated tests from SPEC.md section 6. Together they are the evidence for the
 * assignment's six required cases (successful registration, duplicate email, successful login,
 * wrong password, /me with a valid token, /me with a tampered token) plus the hashed-password,
 * expired-token and CORS checks.
 *
 * <p>Where it sits: these tests start the whole application (controllers, services, Spring
 * Security, JPA) against the real MySQL database {@code auth_test}, and send HTTP requests through
 * MockMvc, a fake HTTP client that calls the app without opening a network port. Every request
 * uses {@code .andDo(print())} so the status and body appear in the test log for screenshots.
 *
 * <p>Requires: MySQL running locally with the {@code auth_test} database, and
 * {@code application-local.properties} (MYSQL_USER, MYSQL_PASSWORD, JWT_SECRET) in the project root.
 */
// @SpringBootTest: start the full application, exactly as ./mvnw spring-boot:run would.
@SpringBootTest
// @AutoConfigureMockMvc: create a MockMvc object that sends requests through the real security filters.
@AutoConfigureMockMvc
// @ActiveProfiles("test"): also load application-test.properties (auth_test database, create-drop).
@ActiveProfiles("test")
class AuthApiTests {

    // The same sample account used in SPEC.md section 2.
    private static final String USERNAME = "debra_s";
    private static final String EMAIL = "debra@example.com";
    private static final String PASSWORD = "correct-horse-battery";

    private final MockMvc mockMvc;
    private final UserRepository userRepository;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    /**
     * Receives the objects the tests need from the running application.
     *
     * @param mockMvc        sends fake HTTP requests to the app
     * @param userRepository used to clear the table and to read the stored hash directly
     * @param jwtEncoder     the app's own token signer (used to mint an already-expired token)
     * @param jwtDecoder     the app's own token checker (used to read a token's claims)
     */
    // @Autowired: in a test class, Spring needs this to know it should fill the constructor parameters.
    @Autowired
    AuthApiTests(MockMvc mockMvc, UserRepository userRepository, JwtEncoder jwtEncoder, JwtDecoder jwtDecoder) {
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
    }

    /** Empties the users table so every test starts from the same clean state. */
    // @BeforeEach: JUnit runs this method before every single test.
    @BeforeEach
    void clearUsers() {
        userRepository.deleteAll();
    }

    // ------------------------------------------------------------------ register

    /**
     * Required case 1: successful registration.
     * Sends POST /api/auth/register with a valid username, email and password.
     * Expects 201 and the user JSON (id, username, email, role USER, createdAt) with no password field.
     */
    // @Test: JUnit runs this method as one test. "throws Exception": MockMvc's methods can throw
    // checked exceptions; letting them escape simply makes the test fail with the error shown.
    // jsonPath("$.id"): "$" is the top of the JSON response body, so "$.id" is its "id" field.
    @Test
    void register_success_returns201_withoutPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(USERNAME, EMAIL, PASSWORD)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.createdAt").value(notNullValue()))
                // SECURITY: neither the password nor its hash may appear in the response.
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    /**
     * Required case 2: duplicate email.
     * Registers debra@example.com, then registers again with the same email (different username,
     * and different capitalisation to prove the email is normalized).
     * Expects 409 with {"message":"Email already registered"}, produced by the database constraint.
     */
    @Test
    void register_duplicateEmail_returns409() throws Exception {
        registerUser(USERNAME, EMAIL, PASSWORD);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("another_user", "Debra@Example.com", PASSWORD)))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    /**
     * Invalid input is rejected with 400 (two requests).
     * Request 1: a valid body except an 11-character password → 400 with
     * {"message":"Invalid input","errors":{"password":"size must be between 12 and 64"}}.
     * Request 2: malformed JSON → 400 with {"message":"Invalid input"}; also confirms the exception
     * Spring throws for an unreadable body is HttpMessageNotReadableException (SPEC.md section 4).
     */
    @Test
    void register_invalidInput_returns400() throws Exception {
        // Request 1: "elevenchars" is 11 characters, one below the minimum of 12.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(USERNAME, EMAIL, "elevenchars")))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid input"))
                .andExpect(jsonPath("$.errors.password").value("size must be between 12 and 64"));

        // Request 2: the JSON is cut off (no closing quote or brace), so it cannot be parsed.
        MvcResult malformedResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"debra_s\",\"email\":"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid input"))
                .andReturn();

        // Confirm which exception reached GlobalExceptionHandler for the malformed body.
        assertInstanceOf(HttpMessageNotReadableException.class, malformedResult.getResolvedException());
    }

    // ------------------------------------------------------------------ login

    /**
     * Required case 3: successful login.
     * Registers a user, then sends POST /api/auth/login with the right email and password.
     * Expects 200 with accessToken, tokenType "Bearer" and expiresIn 900. Then checks the token
     * with the app's own JwtDecoder (which verifies the HS256 signature) and asserts that exp is
     * present and that no email or password claim is in the payload.
     */
    @Test
    void login_success_returns200_withSignedTokenHavingExp() throws Exception {
        registerUser(USERNAME, EMAIL, PASSWORD);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(EMAIL, PASSWORD)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(notNullValue()))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn();

        // JsonPath.read pulls one field out of the JSON text: here the "accessToken" value.
        String token = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");

        // decode() throws if the signature is wrong, so reaching the next line proves the token is signed by us.
        Jwt decodedToken = jwtDecoder.decode(token);

        // exp must exist and be 900 seconds after iat.
        assertNotNull(decodedToken.getExpiresAt());
        assertEquals(decodedToken.getIssuedAt().plusSeconds(900), decodedToken.getExpiresAt());

        // SECURITY: the payload is readable by anyone, so it must not contain personal data.
        assertFalse(decodedToken.hasClaim("email"));
        assertFalse(decodedToken.hasClaim("password"));
    }

    /**
     * Required case 4: wrong password.
     * Registers a user, then logs in with the right email but a wrong password.
     * Expects 401 with the generic {"message":"Invalid email or password"}.
     */
    @Test
    void login_wrongPassword_returns401_genericMessage() throws Exception {
        registerUser(USERNAME, EMAIL, PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(EMAIL, "wrong-password-123")))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    /**
     * Generic login failure for an unknown email.
     * Logs in with an email that was never registered.
     * Expects exactly the same 401 and {"message":"Invalid email or password"} as a wrong password,
     * so an attacker cannot tell which emails exist.
     */
    @Test
    void login_unknownEmail_returnsSame401Message() throws Exception {
        // First get the wrong-password message for a real account, to compare against.
        registerUser(USERNAME, EMAIL, PASSWORD);
        MvcResult wrongPasswordResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(EMAIL, "wrong-password-123")))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andReturn();

        // Now the unknown email.
        MvcResult unknownEmailResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("nobody@example.com", PASSWORD)))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andReturn();

        // The two response bodies must be identical, character for character.
        assertEquals(wrongPasswordResult.getResponse().getContentAsString(),
                unknownEmailResult.getResponse().getContentAsString());
    }

    // ------------------------------------------------------------------ /api/users/me

    /**
     * Required case 5: /me with a valid token.
     * Registers, logs in, then sends GET /api/users/me with "Authorization: Bearer <token>".
     * Expects 200 with the same user JSON the register call returned (including the same createdAt),
     * and no password field.
     */
    @Test
    void me_validToken_returns200_withOwnProfile() throws Exception {
        String registerBody = registerUser(USERNAME, EMAIL, PASSWORD);
        Integer registeredId = JsonPath.read(registerBody, "$.id");
        String registeredCreatedAt = JsonPath.read(registerBody, "$.createdAt");
        String token = loginAndGetToken(EMAIL, PASSWORD);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(registeredId))
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.createdAt").value(registeredCreatedAt))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    /**
     * /me without a token.
     * Sends GET /api/users/me with no Authorization header.
     * Expects 401 from Spring Security, with a WWW-Authenticate header.
     */
    @Test
    void me_missingToken_returns401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"));
    }

    /**
     * Required case 6: /me with a tampered token.
     * Logs in to get a real token, changes one character of its signature, and sends it to
     * GET /api/users/me. Expects 401, because the signature no longer matches.
     */
    @Test
    void me_tamperedToken_returns401() throws Exception {
        registerUser(USERNAME, EMAIL, PASSWORD);
        String token = loginAndGetToken(EMAIL, PASSWORD);

        // A JWT is "header.payload.signature". Change one character a few places into the
        // signature. (Not the very last character: in Base64URL its lowest bits can be padding,
        // so changing it might not change the decoded signature at all.)
        int signatureStart = token.lastIndexOf('.') + 1;
        int positionToChange = signatureStart + 5;
        char originalChar = token.charAt(positionToChange);
        char replacementChar = 'A';
        if (originalChar == 'A') {
            replacementChar = 'B';
        }
        String tamperedToken = token.substring(0, positionToChange)
                + replacementChar
                + token.substring(positionToChange + 1);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + tamperedToken))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    /**
     * /me with an expired token.
     * Uses the app's own JwtEncoder to sign an otherwise valid token whose exp was 5 minutes ago,
     * and sends it to GET /api/users/me. Expects 401, because the decoder checks exp.
     */
    @Test
    void me_expiredToken_returns401() throws Exception {
        String registerBody = registerUser(USERNAME, EMAIL, PASSWORD);
        Integer userId = JsonPath.read(registerBody, "$.id");

        // Issued 20 minutes ago with the normal 15-minute lifetime, so it expired 5 minutes ago.
        Instant expiresAt = Instant.now().minusSeconds(5 * 60);
        Instant issuedAt = expiresAt.minusSeconds(900);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("gameflix-auth")
                .subject(String.valueOf(userId))
                .claim("role", "USER")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
        String expiredToken = jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + expiredToken))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ storage and CORS

    /**
     * The stored password is a bcrypt hash, not plaintext.
     * Registers a user, then reads the row straight from the database.
     * Expects the password column to start with "{bcrypt}$2", use strength 12, and not equal the
     * submitted password.
     */
    @Test
    void storedPassword_isBcryptHash_notPlaintext() throws Exception {
        registerUser(USERNAME, EMAIL, PASSWORD);

        Optional<User> storedUser = userRepository.findByEmail(EMAIL);
        assertTrue(storedUser.isPresent());
        String storedPassword = storedUser.get().getPassword();

        assertTrue(storedPassword.startsWith("{bcrypt}$2"));
        // "$12$" is the work factor chosen in SecurityConfig.
        assertTrue(storedPassword.contains("$12$"));
        assertNotEquals(PASSWORD, storedPassword);
    }

    /**
     * CORS allows only the configured origin.
     * Sends two browser "preflight" OPTIONS requests to /api/auth/login: one from
     * http://localhost:8081 (allowed) and one from http://evil.example (not allowed).
     * Expects 200 with Access-Control-Allow-Origin: http://localhost:8081 for the first, and
     * 403 with no Access-Control-Allow-Origin header for the second.
     */
    @Test
    void cors_allowedOrigin_accepted_otherOrigin_rejected() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:8081")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8081"))
                .andExpect(header().string("Access-Control-Allow-Origin", not("*")));

        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Registers a user and checks that it worked.
     *
     * @return the 201 response body (the user JSON)
     */
    private String registerUser(String username, String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, email, password)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    /**
     * Logs in and returns the access token from the 200 response.
     *
     * @return the JWT string
     */
    private String loginAndGetToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, password)))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    /** @return a register request body as JSON text */
    private String registerJson(String username, String email, String password) {
        return "{\"username\":\"" + username + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    /** @return a login request body as JSON text */
    private String loginJson(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }
}
