package com.tempocerto.api.controller;

import com.tempocerto.api.model.Profissional;
import com.tempocerto.api.repository.ProfissionalRepository;
import java.time.Instant;
import java.util.List;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleControllerTest {
    private final ProfissionalRepository perfis = mock(ProfissionalRepository.class);
    private final MongoOperations mongo = mock(MongoOperations.class);
    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final GoogleController login = new GoogleController("cliente", perfis, mongo, decoder);
    private final GoogleController.Login pedido = new GoogleController.Login("token");

    private void identidade(String audiencia, String email) {
        when(decoder.decode("token")).thenReturn(Jwt.withTokenValue("token").header("alg", "RS256")
            .issuer("https://accounts.google.com").subject("usuario-google").audience(List.of(audiencia))
            .claim("email", email).claim("email_verified", true).expiresAt(Instant.now().plusSeconds(300)).build());
    }

    @Test
    void rejeitaTokenDeOutroAplicativo() {
        identidade("outro", "ana@gmail.com");
        assertThatThrownBy(() -> login.entrar(pedido)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(perfis, mongo);
    }

    @Test
    void exigeAutoridadeGoogleParaVincularEmail() {
        identidade("cliente", "ana@example.com");
        when(perfis.findTop2ByEmailIgnoreCase("ana@example.com")).thenReturn(List.of(new Profissional()));
        assertThatThrownBy(() -> login.entrar(pedido)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(mongo);
    }

    @Test
    void criaSessaoParaPerfilCadastrado() {
        identidade("cliente", "ana@gmail.com");
        var perfil = new Profissional();
        perfil.setId("perfil");
        perfil.setEmail("ana@gmail.com");
        when(perfis.findTop2ByEmailIgnoreCase(perfil.getEmail())).thenReturn(List.of(perfil));
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(Profissional.class)))
            .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        var sessao = login.entrar(pedido).getBody();
        assertThat(sessao.id()).isEqualTo("perfil");
        assertThat(sessao.chaveAgenda()).isNotBlank();
    }
}
