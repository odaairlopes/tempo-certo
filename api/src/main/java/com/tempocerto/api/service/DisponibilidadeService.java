package com.tempocerto.api.service;

import com.tempocerto.api.model.Disponibilidade;
import com.tempocerto.api.model.Profissional;
import com.tempocerto.api.repository.AgendamentoRepository;
import com.tempocerto.api.repository.ProfissionalRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Service
public class DisponibilidadeService {
    private final ProfissionalRepository profissionais;
    private final AgendamentoRepository agendamentos;
    private final MongoOperations mongo;
    private final Clock relogio;

    public DisponibilidadeService(ProfissionalRepository profissionais, AgendamentoRepository agendamentos,
                                  MongoOperations mongo, Clock relogio) {
        this.profissionais = profissionais;
        this.agendamentos = agendamentos;
        this.mongo = mongo;
        this.relogio = relogio;
    }

    public Disponibilidade consultar(String id, String chave) {
        var profissional = profissionais.findById(id).orElse(null);
        AcessoAgenda.autorizar(profissional, chave);
        return profissional.getDisponibilidade();
    }

    public void salvar(String id, String chave, Disponibilidade disponibilidade) {
        var profissional = profissionais.findById(id).orElse(null);
        var acesso = AcessoAgenda.autorizar(profissional, chave);
        boolean diasRepetidos = disponibilidade.expedientes().stream().map(Disponibilidade.Expediente::dia)
            .distinct().count() != disponibilidade.expedientes().size();
        boolean horarioInvalido = disponibilidade.expedientes().stream().anyMatch(expediente ->
            !expediente.inicio().equals(expediente.inicio().truncatedTo(ChronoUnit.MINUTES))
                || !expediente.fim().equals(expediente.fim().truncatedTo(ChronoUnit.MINUTES))
                || expediente.inicio().getMinute() % 30 != 0 || expediente.fim().getMinute() % 30 != 0
                || ChronoUnit.MINUTES.between(expediente.inicio(), expediente.fim()) < 30);
        if (diasRepetidos || horarioInvalido) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Informe um expediente por dia, com horários inteiros ou de meia em meia hora.");
        }
        var resultado = mongo.updateFirst(Query.query(where("id").is(id)).addCriteria(acesso),
            new Update().set("disponibilidade", disponibilidade), Profissional.class);
        if (resultado.getMatchedCount() == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Credencial de acesso inválida.");
        }
    }

    public Horarios horarios(String id, LocalDate data) {
        var agora = LocalDateTime.now(relogio);
        if (data.isBefore(agora.toLocalDate()) || data.isAfter(agora.toLocalDate().plusDays(365))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha uma data nos próximos 365 dias.");
        }
        var profissional = profissionais.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profissional não encontrado."));
        var disponibilidade = profissional.getDisponibilidade();
        if (disponibilidade == null) return new Horarios(false, List.of());
        var expediente = disponibilidade.expedientes().stream()
            .filter(janela -> janela.dia() == data.getDayOfWeek()).findFirst();
        if (expediente.isEmpty() || disponibilidade.folgas().contains(data)) return new Horarios(true, List.of());

        var inicio = data.atStartOfDay();
        var ocupados = agendamentos.findNoPeriodo(
            profissional.getId(), inicio.minusMinutes(30), inicio.plusDays(1)).stream()
            .map(reserva -> reserva.getDataHora()).toList();
        var horarios = new ArrayList<LocalTime>();
        var fim = data.atTime(expediente.get().fim());
        for (var horario = data.atTime(expediente.get().inicio()); !horario.plusMinutes(30).isAfter(fim);
             horario = horario.plusMinutes(30)) {
            var candidato = horario;
            if (horario.isAfter(agora) && ocupados.stream().noneMatch(reserva ->
                    Duration.between(reserva, candidato).abs().compareTo(Duration.ofMinutes(30)) < 0)) {
                horarios.add(horario.toLocalTime());
            }
        }
        return new Horarios(true, horarios);
    }

    public record Horarios(boolean configurada, List<LocalTime> horarios) {}
}
