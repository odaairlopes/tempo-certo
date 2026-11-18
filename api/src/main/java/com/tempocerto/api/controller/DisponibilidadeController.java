package com.tempocerto.api.controller;

import com.tempocerto.api.model.Disponibilidade;
import com.tempocerto.api.service.DisponibilidadeService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/profissionais/{id}")
public class DisponibilidadeController {
    private final DisponibilidadeService service;

    public DisponibilidadeController(DisponibilidadeService service) {
        this.service = service;
    }

    @GetMapping("/horarios")
    public ResponseEntity<DisponibilidadeService.Horarios> horarios(@PathVariable String id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.horarios(id, data));
    }

    @GetMapping("/disponibilidade")
    public ResponseEntity<Configuracao> consultar(@PathVariable String id,
            @RequestHeader(value = "X-Agenda-Key", required = false) String chave) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Configuracao(service.consultar(id, chave)));
    }

    @PutMapping("/disponibilidade")
    public ResponseEntity<Configuracao> salvar(@PathVariable String id, @Valid @RequestBody Disponibilidade disponibilidade,
            @RequestHeader(value = "X-Agenda-Key", required = false) String chave) {
        service.salvar(id, chave, disponibilidade);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Configuracao(disponibilidade));
    }

    public record Configuracao(Disponibilidade disponibilidade) {}
}
