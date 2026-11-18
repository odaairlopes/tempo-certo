package com.tempocerto.api.service;

import com.tempocerto.api.model.Agendamento;
import com.tempocerto.api.repository.ProfissionalRepository;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Component
public class NotificacaoAgendamento {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificacaoAgendamento.class);
    private final RestClient client;
    private final URI url;
    private final String chave;
    private final Clock relogio;
    private final MongoOperations mongo;
    private final ProfissionalRepository profissionais;

    public NotificacaoAgendamento(@Value("${tempocerto.webhook-url}") String url,
                                 @Value("${tempocerto.webhook-key}") String chave,
                                 Clock relogio, MongoOperations mongo, ProfissionalRepository profissionais) {
        if (!url.isBlank() && chave.isBlank()) {
            throw new IllegalArgumentException("Configure N8N_WEBHOOK_KEY para usar o webhook.");
        }
        var http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(3)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.client = RestClient.builder().requestFactory(factory).build();
        this.url = url.isBlank() ? null : URI.create(url);
        if (this.url != null && (this.url.getHost() == null || this.url.getUserInfo() != null
                || !("http".equalsIgnoreCase(this.url.getScheme()) || "https".equalsIgnoreCase(this.url.getScheme())))) {
            throw new IllegalArgumentException("Configure N8N_WEBHOOK_URL com uma URL HTTP ou HTTPS.");
        }
        this.chave = chave;
        this.relogio = relogio;
        this.mongo = mongo;
        this.profissionais = profissionais;
    }

    @Scheduled(fixedDelay = 5000)
    public void processar() {
        if (url == null) return;
        entregar("confirmacao", "CONFIRMACAO");
        entregar("lembrete", "LEMBRETE");
    }

    private void entregar(String campo, String tipo) {
        var agora = relogio.instant().truncatedTo(ChronoUnit.MILLIS);
        var limite = agora.plusSeconds(90);
        String proxima = campo + ".proxima";
        var fila = Query.query(where(proxima).lte(agora).and("dataHora")
            .gt(LocalDateTime.ofInstant(agora, relogio.getZone()))).with(Sort.by(proxima));
        var reserva = mongo.findAndModify(fila, new Update().set(proxima, limite).inc(campo + ".tentativas", 1),
            FindAndModifyOptions.options().returnNew(true), Agendamento.class);
        if (reserva == null) return;

        var claim = Query.query(where("id").is(reserva.getId()).and(proxima).is(limite));
        var profissional = profissionais.findById(reserva.getProfissionalId());
        if (profissional.isEmpty()) {
            mongo.updateFirst(claim, new Update().unset(campo), Agendamento.class);
            return;
        }

        String entregaId = reserva.getId() + "-" + tipo;
        var evento = new Evento(reserva.getId(), entregaId, tipo, profissional.get().getNome(),
            reserva.getNomeCliente(), reserva.getEmailCliente(), reserva.getDataHora().toString());
        try {
            var resposta = client.post().uri(url).header("X-Webhook-Key", chave).header("Idempotency-Key", entregaId)
                .contentType(MediaType.APPLICATION_JSON).body(evento).retrieve().toBodilessEntity();
            if (!resposta.getStatusCode().is2xxSuccessful()) {
                throw new RestClientException("O webhook não confirmou a entrega.");
            }
            mongo.updateFirst(claim, new Update().unset(campo), Agendamento.class);
        } catch (RestClientException exception) {
            var entrega = campo.equals("confirmacao") ? reserva.getConfirmacao() : reserva.getLembrete();
            long espera = Math.min(3600, 30L << Math.min(7, entrega.tentativas() - 1));
            mongo.updateFirst(claim, new Update().set(proxima, relogio.instant().plusSeconds(espera)), Agendamento.class);
            LOGGER.warn("Falha na entrega {}: {}", entregaId, exception.getMostSpecificCause().getClass().getSimpleName());
            if (entrega.tentativas() == 1) Sentry.captureMessage("Falha no webhook de agendamento", SentryLevel.WARNING);
        }
    }

    public record Evento(String id, String entregaId, String tipo, String profissional, String nomeCliente,
                         String emailCliente, String dataHora) {}
}
