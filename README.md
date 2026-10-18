# GameFlix Auth Service

HCDD 412 · Module 4: Login & Registration Endpoints · Debra Salorino

## 1. Overview

A Spring Boot REST API that lets a GameFlix user register, log in with email and password, and get their own profile using a signed JWT (JSON Web Token: a signed string that proves who the caller is). Accounts are stored in MySQL through Spring Data JPA. Passwords are stored only as bcrypt hashes.

| Endpoint | Auth | Success | Failures |
|---|---|---|---|
| `POST /api/auth/register` | public | **201** + user JSON (no password) | **400** invalid input · **409** email (or username) already registered |
| `POST /api/auth/login` | public | **200** + `{"accessToken","tokenType":"Bearer","expiresIn":900}` | **401** `{"message":"Invalid email or password"}` for any credential failure |
| `GET /api/users/me` | `Authorization: Bearer <jwt>` | **200** + the caller's user JSON | **401** missing, invalid, tampered or expired token |

Stack: Java 21, Spring Boot 4.1.1 (Spring Security 7, Spring Data JPA, Hibernate 7), MySQL 8, Maven wrapper. The full build specification is in [SPEC.md](SPEC.md).

## 2. How to run

**Requirements:** a JDK, version 21 or newer, and MySQL 8 running on `localhost:3306`. This project was built and tested on **JDK 26** with **Java 21 as the compile target** (`<java.version>21</java.version>` in `pom.xml`), so either JDK 21 or any newer JDK works.

**1. Create the databases and the MySQL user** (as a MySQL admin user). `auth` already exists from Module 3; `IF NOT EXISTS` leaves it alone. `auth_test` is used only by the automated tests.

```sql
CREATE DATABASE IF NOT EXISTS auth;
CREATE DATABASE IF NOT EXISTS auth_test;
CREATE USER IF NOT EXISTS 'gameflix_auth'@'localhost' IDENTIFIED BY 'choose-a-password';
GRANT ALL PRIVILEGES ON auth.* TO 'gameflix_auth'@'localhost';
GRANT ALL PRIVILEGES ON auth_test.* TO 'gameflix_auth'@'localhost';
```

**2. Create your local secrets file** from the committed example. `application-local.properties` is git-ignored, so your real values are never committed.

```bash
cp application.properties.example application-local.properties
```

**3. Generate a JWT secret** and paste the output as `JWT_SECRET` in `application-local.properties`. Also set `MYSQL_USER` and `MYSQL_PASSWORD` to the user from step 1. (Environment variables with the same names also work.)

```bash
openssl rand -base64 32
```

The app refuses to start if the secret is missing, not Base64, or shorter than 32 bytes.

**4. Run the app** (listens on port 8080):

```bash
./mvnw spring-boot:run
```

**5. Run the automated tests** (uses the `auth_test` database; the app does not need to be running):

```bash
./mvnw test
```

**6. Run the live demo** (in a second terminal, while the app is running):

```bash
./demo.sh
```

Note: because `spring-boot-starter-security` is on the classpath and there is no `UserDetailsService`, Spring Boot may log `Using generated security password`. That default user is unreachable: form login and HTTP Basic are disabled, and the only way in is a JWT from `/api/auth/login`.

## 3. Schema

Output of `SHOW CREATE TABLE users;` from the real `auth` database (also committed as [schema.sql](schema.sql)):

```sql
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(254) NOT NULL,
  `password` varchar(255) NOT NULL,
  `role` enum('ADMIN','USER') NOT NULL,
  `username` varchar(30) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_username` (`username`),
  UNIQUE KEY `uk_users_email` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

- **Email uniqueness is a database constraint** (`uk_users_email`), not an application check. Registration inserts directly; if the email is taken, MySQL rejects the insert and `GlobalExceptionHandler` turns the error into **409** "Email already registered", based on the constraint name. This cannot be raced by two simultaneous requests the way an "exists?" pre-check can. Username uniqueness works the same way (`uk_users_username` → "Username already taken").
- **The password column is `VARCHAR(255)`**, so the 68-character `{bcrypt}$2a$12$…` hash is stored without truncation.

## 4. Password hashing

Passwords are hashed with **bcrypt at strength 12**, wrapped in Spring Security's `DelegatingPasswordEncoder`, so stored values look like `{bcrypt}$2a$12$…`. bcrypt is an OWASP-listed adaptive algorithm that is built into Spring Security, so it needs no extra dependency. Strength 12 is above OWASP's minimum of 10. Each +1 doubles the cost, so 12 makes an attacker's work 4x the default while one login still takes well under a second on a laptop. The delegating encoder writes the algorithm name as a prefix, which allows a later migration to another algorithm without breaking existing hashes. Passwords are capped at **64 characters** because bcrypt ignores everything after 72 bytes.

