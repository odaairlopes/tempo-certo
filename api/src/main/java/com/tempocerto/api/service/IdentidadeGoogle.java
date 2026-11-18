package com.tempocerto.api.service;
import java.time.Instant;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IdentidadeGoogle {
    private final JwtDecoder decoder;
    private final String clientId;

    @org.springframework.beans.factory.annotation.Autowired
    public IdentidadeGoogle(@Value("${GOOGLE_CLIENT_ID:}") String clientId) {
        this(clientId, NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build());
    }

    public IdentidadeGoogle(String clientId, JwtDecoder decoder) {
        this.clientId = clientId;
        this.decoder = decoder;
    }

    public Jwt verificar(String credential) {
        if (clientId.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Login Google desativado.");
        Jwt token;
        try {
            token = decoder.decode(credential);
        } catch (JwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credencial Google inválida.");
        }
        String email = token.getClaimAsString("email");
        String sub = token.getSubject();
        Instant agora = Instant.now();
        if (!Set.of("https://accounts.google.com", "accounts.google.com").contains(
                String.valueOf(token.getClaimAsString("iss"))) || token.getAudience() == null
                || !token.getAudience().contains(clientId) || token.getExpiresAt() == null
                || !token.getExpiresAt().isAfter(agora) || sub == null || sub.isBlank() || email == null
                || !Boolean.TRUE.equals(token.getClaimAsBoolean("email_verified"))) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credencial Google inválida.");
        return token;
    }
}
