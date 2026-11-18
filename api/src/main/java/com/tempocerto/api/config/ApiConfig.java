package com.tempocerto.api.config;

import io.sentry.Sentry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableScheduling
public class ApiConfig implements WebMvcConfigurer {
    private final String origem;
    private final String sentryDsn;

    public ApiConfig(@Value("${tempocerto.cors-origin}") String origem,
                     @Value("${tempocerto.sentry-dsn}") String sentryDsn) {
        this.origem = origem;
        this.sentryDsn = sentryDsn;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/auth/clientes/*").allowedOrigins(origem).allowedMethods("POST").allowedHeaders("Content-Type");
        registry.addMapping("/auth/google").allowedOrigins(origem).allowedMethods("POST").allowedHeaders("Content-Type");
        registry.addMapping("/profissionais/*/categoria").allowedOrigins(origem).allowedMethods("PUT")
            .allowedHeaders("Content-Type", "X-Agenda-Key");
        registry.addMapping("/profissionais/*/horarios").allowedOrigins(origem).allowedMethods("GET");
        registry.addMapping("/profissionais/*/disponibilidade").allowedOrigins(origem).allowedMethods("GET", "PUT")
            .allowedHeaders("Content-Type", "X-Agenda-Key");
        for (String rota : new String[] {"/profissionais", "/agendamentos", "/agendamentos/profissional/*"}) {
            registry.addMapping(rota).allowedOrigins(origem).allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type", "X-Agenda-Key", "Authorization");
        }
    }

    @Bean
    Clock relogio() {
        return Clock.system(ZoneId.of("America/Sao_Paulo"));
    }

    @PostConstruct
    void iniciarSentry() {
        if (!sentryDsn.isBlank()) {
            Sentry.init(options -> {
                options.setDsn(sentryDsn);
                options.setSendDefaultPii(false);
            });
        }
    }

    @PreDestroy
    void fecharSentry() {
        if (!sentryDsn.isBlank()) Sentry.close();
    }
}
