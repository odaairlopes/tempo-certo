package com.tempocerto.api.controller;

import com.tempocerto.api.model.Profissional;
import com.tempocerto.api.repository.ProfissionalRepository;
import com.tempocerto.api.service.AcessoAgenda;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@RestController
public class GoogleController {
    private final com.tempocerto.api.service.IdentidadeGoogle google;
    private final ProfissionalRepository profissionais;
    private final MongoOperations mongo;

    public GoogleController(com.tempocerto.api.service.IdentidadeGoogle google, ProfissionalRepository profissionais, MongoOperations mongo) {
        this.google = google;
        this.profissionais = profissionais;
        this.mongo = mongo;
    }

    @PostMapping("/auth/google")
    public ResponseEntity<Sessao> entrar(@Valid @RequestBody Login login) {
        Jwt token = google.verificar(login.credential());
        String email = token.getClaimAsString("email");
        String sub = token.getSubject();
        var perfis = profissionais.findTop2ByEmailIgnoreCase(email);
        if (perfis.size() != 1) throw negado();
        var perfil = perfis.getFirst();
        String vinculo = perfil.getGoogleSub();
        if (vinculo == null) {
            String dominio = token.getClaimAsString("hd");
            if (!email.toLowerCase(Locale.ROOT).endsWith("@gmail.com") && (dominio == null || dominio.isBlank())) throw negado();
        } else if (!vinculo.equals(sub)) throw negado();

        String chave = AcessoAgenda.gerarChave();
        var resultado = mongo.updateFirst(Query.query(where("id").is(perfil.getId()).and("googleSub").is(vinculo)
            .and("email").is(perfil.getEmail())), new Update().set("googleSub", sub)
            .set("sessaoHash", AcessoAgenda.hash(chave)).set("sessaoAte", token.getExpiresAt()), Profissional.class);
        if (resultado.getMatchedCount() != 1) throw negado();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Sessao(perfil.getId(), chave));
    }

    private static ResponseStatusException negado() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "A conta Google não tem acesso a esta agenda.");
    }

    public record Login(@NotBlank @Size(max = 8192) String credential) {}
    public record Sessao(String id, String chaveAgenda) {}
}
