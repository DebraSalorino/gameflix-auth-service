package com.gameflix.auth;

import com.gameflix.auth.dto.TokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Creates the signed login token (JWT: JSON Web Token, a signed string that proves who the caller is).
 *
 * <p>Where it sits: controller → {@link AuthService} → <b>TokenService</b>. It is called only after
 * the password has been checked. Checking tokens on later requests is done by Spring Security with
 * the {@code JwtDecoder} bean in {@link SecurityConfig}, not here.
 *
 * <p>Satisfies SPEC.md section 4 ("JWT": HS256, claims iss/sub/role/iat/exp only, 900-second
 * lifetime) and the assignment requirement "signed JWT with exp".
 */
// @Service: tells Spring to create one shared instance of this class and inject it where needed.
@Service
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final long ttlSeconds;

    /**
     * @param jwtEncoder the HS256 encoder built in {@link SecurityConfig}
     * @param ttlSeconds token lifetime in seconds, from {@code gameflix.jwt.ttl-seconds} (900)
     */
    // @Value: copies the value of a property from application.properties into this parameter.
    public TokenService(JwtEncoder jwtEncoder, @Value("${gameflix.jwt.ttl-seconds}") long ttlSeconds) {
        this.jwtEncoder = jwtEncoder;
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * Builds and signs a token for a user who has just logged in.
     *
     * @param user the authenticated user
     * @return the token, the type "Bearer" and the lifetime in seconds
     */
    public TokenResponse createToken(User user) {
        // Use one "now" for both times, so exp is exactly iat + ttlSeconds.
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(ttlSeconds);

        // SECURITY: only these five claims. The payload is Base64URL-encoded, not encrypted, so
        // anyone holding the token can read it: no email, username or password goes in here.
        // builder() starts an empty claim set, each line below adds one claim, and build()
        // finishes it. (Spring Security's API requires this "builder" style for claims.)
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("gameflix-auth")
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();

        // Name HS256 explicitly. Without this header Nimbus would assume RS256 (a public/private
        // key algorithm) and fail, because we only have a shared secret key.
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        // Sign the header + claims with the secret key and get the compact "xxx.yyy.zzz" string.
        String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return new TokenResponse(tokenValue, "Bearer", ttlSeconds);
    }
}
