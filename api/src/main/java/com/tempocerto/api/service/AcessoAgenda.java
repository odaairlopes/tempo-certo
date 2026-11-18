package com.tempocerto.api.service;

import com.tempocerto.api.model.Profissional;
import java.time.Instant;
import org.springframework.data.mongodb.core.query.Criteria;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class AcessoAgenda {
    private static final SecureRandom RANDOM = new SecureRandom();

    private AcessoAgenda() {}

    public static String gerarChave() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String chave) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(chave.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static void verificar(String esperado, String recebido) {
        if (esperado == null || esperado.isBlank() || recebido == null
            || !MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8),
                recebido.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Credencial de acesso inválida.");
        }
    }

    public static Criteria autorizar(Profissional perfil, String chave) {
        String recebido = chave == null ? null : hash(chave);
        boolean sessao = perfil != null && perfil.getSessaoAte() != null
            && perfil.getSessaoAte().isAfter(Instant.now()) && recebido != null
            && recebido.equals(perfil.getSessaoHash());
        if (sessao) {
            return Criteria.where("sessaoHash").is(recebido).and("sessaoAte").gt(Instant.now());
        }
        verificar(perfil == null ? null : perfil.getChaveAgendaHash(), recebido);
        return Criteria.where("chaveAgendaHash").is(recebido);
    }
}
