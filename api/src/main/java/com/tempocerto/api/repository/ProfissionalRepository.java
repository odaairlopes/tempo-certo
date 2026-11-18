package com.tempocerto.api.repository;

import com.tempocerto.api.model.Profissional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProfissionalRepository extends MongoRepository<Profissional, String> {
    java.util.List<Profissional> findTop2ByEmailIgnoreCase(String email);
}
