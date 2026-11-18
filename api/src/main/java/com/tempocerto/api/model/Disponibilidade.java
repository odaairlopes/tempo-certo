package com.tempocerto.api.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

public record Disponibilidade(
    @NotNull @Size(max = 7) List<@NotNull @Valid Expediente> expedientes,
    @NotNull @Size(max = 366) List<@NotNull LocalDate> folgas
) {
    public boolean permite(LocalDateTime horario) {
        return !folgas.contains(horario.toLocalDate()) && expedientes.stream().anyMatch(expediente ->
            expediente.dia() == horario.getDayOfWeek()
                && !horario.toLocalTime().isBefore(expediente.inicio())
                && ChronoUnit.MINUTES.between(horario.toLocalTime(), expediente.fim()) >= 30
                && ChronoUnit.MINUTES.between(expediente.inicio(), horario.toLocalTime()) % 30 == 0);
    }

    public record Expediente(@NotNull DayOfWeek dia, @NotNull LocalTime inicio, @NotNull LocalTime fim) {}
}