## 5. `ddl-auto`

- **Running app (`auth` database): `spring.jpa.hibernate.ddl-auto=update`.** Hibernate creates the `users` table if it is missing and keeps its data between runs, which is needed for the demo and the hashed-password evidence. It never alters or drops the Module 3 tables.
- **Tests (`auth_test` database): `create-drop`**, set only in `src/test/resources/application-test.properties`. Each test run starts with a freshly created table and drops it at the end, so tests never touch real data.

## 6. Token lifetime

Tokens expire after **15 minutes** (`gameflix.jwt.ttl-seconds=900`; `exp` = `iat` + 900). This matches the sprint plan (T-1.35) and the low end of the Module 4 lecture's 15–60 minute range for high-security contexts. There are no refresh tokens or revocation list yet, so a stolen token stays valid until it expires; 15 minutes bounds that window. Refresh tokens are planned (US-001 T-1.36, T-1.41).

## 7. Security decisions

- **CSRF disabled.** CSRF (Cross-Site Request Forgery) works by making a browser send a cookie it holds automatically. This API is stateless and the JWT travels in the `Authorization` header, never in a cookie, so there is no ambient credential for a forged request to use.
- **Stateless sessions.** `SessionCreationPolicy.STATELESS`: no HTTP session is ever created; every request must carry its own token.
- **CORS to one origin.** Only `gameflix.cors.allowed-origin` (default `http://localhost:8081`, the planned frontend) is allowed, with methods GET/POST/OPTIONS, headers `Authorization` and `Content-Type`, and no credentials. Never `*`.
- **Generic login failure and dummy-hash timing.** An unknown email and a wrong password both return the same 401 `{"message":"Invalid email or password"}`. Login always runs exactly one bcrypt check; for an unknown email it checks against a dummy hash created at startup, so response time does not reveal which emails are registered.
- **Minimal claims.** The token is HS256-signed and contains only `iss`, `sub` (user id), `role`, `iat` and `exp`. The payload is Base64URL-encoded, not encrypted, so no email, username or password is put in it. The JWT secret is at least 32 bytes, has no default anywhere in committed files, and the decoder also checks `exp`, `nbf` and `iss`.
- **No sensitive data in logs or responses.** Passwords, hashes, tokens and request bodies are never logged; the Hibernate logger that would print a duplicate email is set to ERROR; error responses never include stack traces or exception messages; the `User` entity is never returned from a controller.

## 8. Test evidence

Run the tests with `./mvnw test` (requires MySQL and `application-local.properties`; see section 2). All 12 pass. The live demo is `./demo.sh` against the running app.

The 12 tests in `src/test/java/com/gameflix/auth/AuthApiTests.java`, mapped to the six required cases:

| Required case | Tests |
|---|---|
| 1. Successful registration (201, no password) | `register_success_returns201_withoutPassword` |
| 2. Duplicate email (409) | `register_duplicateEmail_returns409` |
| 3. Successful login (200, signed token with `exp`) | `login_success_returns200_withSignedTokenHavingExp` |
| 4. Wrong password (401, generic message) | `login_wrongPassword_returns401_genericMessage`, `login_unknownEmail_returnsSame401Message` |
| 5. `/api/users/me` with a valid token (200) | `me_validToken_returns200_withOwnProfile` |
| 6. `/api/users/me` with a tampered token (401) | `me_tamperedToken_returns401`, plus `me_missingToken_returns401` and `me_expiredToken_returns401` |
| Additional checks | `register_invalidInput_returns400` (11-character password and malformed JSON), `storedPassword_isBcryptHash_notPlaintext` (`{bcrypt}$2` prefix), `cors_allowedOrigin_accepted_otherOrigin_rejected` (preflight from `http://localhost:8081` vs `http://evil.example`) |

`demo.sh` covers the same six cases with `curl -i -s` against the live app. A sample run produced:

| Case | Status | Body |
|---|---|---|
| 1. Successful registration | 201 | `{"id":1,"username":"demo_1792357831","email":"demo_1792357831@example.com","role":"USER","createdAt":"2026-10-18T21:10:31.487786Z"}` |
| 2. Duplicate email | 409 | `{"message":"Email already registered"}` |
| 3. Successful login | 200 | `{"accessToken":"eyJhbGciOiJIUzI1NiJ9…","tokenType":"Bearer","expiresIn":900}` |
| 4. Wrong password | 401 | `{"message":"Invalid email or password"}` |
| 5. `/me` with the token | 200 | same user JSON as case 1 |
| 6. `/me` with a tampered token | 401 | empty body; `WWW-Authenticate: Bearer error="invalid_token" … Invalid signature` |

After case 3, `demo.sh` prints the full token on its own line under `=== Access token (paste into jwt.io for the next assignment) ===`, for use in the next assignment.

