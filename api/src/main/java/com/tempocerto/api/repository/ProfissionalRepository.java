package com.tempocerto.api.repository;

import com.tempocerto.api.model.Profissional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProfissionalRepository extends MongoRepository<Profissional, String> {
    @org.springframework.data.mongodb.repository.Query(value = "{}", fields = "{'nome':1,'categoria':1,'fotoUrl':1}")
    java.util.List<Profissional> listarPerfisPublicos();

    java.util.List<Profissional> findTop2ByEmailIgnoreCase(String email);
}
