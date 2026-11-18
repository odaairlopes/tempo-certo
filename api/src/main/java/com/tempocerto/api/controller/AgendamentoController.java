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

    private final com.tempocerto.api.service.ContasClientes contas;
    public record Reserva(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=128) String profissionalId,
                          @jakarta.validation.constraints.NotNull java.time.LocalDateTime dataHora) {}

    public AgendamentoController(AgendamentoService service, com.tempocerto.api.service.ContasClientes contas) {
        this.service = service;
        this.contas = contas;
    }

    @PostMapping
    public Agendamento criar(@Valid @RequestBody Reserva pedido,
        @RequestHeader(value = "Authorization", required = false) String authorization) {
        var cliente = contas.exigir(authorization);
        return service.criar(new NovoAgendamento(pedido.profissionalId(), cliente.nome(), cliente.email(), pedido.dataHora()), cliente.id());
    }

    @GetMapping("/profissional/{id}")
    public ResponseEntity<List<Agendamento>> buscarPorProfissional(@PathVariable String id,
        @RequestHeader(value = "X-Agenda-Key", required = false) String chave) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.buscarPorProfissional(id, chave));
    }
}
