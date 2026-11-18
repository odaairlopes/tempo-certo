package com.tempocerto.api.controller;

import com.tempocerto.api.dto.NovoAgendamento;
import com.tempocerto.api.model.Agendamento;
import com.tempocerto.api.service.AgendamentoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/agendamentos")
public class AgendamentoController {
    private final AgendamentoService service;

    public AgendamentoController(AgendamentoService service) {
        this.service = service;
    }

    @PostMapping
    public Agendamento criar(@Valid @RequestBody NovoAgendamento pedido) {
        return service.criar(pedido);
    }

    @GetMapping("/profissional/{id}")
    public ResponseEntity<List<Agendamento>> buscarPorProfissional(@PathVariable String id,
        @RequestHeader(value = "X-Agenda-Key", required = false) String chave) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.buscarPorProfissional(id, chave));
    }
}
