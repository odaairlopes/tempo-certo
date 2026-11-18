package com.tempocerto.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record NovoAgendamento(
    @NotBlank @Size(max = 128) String profissionalId,
    @NotBlank @Size(max = 120) String nomeCliente,
    @NotBlank @Email @Size(max = 254) String emailCliente,
    @NotNull LocalDateTime dataHora
) {}
