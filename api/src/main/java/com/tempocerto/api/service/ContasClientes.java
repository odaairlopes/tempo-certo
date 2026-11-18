package com.tempocerto.api.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ContasClientes {
    @Document("clientes")
    public record Conta(@Id String id, String nome, String email, String senhaHash) {}
    @Document("sessoes_clientes")
    public record Acesso(@Id String id, String clienteId, @Indexed(expireAfter = "0s") Instant expira) {}
    public record Sessao(String nome, String token) {}

    private final MongoOperations mongo;
    private final Clock clock;
    private final Pbkdf2PasswordEncoder encoder = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final String hashAusente = encoder.encode(AcessoAgenda.gerarChave());

    public ContasClientes(MongoOperations mongo, Clock clock) {
        this.mongo = mongo;
        this.clock = clock;
    }

    public Sessao cadastrar(String nome, String email, String senha) {
        String normalizado = email.strip().toLowerCase(Locale.ROOT);
        var conta = new Conta("email:" + normalizado, nome.strip(), normalizado, encoder.encode(senha));
        try {
            mongo.insert(conta);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Não foi possível cadastrar este e-mail.");
        }
        return abrir(conta);
    }

    public Sessao entrar(String email, String senha) {
        var conta = mongo.findById("email:" + email.strip().toLowerCase(Locale.ROOT), Conta.class);
        if (!encoder.matches(senha, conta == null ? hashAusente : conta.senhaHash()) || conta == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos.");
        return abrir(conta);
    }

    public Sessao entrarGoogle(Jwt token) {
        String id = "google:" + token.getSubject();
        var conta = mongo.findById(id, Conta.class);
        if (conta == null) {
            String email = token.getClaimAsString("email");
            String nome = token.getClaimAsString("name");
            conta = new Conta(id, nome == null || nome.isBlank() ? email : nome, email, null);
            try {
                mongo.insert(conta);
            } catch (DuplicateKeyException e) {
                conta = mongo.findById(id, Conta.class);
            }
        }
        return abrir(conta);
    }

    private Sessao abrir(Conta conta) {
        String token = AcessoAgenda.gerarChave();
        mongo.insert(new Acesso(AcessoAgenda.hash(token), conta.id(), clock.instant().plus(Duration.ofHours(8))));
        return new Sessao(conta.nome(), token);
    }

    public Conta exigir(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ") && authorization.length() <= 160) {
            var acesso = mongo.findById(AcessoAgenda.hash(authorization.substring(7)), Acesso.class);
            if (acesso != null && acesso.expira().isAfter(clock.instant())) {
                var conta = mongo.findById(acesso.clienteId(), Conta.class);
                if (conta != null) return conta;
            }
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Entre na sua conta para agendar.");
    }
}
