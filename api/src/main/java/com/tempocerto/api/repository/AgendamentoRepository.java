package com.tempocerto.api.repository;

import com.tempocerto.api.model.Agendamento;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface AgendamentoRepository extends MongoRepository<Agendamento, String> {
    List<Agendamento> findByProfissionalIdOrderByDataHoraAsc(String profissionalId);
    @Query("{'profissionalId': ?0, 'dataHora': {'$gte': ?1, '$lt': ?2}}")
    List<Agendamento> findNoPeriodo(
        String profissionalId, LocalDateTime inicio, LocalDateTime fim);
    @Query(value = "{'profissionalId': ?0, 'dataHora': {'$gt': ?1, '$lt': ?2}}", exists = true)
    boolean existeEntre(
        String profissionalId, LocalDateTime inicio, LocalDateTime fim);
}
