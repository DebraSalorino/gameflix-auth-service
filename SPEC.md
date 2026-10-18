# SPEC: GameFlix Auth Service (Module 4: Login & Registration Endpoints)

Author: Debra Salorino · Course: HCDD 412 · Due: Mon Oct 19, 2026, 11:59 pm
This file is the build specification. Claude Code builds from it; the human reviews against it.

**Priority order for every decision:** (1) the assignment requirements below, (2) best practice, (3) the simplest option that satisfies 1 and 2. If something here conflicts with the assignment, the assignment wins. Stop and ask instead of guessing.

---

## 1. Stack

- Java 21, Spring Boot **4.x latest stable** (no SNAPSHOT, M, or RC), Maven with the Maven wrapper.
- Dependencies (Spring Initializr ids): `web`, `security`, `oauth2-resource-server`, `data-jpa`, `mysql`, `validation`. Keep the test starters Initializr adds for these.
- No Thymeleaf, no DevTools, no Lombok, no third-party JWT library (use Spring Security's built-in Nimbus support), no Flyway.
- Spring Boot 4 rules: use `spring-boot-starter-webmvc`; Jackson 3 (`tools.jackson.*`, but annotations stay `com.fasterxml.jackson.annotation`); `@MockitoBean`, not `@MockBean`; `@SpringBootTest` + `@AutoConfigureMockMvc` for MockMvc. Spring Security 7: lambda DSL `SecurityFilterChain` bean only. **Never** `WebSecurityConfigurerAdapter`, `antMatchers`, or `authorizeRequests`.
- groupId `com.gameflix`, artifactId `gameflix-auth-service`, base package `com.gameflix.auth`, server port 8080.

## 1a. Code style: simple first, commented for a learner

The reader is a student who must explain every line on camera. Optimize for readability over cleverness.

**Keep it simple**
- Fewest classes that satisfy the spec. Suggested set (one job each): `User`, `Role`, `UserRepository`, `dto/` records, `AuthService`, `TokenService`, `SecurityConfig` (security, CORS, password encoder, JWT encoder/decoder in one file), `AuthController`, `UserController`, `InvalidCredentialsException`, `GlobalExceptionHandler`. No interfaces with one implementation, no mappers, no base classes, no custom annotations, no builders where a constructor works.
- Plain constructor injection (`final` fields, one constructor). No field `@Autowired`.
- Straight-line code: early returns, `if` statements, and local variables with descriptive names. Avoid long stream chains, nested lambdas beyond what Spring Security's DSL requires, ternaries inside ternaries, and method references where a lambda or loop is clearer.
- Spell types out where it helps understanding (avoid `var`).
- No code the spec doesn't ask for. No "just in case" features, no unused methods or imports.

**Comment extensively**
- Every class starts with a Javadoc block: what the class is responsible for, where it sits in the request flow (controller → service → repository → database), and which SPEC.md section and assignment requirement it satisfies.
- Every method has a Javadoc block: what it does, its inputs, what it returns, and what errors or status codes can result.
- Inside methods, a plain-English comment above each logical step explaining **why**, not only what (e.g. why the dummy hash runs, why CSRF is off, why we catch the constraint violation instead of checking first).
- Every annotation that a beginner wouldn't recognize gets a one-line comment the first time it appears in each file (`@Entity`, `@PrePersist`, `@Valid`, `@RestControllerAdvice`, `@AuthenticationPrincipal`, `@Transactional`, `@Bean`, etc.).
- Every line of the `SecurityFilterChain` gets its own comment.
- Mark each security-critical decision with a comment starting `// SECURITY:` so it can be found with one search.
- Comments in `application.properties` and the test profile explain every setting.
- Each test has a comment stating which required case it proves, the request it sends, and the status code and body it expects.
- Plain words in comments; define a term the first time it's used (e.g. "JWT (JSON Web Token): a signed string that proves who the caller is").

## 2. Endpoints

| Endpoint | Auth | Success | Failures |
|---|---|---|---|
| `POST /api/auth/register` | public | **201** + user JSON (no password) | **400** invalid input · **409** email (or username) already registered |
| `POST /api/auth/login` | public | **200** + token JSON | **401** on any credential failure, one generic message |
| `GET /api/users/me` | Bearer JWT | **200** + caller's user JSON | **401** missing, invalid, tampered, or expired token |

### Request and response bodies

```
POST /api/auth/register   {"username":"debra_s","email":"debra@example.com","password":"correct-horse-battery"}
201                       {"id":1,"username":"debra_s","email":"debra@example.com","role":"USER","createdAt":"2026-10-18T20:15:00Z"}
400                       {"message":"Invalid input","errors":{"password":"size must be between 12 and 64"}}
409                       {"message":"Email already registered"}     (or "Username already taken")

POST /api/auth/login      {"email":"debra@example.com","password":"correct-horse-battery"}
200                       {"accessToken":"<jwt>","tokenType":"Bearer","expiresIn":900}
401                       {"message":"Invalid email or password"}

GET /api/users/me         Authorization: Bearer <jwt>
200                       same shape as the 201 register body
401                       Spring Security's default 401 (empty body, WWW-Authenticate header) is acceptable
```

Use Java `record` DTOs for every request and response. **Never return the `User` entity from a controller.** No response type has a password field.

### Validation (Bean Validation on request records, `@Valid` in controllers)

- `username`: required, regex `^[A-Za-z0-9][A-Za-z0-9_-]{2,29}$` (SRS REQ-ACC-001 to 003).
- `email`: required (`@NotBlank`; `@Email` alone accepts an empty string), `@Email`, max 254. Normalize with `trim()` + `toLowerCase(Locale.ROOT)` before saving or looking up.
- `password`: required, `@Size(min = 12, max = 64)`. Never trim or otherwise modify it. 12 is the SRS minimum (REQ-ACC-006); 64 is the assignment's cap because bcrypt truncates at 72 bytes.
- Login request: `email` and `password` required (`@NotBlank`), no other format rules (format rules on login would leak information and add nothing).
- Malformed JSON or a missing body → 400 with `{"message":"Invalid input"}`.

## 3. Persistence (MySQL, Spring Data JPA)

- Database `auth` (existing schema from Module 3) for running; database `auth_test` for automated tests.
- Entity `User` → table **`users`**:

| Column | Type | Rules |
|---|---|---|
| `id` | BIGINT | PK, auto-increment (`GenerationType.IDENTITY`) |
| `username` | VARCHAR(30) | NOT NULL, unique constraint named `uk_users_username` |
| `email` | VARCHAR(254) | NOT NULL, unique constraint named `uk_users_email` |
| `password` | **VARCHAR(255)** | NOT NULL. Holds the encoded hash, never plaintext. Column name must be `password` (grader runs `SELECT id, email, password FROM users;`) |
| `role` | ENUM('USER','ADMIN') | NOT NULL, `@Enumerated(EnumType.STRING)`, enum `Role { USER, ADMIN }`, field initialized to `Role.USER`. Hibernate maps a STRING enum to a native MySQL `enum` column; accept that, don't fight it. |
| `created_at` | DATETIME(6) | NOT NULL, set in `@PrePersist` (UTC `Instant`) |

- Declare both unique constraints in `@Table(uniqueConstraints = …)` with the names above so the database enforces them.
- `UserRepository extends JpaRepository<User, Long>` with `Optional<User> findByEmail(String email)`.
- **409 must come from the database constraint**, not from an `existsByEmail` pre-check. Save with `saveAndFlush` and let the `DataIntegrityViolationException` propagate to `GlobalExceptionHandler`, which chooses the message by which constraint name appears in the root-cause message (`uk_users_email` → "Email already registered", `uk_users_username` → "Username already taken"; neither → "Already registered"). **Do not put `@Transactional` on `AuthService`**: the repository method already runs in its own transaction, which rolls back cleanly when the insert fails. Catching the exception inside a service-level transaction would leave that transaction marked rollback-only and the commit would fail with a confusing `UnexpectedRollbackException`.
- `spring.jpa.hibernate.ddl-auto=update` for running (keeps data between runs for evidence; it creates the new `users` table and never touches the Module 3 tables). `create-drop` in the test profile only.
- `spring.jpa.open-in-view=false`. Do **not** enable Hibernate TRACE logging (the course setup page's `logging.level.org.hibernate.orm.results=TRACE` logs row values, including stored hashes).

## 4. Security

### Password hashing
- `PasswordEncoder` bean: `new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(12)))`. Stored values look like `{bcrypt}$2a$12$…` (68 chars). Do not use `PasswordEncoderFactories` (its bcrypt default is strength 10). Never `NoOpPasswordEncoder`, never MD5/SHA.
- Login always runs one `passwordEncoder.matches(...)`: against the user's hash if the email exists, otherwise against a dummy hash created once at startup (encode a random UUID). Then return the same 401. This keeps response time and message identical for unknown emails and wrong passwords (US-001 T-1.31).
- Never log a password, hash, token, or request body.

### JWT
- HS256 via Spring Security's `NimbusJwtEncoder` / `NimbusJwtDecoder` (from `spring-boot-starter-oauth2-resource-server`). No hand-written token-parsing filter.
- Key: property `gameflix.jwt.secret`, a Base64 string decoding to **at least 32 bytes**. Build one `SecretKeySpec(bytes, "HmacSHA256")`. Fail at startup with a clear message if missing or too short. **No default value anywhere in committed files** (no `${JWT_SECRET:something}`).
- Encoder bean: `new NimbusJwtEncoder(new ImmutableSecret<>(secretKey))` (`com.nimbusds.jose.jwk.source.ImmutableSecret`). When encoding, pass `JwsHeader.with(MacAlgorithm.HS256)` explicitly (otherwise Nimbus picks RS256 and fails). Encode with `jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue()`.
- Decoder: `NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build()` with validator `JwtValidators.createDefaultWithIssuer("gameflix-auth")` (checks `exp`, `nbf`, and `iss` after the signature is verified).
- Claims: `iss` = `gameflix-auth`, `sub` = user id as a string, `role`, `iat`, `exp`. **Nothing else.** No email, username, or password in the payload; it is Base64URL-encoded, not encrypted.
- Lifetime: property `gameflix.jwt.ttl-seconds=900` read with `@Value("${gameflix.jwt.ttl-seconds}") long ttlSeconds` (a plain number is simpler to explain than a `Duration` string). `exp` = `iat` + `ttlSeconds`; `expiresIn` in the login response = `ttlSeconds`.
- `/api/users/me`: take `@AuthenticationPrincipal Jwt jwt`, parse `jwt.getSubject()` to `Long`, load the user. If the user no longer exists, return `ResponseEntity.status(401).build()`.
- Startup note: with `spring-boot-starter-security` present and no `UserDetailsService` bean, Spring Boot may log `Using generated security password`. That default in-memory user is unreachable because `formLogin` and `httpBasic` are disabled. Say so in a comment in `SecurityConfig` and in one README line, so a grader who sees the log line knows it was considered.

### SecurityFilterChain
- `csrf` disabled, with a code comment explaining why: the API is stateless and the JWT travels in the `Authorization` header, never a cookie, so there is no ambient credential for CSRF to abuse.
- `sessionManagement` → `SessionCreationPolicy.STATELESS`.
- `POST /api/auth/register` and `POST /api/auth/login` → `permitAll()`; everything else → `authenticated()`.
- `oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))`.
- Disable `formLogin` and `httpBasic`.

### CORS
- One allowed origin from property `gameflix.cors.allowed-origin` (default `http://localhost:8081`, the planned Thymeleaf frontend). Methods GET, POST, OPTIONS. Headers `Authorization`, `Content-Type`. `allowCredentials(false)`. Never `*`.
- Wire it with a `CorsConfigurationSource` bean and `http.cors(Customizer.withDefaults())`.

### Error handling
One `@RestControllerAdvice`:
- `MethodArgumentNotValidException` → 400 with field errors map.
- `HttpMessageNotReadableException` → 400 `{"message":"Invalid input"}`. (Verify the exception type by sending malformed JSON in test 3; if Spring Framework 7 throws a different type for unreadable bodies, handle that one instead.)
- `DataIntegrityViolationException` → 409 (see section 3).
- A custom `InvalidCredentialsException` → 401 `{"message":"Invalid email or password"}`.
- No stack traces or exception messages in response bodies.

## 5. Configuration and secrets

- `application.properties` (committed), placeholders only:
  ```
  spring.application.name=gameflix-auth-service
  spring.config.import=optional:file:./application-local.properties
  spring.datasource.url=jdbc:mysql://localhost:3306/auth?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC
  spring.datasource.username=${MYSQL_USER}
  spring.datasource.password=${MYSQL_PASSWORD}
  spring.jpa.hibernate.ddl-auto=update
  spring.jpa.open-in-view=false
  gameflix.jwt.secret=${JWT_SECRET}
  gameflix.jwt.ttl-seconds=900
  gameflix.cors.allowed-origin=${FRONTEND_ORIGIN:http://localhost:8081}
  ```
  (`allowPublicKeyRetrieval`/`useSSL=false` match the course setup page and are for local development only.)
- `application-local.properties` (project root, **git-ignored**, created by the human) supplies `MYSQL_USER`, `MYSQL_PASSWORD`, `JWT_SECRET`. Environment variables with the same names also work. The `spring.config.import` line is what loads it; it applies to `./mvnw spring-boot:run` **and** `./mvnw test`, because both run from the project root. If Spring Boot rejects that import for any reason, rename the file to `local.properties` in both places and update `.gitignore` and the README, and tell Debra.
- `application.properties.example` (committed): the same three keys with placeholder values (`MYSQL_USER=gameflix_auth`, `MYSQL_PASSWORD=change-me`, `JWT_SECRET=paste-output-of-openssl-rand-base64-32`), each with a comment, plus a header comment: copy to `application-local.properties`, which is git-ignored; the remaining settings live in `src/main/resources/application.properties`.
- `src/test/resources/application-test.properties`: datasource URL → `auth_test`, `ddl-auto=create-drop`. No secrets here; the test profile inherits `${MYSQL_USER}`, `${MYSQL_PASSWORD}`, and `${JWT_SECRET}` from the same local file. **Do not commit a test secret** (the assignment deducts for any committed secret).
- `.gitignore` must include `application-local.properties`, `target/`, `.idea/`, `*.iml`, `.DS_Store`.

## 6. Tests (MockMvc evidence)

`@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`, real MySQL `auth_test`, repository cleared before each test. Every request uses `.andDo(print())` so status and body appear in the log for screenshots. Name tests so the run report reads like the evidence list:

1. `register_success_returns201_withoutPassword`
2. `register_duplicateEmail_returns409`
3. `register_invalidInput_returns400` (two requests: an 11-character password, and a malformed JSON body)
4. `login_success_returns200_withSignedTokenHavingExp` (decode the payload; assert `exp` present and no `email`/`password` claim)
5. `login_wrongPassword_returns401_genericMessage`
6. `login_unknownEmail_returnsSame401Message`
7. `me_validToken_returns200_withOwnProfile`
8. `me_missingToken_returns401`
9. `me_tamperedToken_returns401` (change one character of the signature)
10. `me_expiredToken_returns401` (mint a token with the app's `JwtEncoder`, `exp` 5 minutes in the past)
11. `storedPassword_isBcryptHash_notPlaintext` (`{bcrypt}$2` prefix, not equal to the submitted password)
12. `cors_allowedOrigin_accepted_otherOrigin_rejected` (preflight `OPTIONS` from `http://localhost:8081` vs `http://evil.example`)

All must pass with `./mvnw test`.

## 7. Repository files

- All in the **project root**: `README.md` (section 8), `schema.sql` (output of `SHOW CREATE TABLE users;` from the real database), `application.properties.example`, `demo.sh`, `PROMPT_JOURNAL.md`, `SPEC.md` (this file). `schema.sql` must **not** go in `src/main/resources`, where Spring Boot treats a file of that name as a startup script; it is documentation only.
- `demo.sh`: runs against the live app with `curl -i -s`, labeled with `echo "=== 1. Successful registration ==="` etc., covering the six required cases in order: register success, duplicate email, login success, wrong password, `/me` with the token, `/me` with a tampered token. Use a throwaway email with a timestamp suffix so it can be rerun. Uses only `curl` and `sed`/`grep`/`cut` (no `jq`). After case 3, print the full token on its own line under `=== Access token (paste into jwt.io for the next assignment) ===`.
- Commit messages for AI-generated code start with `[AI-assisted]`. Commits where Debra edited AI output say so.

## 8. README contents (the assignment grades all of these)

1. **Overview**: what the service does; the three endpoints.
2. **How to run**: Java 21, MySQL 8; SQL to create the `auth` and `auth_test` databases and the `gameflix_auth` user; creating `application-local.properties` from the example; generating a secret with `openssl rand -base64 32`; `./mvnw spring-boot:run`; `./mvnw test`; `./demo.sh`.
3. **Schema**: `SHOW CREATE TABLE users;` output, plus the note that email uniqueness is a database constraint and the password column is VARCHAR(255).
4. **Password hashing**: bcrypt through `DelegatingPasswordEncoder`, strength 12, in 2–3 sentences. Why bcrypt (OWASP-listed adaptive algorithm, built into Spring Security, no extra dependency); why 12 (above OWASP's minimum of 10; each +1 doubles cost; 12 keeps one login well under a second on a laptop while multiplying an attacker's cost 4x over the default); why the delegating encoder (algorithm prefix allows later migration); 64-character cap because of bcrypt's 72-byte truncation.
5. **ddl-auto**: `update` and why; `create-drop` for the test database only.
6. **Token lifetime**: 15 minutes. Matches the sprint plan (T-1.35) and the low end of the Module 4 lecture's 15–60 minute range for high-security contexts. There are no refresh tokens or revocation list yet, so a stolen token stays valid until it expires; 15 minutes bounds that window. Refresh tokens are planned (US-001 T-1.36, T-1.41).
7. **Security decisions**: CSRF disabled and why; stateless sessions; CORS origin; generic login failure and dummy-hash timing; claims kept minimal.
8. **Test evidence**: how to run the tests and demo; list of the 12 tests mapped to the six required cases; where the screenshots are; note that `demo.sh` prints a token for the jwt.io assignment.
9. **Module 3 design-change note**: differences from the Module 3 ERD's `auth.account` table:
   - New table `users` instead of `account`; the assignment's verification query targets `users`, and `ddl-auto=update` cannot alter the existing `account` columns. `account` stays as the long-term design.
   - Added `email` (NOT NULL, unique). SRS v0.2 removed email from account creation; this assignment requires email registration, login by email, and 409 on duplicate email.
   - `password VARCHAR(255)` replaces `password_hash CHAR(60)`; CHAR(60) would truncate the 68-character `{bcrypt}` hash.
   - Omitted for now: `region_code` FK, `deletion_requested_at`, `anonymized_at`, default profile creation.
   - Duplicate email returns 409, which discloses that an email is registered. The SRS design (T-1.21) instead returns a uniform success response; the assignment contract takes priority here.
   - Paths are `/api/auth/*` and `/api/users/me` instead of the planned `/api/v1/auth/*`, `/register`, and `/login`.
10. **AI use**: placeholder section for Debra to write: tool (Claude Code; Claude Opus 5.5 and Claude Fable 5.1 for the spec and guide), where it was used, and one thing it produced that she changed or rejected, and why.

## 9. Assignment-to-spec traceability (for the reviewer)

| Assignment requirement | Where in this spec |
|---|---|
| Three endpoints with the stated codes | §2 |
| MySQL via Spring Data JPA, no H2 | §3, §5 |
| Password column sized for the hash (VARCHAR(255)) | §3 table |
| Email uniqueness by database constraint; 409 driven by it | §3 bullets |
| Credentials and secret externalized; `.gitignore`; `.example` file | §5 |
| `ddl-auto` stated and justified | §3, §8.5 |
| Schema committed | §7, §8.3 |
| OWASP adaptive hash, deliberate work factor, justified; 64-char cap for bcrypt | §4, §8.4 |
| Signed JWT with `exp`; lifetime justified | §4 JWT, §8.6 |
| Generic login failure | §2, §4 |
| CORS to one named origin | §4 CORS |
| No confidential data in the payload | §4 claims |
| Six test cases + hashed-password screenshot | §6, build guide Part 5 |
| README sections incl. design-change note and AI use | §8 |
| Spring Boot 3.5 or 4 on Java 21 | §1 |
| Course rules: `[AI-assisted]` commits, prompt journal | §7 |
