package com.tempocerto.api.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "profissionais")
public class Profissional {
    @Id
    private String id;
    private String nome;
    private String email;
    private String chaveAgendaHash;
    private String categoria;
    private String googleSub;
    private String sessaoHash;
    private java.time.Instant sessaoAte;
    private Disponibilidade disponibilidade;
}
