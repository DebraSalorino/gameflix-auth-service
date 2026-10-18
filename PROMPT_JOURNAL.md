# Prompt Journal

HCDD 412 · Module 4 · GameFlix Auth Service · Debra Salorino

One entry per prompt given to the AI tool while building this project. All entries are from one Claude Code session on 2026-10-18.

---

## Entry 1: Scaffold the project

**Date:** 2026-10-18
**Tool:** Claude Code

**Prompt:**

> Read SPEC.md in full; it is the specification for everything in this project. Then scaffold the project in this folder:
> 1. Download a project from start.spring.io with curl: maven-project, java, javaVersion 21, the latest stable Spring Boot 4.x (no SNAPSHOT, M, or RC), groupId com.gameflix, artifactId gameflix-auth-service, packageName com.gameflix.auth, dependencies web,security,oauth2-resource-server,data-jpa,mysql,validation. Unzip it into this folder without overwriting SPEC.md.
> 2. Create .gitignore, application.properties, application.properties.example, and src/test/resources/application-test.properties exactly as SPEC.md sections 1 and 5 describe.
> 3. Run git init. Do not commit and do not write any Java code yet.
> Report the Spring Boot version you got and every dependency in pom.xml.

**What it produced:**
- Queried start.spring.io for available versions and chose **Spring Boot 4.1.1** (the newest 4.x without SNAPSHOT/M/RC), downloaded the starter with curl and unzipped it with `unzip -n` (no overwrite). Added: `pom.xml`, Maven wrapper (`mvnw`, `mvnw.cmd`, `.mvn/`), `.gitattributes`, `HELP.md`, the main application class and Initializr's default test.
- `.gitignore` with the five SPEC-required entries at the top plus Initializr's entries.
- `src/main/resources/application.properties` with the ten SPEC section 5 settings, each commented.
- `application.properties.example` with the three placeholder keys and a header comment.
- `src/test/resources/application-test.properties` (`auth_test` URL, `create-drop`, no secrets).
- Ran `git init`; reported the 11 dependencies in `pom.xml` (5 compile, MySQL driver at runtime, 5 test starters).

What Debra changed and why: Read the report, checked it against SPEC.md and accepted it. 

---

## Entry 2: Implement SPEC sections 2–5

**Date:** 2026-10-18
**Tool:** Claude Code

**Prompt:**

> Implement SPEC.md sections 2 through 5, following the code style in section 1a: the simplest code that meets the spec, commented extensively for a learner. Build the User entity and Role enum, UserRepository, the request and response records, the services, the JWT and security configuration, both controllers, and the exception handler. Follow the spec exactly. If anything in it is ambiguous or does not compile on Spring Boot 4, stop and ask me instead of guessing. Never print or log the contents of application-local.properties. When done, run ./mvnw compile and fix any errors. Note: this machine runs JDK 26 while pom.xml targets Java 21; keep the target at 21. If a build or runtime error is specifically about the JDK version (for example a ByteBuddy or "unsupported class file major version" message), tell me the exact error and the smallest fix before applying it. Then list each file you created with one sentence on what it does.

**What it produced:**
- Checked the Spring Security 7.1.1 jars to confirm the JWT classes named in the spec exist before using them.
- 16 source files: `Role`, `User`, `UserRepository`, `AuthService`, `TokenService`, `SecurityConfig`, `AuthController`, `UserController`, `InvalidCredentialsException`, `GlobalExceptionHandler`, and records `dto/RegisterRequest`, `dto/LoginRequest`, `dto/UserResponse`, `dto/TokenResponse`, `dto/ErrorResponse`, `dto/ValidationErrorResponse`.
- `./mvnw clean compile`: BUILD SUCCESS, compiled with `release 21` (class file major version 65), no JDK-version errors.
- Reported its choices where the spec was silent: two error-body records, a separate `SecretKey` bean, `findUserById` in `AuthService`, `role` as a String in `UserResponse`.
- Flagged two follow-ups: confirm the malformed-JSON exception type in test 3, and the `createdAt` precision difference between register and `/me`.

What Debra changed and why: Read the report, checked it against SPEC.md and accepted it. 

---

## Entry 3: Three fixes, then the 12 tests

**Date:** 2026-10-18
**Tool:** Claude Code

**Prompt:**

