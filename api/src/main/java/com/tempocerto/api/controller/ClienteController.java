package com.tempocerto.api.controller;

import com.tempocerto.api.service.ContasClientes;
import com.tempocerto.api.service.IdentidadeGoogle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/clientes")
public class ClienteController {
    public record Cadastro(@NotBlank @Size(max=120) String nome, @NotBlank @Email @Size(max=254) String email,
                           @NotBlank @Size(min=8,max=128) String senha) {}
    public record Login(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=128) String senha) {}

    private final ContasClientes contas;
    private final IdentidadeGoogle google;

    public ClienteController(ContasClientes contas, IdentidadeGoogle google) {
        this.contas = contas;
        this.google = google;
    }

    @PostMapping("/cadastro")
    public ResponseEntity<ContasClientes.Sessao> cadastrar(@Valid @RequestBody Cadastro dados) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
            .body(contas.cadastrar(dados.nome(), dados.email(), dados.senha()));
    }

    @PostMapping("/login")
    public ResponseEntity<ContasClientes.Sessao> entrar(@Valid @RequestBody Login dados) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(contas.entrar(dados.email(), dados.senha()));
    }

    @PostMapping("/google")
    public ResponseEntity<ContasClientes.Sessao> entrarGoogle(@Valid @RequestBody GoogleController.Login dados) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(contas.entrarGoogle(google.verificar(dados.credential())));
    }
}
