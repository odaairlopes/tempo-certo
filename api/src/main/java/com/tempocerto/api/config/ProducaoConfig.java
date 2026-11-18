package com.tempocerto.api.config;

import com.mongodb.ConnectionString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("prod")
class ProducaoConfig {
    ProducaoConfig(@Value("${spring.mongodb.uri}") String uri,
                   @Value("${tempocerto.admin-key}") String chave) {
        if (!temCredenciaisMongo(uri)) {
            throw new IllegalStateException("Produção exige um MONGODB_URI com usuário e senha.");
        }
        if (chave.isBlank()) {
            throw new IllegalStateException("Produção exige uma ADMIN_KEY.");
        }
    }

    private static boolean temCredenciaisMongo(String uri) {
        try {
            var credencial = new ConnectionString(uri).getCredential();
            return credencial != null && credencial.getUserName() != null
                && credencial.getPassword() != null && credencial.getPassword().length > 0;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