> Three fixes first:
> 1. In User's @PrePersist, set createdAt to Instant.now().truncatedTo(ChronoUnit.MICROS) so the register response and /me return the same value, and add a comment explaining that MySQL DATETIME(6) only stores microseconds.
> 2. Delete the Initializr default test GameflixAuthServiceApplicationTests; the tests you write next cover startup.
> 3. Keep the HttpMessageNotReadableException handler but confirm its type with the malformed-JSON case in test 3.
> Then write the 12 tests in SPEC.md section 6, commented as section 1a requires, and run ./mvnw test. If a test fails, fix the application code, not the test's expectation, unless the test contradicts SPEC.md; tell me which you changed and why. Finish with the pass/fail summary.

**What it produced:**
- `User.onCreate()` now truncates `createdAt` to microseconds, with the DATETIME(6) comment.
- Deleted `GameflixAuthServiceApplicationTests.java`.
- `src/test/java/com/gameflix/auth/AuthApiTests.java` with the 12 SPEC section 6 tests, each commented with the case it proves, the request and the expected status/body. Test 3 asserts that the malformed-JSON exception is `HttpMessageNotReadableException` (confirmed). Test 7 asserts `/me` returns the same `createdAt` as register.
- `./mvnw test`: **12 run, 0 failures, 0 errors**, so no application code or test expectations were changed.
- Reported JDK 26 warnings (ByteBuddy/Mockito dynamic agent loading; not errors) and that Hibernate logged the duplicate email at WARN, with a suggested one-line fix that it did not apply without approval.

What Debra changed and why: Read the report, checked it against SPEC.md and accepted it. 

---

## Entry 4: Hide the duplicate-entry email from the log

**Date:** 2026-10-18
**Tool:** Claude Code

**Prompt:**

> add the logging line to hide the duplicate-entry email

**What it produced:**
- Added `logging.level.org.hibernate.orm.jdbc.error=ERROR` to `src/main/resources/application.properties`, with a `SECURITY:` comment explaining that Hibernate's WARN message contains the submitted email.
- Reran `./mvnw test`: 12/12 pass, and the log no longer contains any "Duplicate entry" line. Noted the trade-off: other WARN-level SQL messages from that logger are hidden too.

What Debra changed and why: Claude Code flagged the email in the log and explained the tradeoff. I made the decision to hide the duplicate entry from the log. 

---

## Entry 5: Run the app, demo.sh and schema.sql

**Date:** 2026-10-18
**Tool:** Claude Code

**Prompt:**

> Start the app with ./mvnw spring-boot:run in the background and wait until it is listening on 8080. Write demo.sh as SPEC.md section 7 describes, make it executable, run it, and show me its full output. Then run
> /usr/local/mysql/bin/mysql --login-path=local auth -e "SHOW CREATE TABLE users\G"
> and save the CREATE TABLE statement as schema.sql in the project root. Confirm the password column is varchar(255) and the uk_users_email unique key exists. Then stop the app.

**What it produced:**
- Started the app in the background and waited until it answered on port 8080.
- `demo.sh` (executable, curl/grep/cut only, timestamped throwaway account) covering the six required cases, printing the token after case 3. Run results: 201, 409, 200, 401, 200, 401.
- `schema.sql` with the `CREATE TABLE users` statement from the real `auth` database; confirmed `` `password` varchar(255) NOT NULL `` and `` UNIQUE KEY `uk_users_email` (`email`) ``.
- Stopped the app (port 8080 free). Noted that the "Using generated security password" line did not appear in this run's log.

What Debra changed and why: Read the report, checked it against SPEC.md and accepted it.

---

## Entry 6: README and prompt journal

**Date:** 2026-10-18
**Tool:** Claude Code

**Prompt:**

> Write README.md exactly as SPEC.md section 8 describes, using schema.sql, the real test names from AuthApiTests, and the demo.sh output you just saw. Add to the "How to run" section that the project was built and tested on JDK 26 with Java 21 as the compile target (pom.xml java.version 21), so either JDK 21 or newer works. Leave the AI-use section as a clearly marked TODO for me to write. Then create PROMPT_JOURNAL.md with one entry per prompt I gave you in this session: the prompt text, the tool (Claude Code), what it produced, and a "What Debra changed and why" line marked TODO. Include my request to hide the duplicate-entry email from the log as its own entry. Finish by listing every file in the project root and in src/main and src/test, so I can see the whole project at a glance.

**What it produced:**
- `README.md` with the ten SPEC section 8 sections, the real `SHOW CREATE TABLE` output, the 12 real test names mapped to the six required cases, a summary of the `demo.sh` run, and the JDK 26 / Java 21 target note. The AI-use section and the screenshot location are marked TODO.
- This file, `PROMPT_JOURNAL.md`.

What Debra changed and why: Read the report, checked it against SPEC.md and accepted it.
