package com.gameflix.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * All security settings in one place: which endpoints are public, how tokens are checked,
 * how passwords are hashed, how tokens are signed, and which browser origin may call the API.
 *
 * <p>Where it sits: Spring Security's filters run <b>before</b> any controller. For
 * {@code /api/users/me} they read the {@code Authorization: Bearer <token>} header, check the
 * token with the {@link JwtDecoder} below, and answer 401 themselves if it is missing or invalid.
 *
 * <p>Satisfies SPEC.md section 4 ("Password hashing", "JWT", "SecurityFilterChain", "CORS") and the
 * assignment requirements "OWASP adaptive hash with a deliberate work factor", "signed JWT with
 * exp" and "CORS to one named origin".
 *
 * <p>Note on the startup log line "Using generated security password": Spring Boot prints it
 * because spring-boot-starter-security is present and this app defines no UserDetailsService.
 * That default in-memory user can never be used, because formLogin and httpBasic are both
 * disabled below; the only way in is a JWT from {@code /api/auth/login}.
 */
// @Configuration: this class declares beans (shared objects Spring creates and injects).
@Configuration
public class SecurityConfig {

    // The value every token's "iss" (issuer) claim must have. Used when signing and when checking.
    private static final String TOKEN_ISSUER = "gameflix-auth";

    /**
     * Defines the security rules for every HTTP request.
     *
     * @param http Spring Security's builder for the filter chain
     * @return the finished filter chain
     * @throws Exception if Spring Security cannot build the chain
     */
    // @Bean: the object this method returns is created once and managed by Spring.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // SECURITY: CSRF (Cross-Site Request Forgery) protection is off. CSRF tricks a
                // browser into sending a cookie it holds automatically. This API is stateless and
                // the JWT travels in the Authorization header, never in a cookie, so a forged
                // request has no credential to ride on.
                .csrf(csrf -> csrf.disable())
                // SECURITY: apply the CORS rules from the corsConfigurationSource bean below.
                .cors(Customizer.withDefaults())
                // SECURITY: never create an HTTP session; every request must bring its own token.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Decide who may call which endpoint.
                .authorizeHttpRequests(auth -> auth
                        // SECURITY: registering and logging in must work without a token.
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        // SECURITY: everything else, including /api/users/me, needs a valid token.
                        .anyRequest().authenticated())
                // SECURITY: read "Authorization: Bearer <jwt>" and check it with the JwtDecoder bean.
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                // SECURITY: no HTML login page; this is a JSON API.
                .formLogin(form -> form.disable())
                // SECURITY: no username:password Basic header; the only credential is a JWT.
                .httpBasic(basic -> basic.disable());

        return http.build();
    }

    /**
     * The password hasher: bcrypt with strength (work factor) 12.
     *
     * @return an encoder whose output looks like {@code {bcrypt}$2a$12$...} (68 characters)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        // SECURITY: bcrypt is an OWASP-listed adaptive hash. Strength 12 means 2^12 rounds: each
        // +1 doubles the cost, so 12 is 4x harder to brute-force than the default of 10 while one
        // login still takes well under a second. The delegating encoder adds the "{bcrypt}"
        // prefix to each stored hash so a different algorithm can be introduced later.
        return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(12)));
    }

    /**
     * Builds the secret key that signs and checks tokens, from {@code gameflix.jwt.secret}.
     *
     * @param base64Secret the Base64 secret from application-local.properties or the JWT_SECRET
     *                     environment variable (there is no default value anywhere)
     * @return an HMAC-SHA256 key
     * @throws IllegalStateException at startup if the secret is blank, not Base64, or shorter
     *         than 32 bytes, so the app never runs with a weak key
     */
    @Bean
    public SecretKey jwtSecretKey(@Value("${gameflix.jwt.secret}") String base64Secret) {
        // SECURITY: refuse to start without a secret.
        if (base64Secret == null || base64Secret.isBlank()) {
            throw new IllegalStateException(
                    "gameflix.jwt.secret is missing. Set JWT_SECRET in application-local.properties "
                            + "(generate one with: openssl rand -base64 32).");
        }

        // Turn the Base64 text back into raw bytes. Report bad Base64 clearly instead of a vague error.
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Secret.trim());
        } catch (IllegalArgumentException notBase64) {
            throw new IllegalStateException(
                    "gameflix.jwt.secret is not valid Base64. Generate one with: openssl rand -base64 32");
        }

        // SECURITY: HS256 needs a key of at least 256 bits (32 bytes); a shorter key is guessable.
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "gameflix.jwt.secret must decode to at least 32 bytes but decodes to "
                            + keyBytes.length + ". Generate one with: openssl rand -base64 32");
        }

        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    /**
     * The token signer used by {@link TokenService}.
     *
     * @param jwtSecretKey the HMAC key from {@link #jwtSecretKey}
     * @return a Nimbus-based encoder that signs with that key
     */
    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        // ImmutableSecret hands the one secret key to Nimbus, the JWT library built into Spring Security.
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    /**
     * The token checker Spring Security uses on every protected request.
     *
     * @param jwtSecretKey the same HMAC key that signs tokens
     * @return a decoder that rejects tokens with a bad signature, wrong issuer, or that are expired
     *         or not yet valid (Spring Security then answers 401)
     */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        // SECURITY: accept only HS256 signatures made with our key, so a token signed with a
        // different algorithm or key is rejected.
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        // SECURITY: after the signature passes, also check exp (expired?), nbf (not yet valid?)
        // and iss (issued by us?).
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(TOKEN_ISSUER));

        return decoder;
    }

    /**
     * CORS (Cross-Origin Resource Sharing) rules: which other website may call this API from a browser.
     *
     * @param allowedOrigin the one allowed origin, from {@code gameflix.cors.allowed-origin}
     *                      (default http://localhost:8081, the planned frontend)
     * @return the CORS rules, applied to every path
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${gameflix.cors.allowed-origin}") String allowedOrigin) {
        CorsConfiguration config = new CorsConfiguration();

        // SECURITY: exactly one named origin, never "*", so other websites' scripts can't call us.
        config.setAllowedOrigins(List.of(allowedOrigin));
        // Only the methods this API uses. OPTIONS is the browser's "preflight" check before the real request.
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        // Only the request headers the frontend needs: the token and the JSON content type.
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // SECURITY: no cookies are sent cross-origin; the token goes in a header instead.
        config.setAllowCredentials(false);

        // Apply these rules to every URL path.
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
