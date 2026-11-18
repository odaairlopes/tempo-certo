package com.tempocerto.api.controller;

import com.tempocerto.api.model.Profissional;
import com.tempocerto.api.repository.ProfissionalRepository;
import com.tempocerto.api.service.AcessoAgenda;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/profissionais")
public class ProfissionalController {
    private final ProfissionalRepository repository;
    private final String adminKey;
    private final MongoOperations mongo;

    public ProfissionalController(ProfissionalRepository repository, MongoOperations mongo,
                                  @Value("${tempocerto.admin-key:}") String adminKey) {
        this.repository = repository;
        this.mongo = mongo;
        this.adminKey = adminKey;
    }

    @PostMapping
    public ResponseEntity<Credenciais> salvar(@Valid @RequestBody NovoProfissional pedido,
            @RequestHeader(value = "X-Admin-Key", required = false) String chave) {
        AcessoAgenda.verificar(adminKey, chave);
        var profissional = new Profissional();
        profissional.setNome(pedido.nome().strip());
        profissional.setEmail(pedido.email());
        profissional.setFotoUrl(pedido.fotoUrl());
        profissional.setCategoria(pedido.categoria() == null ? null : pedido.categoria().strip());
        String novaChave = AcessoAgenda.gerarChave();
        profissional.setChaveAgendaHash(AcessoAgenda.hash(novaChave));
        var salvo = repository.insert(profissional);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
            .body(new Credenciais(salvo.getId(), salvo.getNome(), novaChave));
    }

    @PutMapping("/{id}/chave")
    public ResponseEntity<Credenciais> renovarChave(@PathVariable String id,
            @RequestHeader(value = "X-Admin-Key", required = false) String chave) {
        AcessoAgenda.verificar(adminKey, chave);
        var profissional = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profissional não encontrado."));
        String novaChave = AcessoAgenda.gerarChave();
        mongo.updateFirst(Query.query(Criteria.where("id").is(id)),
            new Update().set("chaveAgendaHash", AcessoAgenda.hash(novaChave)), Profissional.class);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new Credenciais(profissional.getId(), profissional.getNome(), novaChave));
    }

    @GetMapping
    public List<PerfilPublico> listar() {
        return repository.findAll().stream().map(p -> new PerfilPublico(p.getId(), p.getNome(),
            p.getCategoria() == null || p.getCategoria().isBlank() ? "Outros" : p.getCategoria(), p.getFotoUrl())).toList();
    }

    @PutMapping("/{id}/categoria")
    public PerfilPublico categorizar(@PathVariable String id, @Valid @RequestBody Categoria pedido,
            @RequestHeader(value = "X-Agenda-Key", required = false) String chave) {
        var perfil = repository.findById(id).orElse(null);
        var acesso = AcessoAgenda.autorizar(perfil, chave);
        String categoria = pedido.categoria().strip();
        var update = new Update().set("categoria", categoria);
        if (pedido.fotoUrl() != null) update.set("fotoUrl", pedido.fotoUrl());
        var alteracao = mongo.updateFirst(Query.query(Criteria.where("id").is(id)).addCriteria(acesso),
            update, Profissional.class);
        if (alteracao.getMatchedCount() != 1) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Credencial de acesso inválida.");
        }
        return new PerfilPublico(id, perfil.getNome(), categoria, pedido.fotoUrl() == null ? perfil.getFotoUrl() : pedido.fotoUrl());
    }

    public record Categoria(@NotBlank @Size(max = 60) String categoria, @Size(max=2048) @jakarta.validation.constraints.Pattern(regexp="(?:https://[^\\s]+)?") String fotoUrl) {}

    public record NovoProfissional(@NotBlank @Size(max = 120) String nome,
                                   @NotBlank @Email @Size(max = 254) String email, @Size(max = 60) String categoria, @Size(max=2048) @jakarta.validation.constraints.Pattern(regexp="(?:https://[^\\s]+)?") String fotoUrl) {}

    public record PerfilPublico(String id, String nome, String categoria, String fotoUrl) {}
    public record Credenciais(String id, String nome, String chaveAgenda) {}
}