**Screenshots:** in the Word document submitted with this assignment in Canvas (test run, demo.sh output, and the `SELECT id, email, password FROM users;` result).

## 9. Module 3 design-change note

Differences from the Module 3 ERD's `auth.account` table:

- **New table `users` instead of `account`.** The assignment's verification query targets `users`, and `ddl-auto=update` cannot alter the existing `account` columns. `account` stays as the long-term design.
- **Added `email` (NOT NULL, unique).** SRS v0.2 removed email from account creation; this assignment requires email registration, login by email, and 409 on duplicate email.
- **`password VARCHAR(255)` replaces `password_hash CHAR(60)`.** CHAR(60) would truncate the 68-character `{bcrypt}` hash.
- **Omitted for now:** `region_code` FK, `deletion_requested_at`, `anonymized_at`, default profile creation.
- **Duplicate email returns 409**, which discloses that an email is registered. The SRS design (T-1.21) instead returns a uniform success response; the assignment contract takes priority here.
- **Paths are `/api/auth/*` and `/api/users/me`** instead of the planned `/api/v1/auth/*`, `/register`, and `/login`.

## 10. AI use

I used Claude Opus 5.5 and Claude Fable 5.1 in the claude.ai app to iterate on the assignment and design. The sprint plan was converted into a SPEC.md file and audited multiple times. Most changes were made to encourage simplicity in the code and accommodate my need to understand and explain the implementation details. To accomplish this, it was decided that every class and every method will have a Javadoc block explaining what it does and which requirements it satisfied. Each step inside a method has an included "why" comment above it. Unfamiliar annotations are explained and every line of the security config has its own comment. Security decisions are to be tagged // SECURITY: so that they can be easily located. Each test has an included explanation indicating which required case it proves and what response it expects. 

A final audit pass on the SPEC.md file using the Fable model identified several elements that might have broken the build or tests. Corrections were made to avoid the identified issues: 
1. The spec originally said save the user, catch the database's duplicate error inside the service and turn it into a 409. If a method is marked @Transactional and a database error happens inside it, Spring marks the whole transaction as "must roll back" even if the code catches the error. When the method ends, Spring tries to commit, finds the rollback flag, and throws UnexpectedRollbackException, resulting in a 500 instead of a 409. The correction was to leave @Transactional off the AuthService entirely. Spring Data's saveAndFlush already runs inside its own transaction, which rolls back cleanly on failure. The exception then travels up to GlobalExceptionHandler, which turns it into the 409. The code is simpler and still works. 
2. The spec named NimbusJwtEncoder but didn't say how to build it. The class takes "key source" object instead of a raw key, and there's a specific wrapper for a single HMAC secret. Claude Code was initially left to guess, which would have failed at runtime instead of runtime. A modification was made to spell out the exact construction and Claude Code later confirmed it against the actual library before writing the code. 
3. The spec had gamefclix.jwt.ttl=15m. Spring can parse "15m" into a Duration, but only through one of its configuration mechanisms; reading it with the simpler @Value annotation risks failure. Rather than add the heavier mechanism, I changed the property to ....ttl-seconds=900. 
4. The assignment directs me to commit the schema as documentation but SpringBoot treats a file name schema.sql inside /resources as a script to run at startup. If Claude Code put it there, the app might have tried to create a table that Hibernate was also creating. The fix was to require the file in the project rot, outside Spring's reach. 
5. The spec listed @Email as the email rule. @Email only checks format; it treats an empty string as valid, because there's no format to fail. "Email": "" would have passed validation and hit the database as a blank email. @NotBlank was added as a requirement to prevent that from happening. 
6. Tests needed a database password and a JWT secret. Per the assignment, committing and secret loses externalization points. The correction made was to have the test profile inherit the three values from the same git-ignored local file the app uses, and the spec was modified to explicitly direct Claude Code to never commit one to the repo. 
7. I had written VARCHAR(20) for the role column. Hibernate maps a string-stored enum to a native MySQL column instead. The schema committed shows that. Had the spec insisted on VARCHAR, Claude Code would have added extra annotations to fight the default, and the schema in the README would have had to match. 

Last minute additions made to the SPEC.md:
1. A note requirement if Spring Security generates a security password. 
2. The token printed by demo.sh because the next assignment needs a token and I wanted to save that step later. 
3. A traceability table to map each assignment requirement to a spec section so that I (and you) can confirm all requirements have been covered in execution. 

After generation, I walked through the code class by class with Claude Code. When someone registers, someone logs in, or someone calls GET /api/users/me with a token. I documented the file name and method for each step. Potential changes were also documented for review and consideration. I also worked through the documentation to make sure it was all understandable. These comments were improved for readability with assistance from Claude Code (and noted as AI-assisted. Changes were then pushed. 

