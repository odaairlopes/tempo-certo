package com.tempocerto.api.service;

import com.tempocerto.api.dto.NovoAgendamento;
import com.tempocerto.api.model.Agendamento;
import com.tempocerto.api.model.Profissional;
import com.tempocerto.api.repository.AgendamentoRepository;
import com.tempocerto.api.repository.ProfissionalRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgendamentoServiceTest {
    private final Clock relogio = Clock.fixed(Instant.parse("2026-11-17T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    private final AgendamentoRepository agendamentos = mock(AgendamentoRepository.class);
    private final ProfissionalRepository profissionais = mock(ProfissionalRepository.class);
    private final AgendamentoService service = new AgendamentoService(agendamentos, profissionais, relogio);
    private final LocalDateTime futuro = LocalDateTime.now(relogio).plusHours(1);

    @BeforeEach
    void preparar() {
        var profissional = new Profissional();
        profissional.setId("prof1");
        profissional.setNome("Profissional");
        when(profissionais.findById("prof1")).thenReturn(Optional.of(profissional));
    }

    @Test
    void gravaConfirmacaoELembreteJuntoComAReserva() {
        when(agendamentos.insert(any(Agendamento.class))).thenAnswer(invocacao -> {
            Agendamento salvo = invocacao.getArgument(0);
            assertThat(salvo.getConfirmacao()).isEqualTo(new Agendamento.Entrega(relogio.instant(), 0));
            assertThat(salvo.getLembrete()).isEqualTo(new Agendamento.Entrega(relogio.instant().plusSeconds(3600), 0));
            return salvo;
        });
        service.criar(pedido(futuro.plusDays(1)));
        verify(agendamentos).insert(any(Agendamento.class));
    }

    @Test
    void rejeitaHorarioPassadoAntesDePersistir() {
        assertThatExceptionOfType(ResponseStatusException.class)
            .isThrownBy(() -> service.criar(pedido(futuro.minusHours(2))))
            .satisfies(erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(agendamentos);
    }

    @Test
    void retornaConflitoQuandoIndiceRecusaReserva() {
        when(agendamentos.insert(any(Agendamento.class))).thenThrow(new DuplicateKeyException("horário ocupado"));
        assertThatExceptionOfType(ResponseStatusException.class).isThrownBy(() -> service.criar(pedido(futuro)))
            .satisfies(erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void chaveInvalidaNaoConsultaDadosDosClientes() {
        assertThatExceptionOfType(ResponseStatusException.class)
            .isThrownBy(() -> service.buscarPorProfissional("prof1", "chave"))
            .satisfies(erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(agendamentos);
    }

    private NovoAgendamento pedido(LocalDateTime horario) {
        return new NovoAgendamento("prof1", "Cliente", "cliente@example.com", horario);
    }
}
