package com.tempocerto.api.service;

import com.tempocerto.api.dto.NovoAgendamento;
import com.tempocerto.api.model.Agendamento;
import com.tempocerto.api.repository.AgendamentoRepository;
import com.tempocerto.api.repository.ProfissionalRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgendamentoService {
    private final AgendamentoRepository agendamentos;
    private final ProfissionalRepository profissionais;
    private final Clock relogio;

    public AgendamentoService(AgendamentoRepository agendamentos, ProfissionalRepository profissionais,
                              Clock relogio) {
        this.agendamentos = agendamentos;
        this.profissionais = profissionais;
        this.relogio = relogio;
    }

    public Agendamento criar(NovoAgendamento pedido) {
        var horario = pedido.dataHora();
        var agoraLocal = LocalDateTime.now(relogio);
        if (!horario.isAfter(agoraLocal) || horario.getSecond() != 0 || horario.getNano() != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione um horário futuro em minutos inteiros.");
        }
        var profissional = profissionais.findById(pedido.profissionalId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profissional não encontrado."));
        var disponibilidade = profissional.getDisponibilidade();
        if (disponibilidade != null && (!disponibilidade.permite(horario)
                || horario.toLocalDate().isAfter(agoraLocal.toLocalDate().plusDays(365)))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O profissional não atende nesse horário.");
        }
        if (disponibilidade != null && agendamentos.existeEntre(
                profissional.getId(), horario.minusMinutes(30), horario.plusMinutes(30))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este horário já está reservado.");
        }
        var agendamento = new Agendamento();
        agendamento.setProfissionalId(profissional.getId());
        agendamento.setNomeCliente(pedido.nomeCliente().strip());
        agendamento.setEmailCliente(pedido.emailCliente());
        agendamento.setDataHora(horario);
        var agora = relogio.instant();
        var momentoLembrete = horario.atZone(relogio.getZone()).toInstant().minus(Duration.ofHours(24));
        agendamento.setConfirmacao(new Agendamento.Entrega(agora, 0));
        if (momentoLembrete.isAfter(agora)) {
            agendamento.setLembrete(new Agendamento.Entrega(momentoLembrete, 0));
        }
        try {
            return agendamentos.insert(agendamento);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este horário já está reservado.", exception);
        }
    }

    public List<Agendamento> buscarPorProfissional(String id, String chave) {
        AcessoAgenda.autorizar(profissionais.findById(id).orElse(null), chave);
        return agendamentos.findByProfissionalIdOrderByDataHoraAsc(id);
    }

}
