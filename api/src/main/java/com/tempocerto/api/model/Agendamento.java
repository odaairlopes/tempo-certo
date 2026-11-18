package com.tempocerto.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "agendamentos")
@CompoundIndexes({
    @CompoundIndex(name = "profissional_horario_unico", def = "{'profissionalId': 1, 'dataHora': 1}", unique = true),
    @CompoundIndex(name = "fila_confirmacao", def = "{'confirmacao.proxima': 1}", sparse = true),
    @CompoundIndex(name = "fila_lembrete", def = "{'lembrete.proxima': 1}", sparse = true)
})
public class Agendamento {
    @Id
    private String id;
    private String profissionalId;
    private String nomeCliente;
    private String emailCliente;
    private LocalDateTime dataHora;
    @JsonIgnore
    private Entrega confirmacao;
    @JsonIgnore
    private Entrega lembrete;

    public record Entrega(Instant proxima, int tentativas) {}
}
