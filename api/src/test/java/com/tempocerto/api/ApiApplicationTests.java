package com.tempocerto.api;

import com.sun.net.httpserver.HttpServer;
import com.tempocerto.api.model.Agendamento;
import com.tempocerto.api.model.Profissional;
import com.tempocerto.api.repository.AgendamentoRepository;
import com.tempocerto.api.repository.ProfissionalRepository;
import com.tempocerto.api.service.AcessoAgenda;
import com.tempocerto.api.service.NotificacaoAgendamento;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoOperations;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_MONGO_TESTS", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "management.server.port=0",
    "tempocerto.webhook-url=",
    "tempocerto.sentry-dsn="
})
class ApiApplicationTests {
    @LocalServerPort
    int port;

    @Autowired
    ProfissionalRepository profissionais;

    @Autowired
    AgendamentoRepository agendamentos;

    @Autowired
    MongoOperations mongo;

    private String cadastrarEEntrar(HttpClient http, String id) throws Exception {
        String json = """
            {"nome":"Cliente Teste","email":"%s@example.com","senha":"senha-de-teste"}
            """.formatted(id);
        var cadastro = HttpRequest.newBuilder(uri("/auth/clientes/cadastro")).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        assertThat(http.send(cadastro, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(201);
        var login = HttpRequest.newBuilder(uri("/auth/clientes/login")).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        var resposta = http.send(login, HttpResponse.BodyHandlers.ofString());
        assertThat(resposta.statusCode()).withFailMessage(resposta.body()).isEqualTo(200);
        return new tools.jackson.databind.ObjectMapper().readTree(resposta.body()).get("token").asString();
    }

    @Test
    void salvaUmaUnicaReservaMesmoComRequisicoesSimultaneas() throws Exception {
        String id = profissionalDeTeste();
        var horario = LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).plusDays(1).withSecond(0).withNano(0);
        var json = """
            {"profissionalId":"%s","nomeCliente":"Cliente Teste","emailCliente":"cliente@example.com","dataHora":"%s"}
            """.formatted(id, horario);
        try (var http = HttpClient.newHttpClient()) {
            var anonimo = HttpRequest.newBuilder(uri("/agendamentos")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
            assertThat(http.send(anonimo, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
            var token = cadastrarEEntrar(http, id);
            var post = HttpRequest.newBuilder(uri("/agendamentos")).header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
            var primeira = http.sendAsync(post, HttpResponse.BodyHandlers.ofString());
            var segunda = http.sendAsync(post, HttpResponse.BodyHandlers.ofString());
            assertThat(Stream.of(primeira.get(), segunda.get()).map(HttpResponse::statusCode).toList())
                .containsExactlyInAnyOrder(200, 409);
            assertThat(agendamentos.findByProfissionalIdOrderByDataHoraAsc(id)).hasSize(1);

            var agenda = uri("/agendamentos/profissional/" + id);
            var semChave = http.send(HttpRequest.newBuilder(agenda).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(semChave.statusCode()).isEqualTo(403);
            var autorizado = http.send(HttpRequest.newBuilder(agenda).header("X-Agenda-Key", "chave-de-teste").build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(autorizado.statusCode()).isEqualTo(200);
            assertThat(autorizado.body()).contains("Cliente Teste", id + "@example.com").doesNotContain("cliente@example.com");
            assertThat(autorizado.headers().firstValue("Cache-Control")).contains("no-store");

            var lista = http.send(HttpRequest.newBuilder(uri("/profissionais")).build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(lista.body()).contains(id).doesNotContain("profissional@example.com", "chaveAgendaHash");
            var categoria = HttpRequest.newBuilder(uri("/profissionais/" + id + "/categoria"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"categoria\":\"Fisioterapia\"}"));
            assertThat(http.send(categoria.build(), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
            var atualizado = http.send(categoria.header("X-Agenda-Key", "chave-de-teste").build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(atualizado.statusCode()).isEqualTo(200);
            assertThat(profissionais.findById(id).orElseThrow().getCategoria()).isEqualTo("Fisioterapia");

            var invalido = http.send(HttpRequest.newBuilder(uri("/agendamentos"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(invalido.statusCode()).isEqualTo(400);
        } finally {
            agendamentos.deleteAll(agendamentos.findByProfissionalIdOrderByDataHoraAsc(id));
            profissionais.deleteById(id);
        }
    }

    @Test
    void disponibilizaSomenteHorariosLivresERejeitaFolga() throws Exception {
        String id = profissionalDeTeste();
        var data = LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).toLocalDate().plusDays(1);
        var rota = "/profissionais/" + id;
        String configuracao = """
            {"expedientes":[{"dia":"%s","inicio":"09:00","fim":"10:00"}],"folgas":["%s"]}
            """.formatted(data.getDayOfWeek(), data.plusWeeks(1));
        try (var http = HttpClient.newHttpClient()) {
            var salvar = HttpRequest.newBuilder(uri(rota + "/disponibilidade"))
                .header("X-Agenda-Key", "chave-de-teste").header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(configuracao)).build();
            var resposta = http.send(salvar, HttpResponse.BodyHandlers.ofString());
            assertThat(resposta.statusCode()).withFailMessage(resposta.body()).isEqualTo(200);

            var horarios = HttpRequest.newBuilder(uri(rota + "/horarios?data=" + data)).build();
            assertThat(http.send(horarios, HttpResponse.BodyHandlers.ofString()).body()).contains("09:00", "09:30");
            var token = cadastrarEEntrar(http, id);
            for (var horario : new String[] {data + "T09:00", data + "T10:00", data.plusWeeks(1) + "T09:00"}) {
                String json = """
                    {"profissionalId":"%s","nomeCliente":"Cliente","emailCliente":"cliente@example.com","dataHora":"%s"}
                    """.formatted(id, horario);
                var reserva = HttpRequest.newBuilder(uri("/agendamentos")).header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build();
                var resultado = http.send(reserva, HttpResponse.BodyHandlers.ofString());
                assertThat(resultado.statusCode()).withFailMessage(resultado.body())
                    .isEqualTo(horario.equals(data + "T09:00") ? 200 : 409);
                assertThat(resultado.body()).doesNotContain("\"confirmacao\"", "\"lembrete\"");
            }
            assertThat(http.send(horarios, HttpResponse.BodyHandlers.ofString()).body())
                .contains("09:30").doesNotContain("09:00");
        } finally {
            agendamentos.deleteAll(agendamentos.findByProfissionalIdOrderByDataHoraAsc(id));
            profissionais.deleteById(id);
        }
    }

    @Test
    void reprocessaFalhaPersistidaEEntregaLembrete() throws Exception {
        String id = profissionalDeTeste();
        var zona = ZoneId.of("America/Sao_Paulo");
        var agora = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var reserva = new Agendamento();
        reserva.setProfissionalId(id);
        reserva.setNomeCliente("Cliente");
        reserva.setEmailCliente("cliente@example.com");
        reserva.setDataHora(LocalDateTime.ofInstant(agora, zona).plusHours(2));
        reserva.setConfirmacao(new Agendamento.Entrega(agora, 0));
        reserva.setLembrete(new Agendamento.Entrega(agora.plusSeconds(30), 0));
        String reservaId = agendamentos.insert(reserva).getId();
        var chamadas = new CopyOnWriteArrayList<String>();
        var corpos = new CopyOnWriteArrayList<String>();
        var webhook = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        webhook.createContext("/", exchange -> {
            chamadas.add(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            corpos.add(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(chamadas.size() == 1 ? 503 : 204, -1);
            exchange.close();
        });
        webhook.start();
        String url = "http://127.0.0.1:" + webhook.getAddress().getPort();
        try {
            new NotificacaoAgendamento(url, "chave-teste", Clock.fixed(agora, zona), mongo, profissionais).processar();
            assertThat(agendamentos.findById(reservaId).orElseThrow().getConfirmacao())
                .isEqualTo(new Agendamento.Entrega(agora.plusSeconds(30), 1));
            // Um novo worker deve retomar a entrega usando apenas o estado do MongoDB.
            new NotificacaoAgendamento(url, "chave-teste", Clock.fixed(agora.plusSeconds(30), zona), mongo, profissionais)
                .processar();
            assertThat(chamadas).containsExactly(reservaId + "-CONFIRMACAO",
                reservaId + "-CONFIRMACAO", reservaId + "-LEMBRETE");
            assertThat(corpos.getLast()).contains("\"tipo\":\"LEMBRETE\"");
            var salvo = agendamentos.findById(reservaId).orElseThrow();
            assertThat(salvo.getConfirmacao()).isNull();
            assertThat(salvo.getLembrete()).isNull();
        } finally {
            webhook.stop(0);
            agendamentos.deleteById(reservaId);
            profissionais.deleteById(id);
        }
    }

    private String profissionalDeTeste() {
        var profissional = new Profissional();
        profissional.setNome("Profissional de teste");
        profissional.setEmail("profissional@example.com");
        profissional.setChaveAgendaHash(AcessoAgenda.hash("chave-de-teste"));
        return profissionais.insert(profissional).getId();
    }

    private URI uri(String caminho) {
        return URI.create("http://localhost:" + port + caminho);
    }
}
