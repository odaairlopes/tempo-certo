package com.tempocerto.api.controller;

import com.tempocerto.api.model.Profissional;
import com.tempocerto.api.repository.AgendamentoRepository;
import com.tempocerto.api.repository.ProfissionalRepository;
import com.tempocerto.api.service.*;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@RestController
public class PerfilController {
    private final ContasClientes contas;
    private final MongoOperations mongo;
    private final ProfissionalRepository profissionais;
    private final AgendamentoRepository reservas;

    public PerfilController(ContasClientes contas, MongoOperations mongo, ProfissionalRepository profissionais, AgendamentoRepository reservas) {
        this.contas = contas;
        this.mongo = mongo;
        this.profissionais = profissionais;
        this.reservas = reservas;
    }

    public record Perfil(String nome, String email, String foto, String telefone, String cidade) {}
    public record Atendimento(String id, String profissional, LocalDateTime dataHora) {}
    public record Contato(byte[] foto, String telefone, String cidade) {}
    public record Imagem(byte[] foto) {}
    public record FotoPublica(String fotoUrl) {}


    @GetMapping("/cliente/perfil")
    public ResponseEntity<Perfil> perfil(@RequestHeader(value="Authorization", required=false) String auth) {
        var conta = contas.exigir(auth);
        var query = Query.query(where("_id").is(conta.id()));
        query.fields().include("foto").include("telefone").include("cidade");
        var contato = mongo.findOne(query, Contato.class, "clientes");
        byte[] bytes = contato == null ? null : contato.foto();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Perfil(conta.nome(), conta.email(),
            bytes == null ? null : "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes),
            contato == null ? null : contato.telefone(), contato == null ? null : contato.cidade()));
    }

    @PutMapping("/cliente/perfil")
    public ResponseEntity<Perfil> salvar(@RequestHeader(value="Authorization", required=false) String auth,
            @RequestParam String nome, @RequestParam(required=false) MultipartFile foto,
            @RequestParam(required=false) String telefone, @RequestParam(required=false) String cidade) {
        var conta = contas.exigir(auth);
        if (nome.isBlank() || nome.strip().length() > 120)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um nome com até 120 caracteres.");
        var update = new Update().set("nome", nome.strip());
        if (telefone != null) {
            if (!telefone.isBlank() && (telefone.length() > 24 || !telefone.matches("[+0-9() .-]+")
                    || !telefone.replaceAll("[^0-9]", "").matches("[0-9]{8,15}")))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confira o telefone informado.");
            update.set("telefone", telefone.strip());
        }
        if (cidade != null) {
            if (cidade.strip().length() > 100)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use até 100 caracteres na cidade.");
            update.set("cidade", cidade.strip());
        }
        if (foto != null) update.set("foto", FotoPerfil.preparar(foto));
        mongo.updateFirst(Query.query(where("id").is(conta.id())), update, ContasClientes.Conta.class);
        return perfil(auth);
    }

    @GetMapping("/cliente/atendimentos")
    public ResponseEntity<List<Atendimento>> listar(@RequestHeader(value="Authorization", required=false) String auth) {
        var itens = reservas.findByClienteIdOrderByDataHoraDesc(contas.exigir(auth).id());
        var ids = itens.stream().map(a -> a.getProfissionalId()).distinct().toList();
        var query = Query.query(where("id").in(ids));
        query.fields().include("nome");
        Map<String,String> nomes = mongo.find(query, Profissional.class).stream()
            .collect(Collectors.toMap(Profissional::getId, Profissional::getNome));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(itens.stream().map(a ->
            new Atendimento(a.getId(), nomes.getOrDefault(a.getProfissionalId(), "Profissional"), a.getDataHora())).toList());
    }

    @PutMapping("/profissionais/{id}/foto")
    public FotoPublica enviar(@PathVariable String id, @RequestHeader(value="X-Agenda-Key", required=false) String chave,
            @RequestParam MultipartFile foto) {
        var acesso = AcessoAgenda.autorizar(profissionais.findById(id).orElse(null), chave);
        String url = "/profissionais/" + id + "/foto?v=" + UUID.randomUUID();
        var resultado = mongo.updateFirst(Query.query(where("id").is(id)).addCriteria(acesso),
            new Update().set("foto", FotoPerfil.preparar(foto)).set("fotoUrl", url), Profissional.class);
        if (resultado.getMatchedCount() != 1) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return new FotoPublica(url);
    }

    @GetMapping(value="/profissionais/{id}/foto", produces=MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> fotoPublica(@PathVariable String id) {
        // Os identificadores profissionais são ObjectIds gerados pelo MongoDB.
        if (!org.bson.types.ObjectId.isValid(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var query = Query.query(where("_id").is(new org.bson.types.ObjectId(id)));
        query.fields().include("foto");
        var imagem = mongo.findOne(query, Imagem.class, "profissionais");
        if (imagem == null || imagem.foto() == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(imagem.foto());
    }
}
