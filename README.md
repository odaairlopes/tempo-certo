# TempoCerto

Plataforma de agendamento de atendimentos. O cliente escolhe a especialidade, o profissional e um horário disponível. Cada profissional tem um portal próprio para configurar o expediente e consultar as reservas.

**Demonstração:** [Agendar atendimento](https://tempo-certo-flame.vercel.app) · [Portal profissional](https://tempo-certo-flame.vercel.app/profissional)

## Como usar

| Área | Acesso | O que você pode fazer |
| --- | --- | --- |
| Agendamento (`/`) | Conta de cliente por Google ou e-mail e senha | Entrar, escolher a especialidade, selecionar o profissional pela foto e confirmar a data e a hora. |
| Meu perfil (`/perfil`) | Cliente autenticado | Alterar nome e foto, informar telefone e cidade opcionais e consultar o e-mail da conta. |
| Meus atendimentos (`/atendimentos`) | Cliente autenticado | Consultar as reservas vinculadas à conta e abrir um evento de 30 minutos no Google Agenda para revisar e salvar. |
| Portal profissional (`/profissional`) | ID e chave da agenda ou Google vinculado ao profissional | Consultar os atendimentos, editar especialidade e foto, definir expediente semanal e cadastrar folgas. |

A sessão do cliente dura até oito horas. O portal profissional fica fora do menu do cliente. Para testar os dois papéis ao mesmo tempo, abra o portal em outra janela anônima. Fotos de perfil aceitam arquivos JPEG ou PNG de até 1 MB. A API reduz a imagem para até 320 pixels no lado maior e grava a miniatura no MongoDB.

### Regras da agenda

- As reservas usam o horário de Brasília (`America/Sao_Paulo`). O formulário limita o ano a quatro dígitos e limpa datas anteriores a hoje. A API exige um horário futuro.
- Antes de configurar o primeiro expediente, o profissional aceita um horário digitado pelo cliente. Depois de salvar a disponibilidade, a agenda oferece horários livres em blocos de 30 minutos, até 365 dias à frente. Dias sem expediente e datas de folga ficam fechados para novas reservas.
- O nome e o e-mail da reserva vêm da sessão autenticada. Um índice único por profissional e horário protege contra reservas simultâneas para o mesmo início. Se outro cliente ocupar a vaga, a API responde `409`.
- O link do Google Agenda abre uma cópia do evento. O cliente confirma a inclusão no calendário, sem conceder ao TempoCerto acesso de escrita.

## Executar localmente

Instale o Docker com o plugin Compose. Na raiz do repositório:

1. Faça uma cópia de [`.env.example`](.env.example) com o nome `.env`. Defina `ADMIN_KEY` com uma chave aleatória privada. O login por e-mail e senha já permite testar o fluxo local.
2. Suba a aplicação:

   ```sh
   docker compose up --build -d
   ```

3. Acesse o [frontend local](http://localhost:3000). Em um banco novo, cadastre pelo menos um profissional conforme a seção seguinte. A [lista pública de profissionais](http://localhost:8080/profissionais) permite conferir o cadastro.
4. Entre no [portal profissional local](http://localhost:3000/profissional), defina um expediente futuro e salve. Na janela de cliente, crie uma conta, reserve um dos horários e confira o resultado em **Meus atendimentos** e na agenda do profissional.

Use `docker compose logs -f api web` para acompanhar a inicialização e `docker compose down` para parar os serviços preservando os volumes. O banco local padrão é `test`. As portas e o nome do banco podem ser ajustados no `.env`.

### Cadastrar um profissional

Em um cliente HTTP, como Postman ou Insomnia, envie `POST http://localhost:8080/profissionais` com o cabeçalho `X-Admin-Key` igual ao valor de `ADMIN_KEY` da API. Selecione corpo JSON e preencha, por exemplo:

```json
{
  "nome": "Ana Souza",
  "email": "ana@example.com",
  "categoria": "Fisioterapia"
}
```

A resposta `201` contém `id`, `nome` e `chaveAgenda`. Guarde o ID e a chave para acessar o portal. Para testar o login profissional com Google, use no cadastro um e-mail real sob seu controle. Na primeira vinculação, a API exige uma conta Gmail ou Google Workspace verificada e um único profissional com aquele e-mail.

Para produção, use o endereço da API do Render e a chave administrativa daquele ambiente. A API armazena o hash da chave de agenda. Se a chave for perdida, `PUT /profissionais/{id}/chave` com `X-Admin-Key` emite outra e invalida a anterior.

## Estrutura do projeto

| Caminho | Responsabilidade |
| --- | --- |
| [`web/`](web/) | Next.js com App Router, React, TypeScript e Tailwind CSS. Inclui reserva, perfil do cliente e portal profissional. |
| [`api/`](api/) | Java 21, Spring Boot e Spring Data MongoDB. Autentica as contas, valida a disponibilidade, persiste reservas e processa notificações. |
| [`infra/`](infra/) | Workflow do n8n, coleta de métricas pelo Prometheus e dashboard do Grafana. |
| [`docker-compose.yml`](docker-compose.yml) | Ambiente local com web, API e MongoDB, além dos perfis opcionais de automação e observabilidade. |
| [`render.yaml`](render.yaml) e [CI](.github/workflows/ci.yml) | Blueprint da API e verificações da API e do frontend no GitHub Actions. |

## Configuração e deploy

A demonstração usa a Vercel para o frontend, o Render para a API e o MongoDB Atlas para os dados. Os dois deploys são independentes. Na Vercel, a raiz do projeto é `web` e o framework é Next.js. No Render, o Blueprint usa `api` e seu Dockerfile.

| Ambiente | Variável | Valor ou finalidade |
| --- | --- | --- |
| Render | `SPRING_PROFILES_ACTIVE` | `prod`. O perfil de produção exige credenciais na URI do MongoDB e uma chave administrativa. |
| Render | `MONGODB_URI` | URI do Atlas com usuário da aplicação, senha codificada para URL e nome do banco usado pela instalação. Preserve o nome do banco existente ao atualizar. |
| Render | `ADMIN_KEY` | Segredo do backend para cadastro de profissionais e emissão de chaves de agenda. |
| Render | `FRONTEND_ORIGIN` | `https://tempo-certo-flame.vercel.app`, sem barra final. Permite as chamadas do navegador via CORS. |
| Vercel | `NEXT_PUBLIC_API_URL` | `https://tempo-certo-api.onrender.com`, disponível no ambiente Production durante o build. |
| Render e Vercel | `GOOGLE_CLIENT_ID` e `NEXT_PUBLIC_GOOGLE_CLIENT_ID` | Mesmo ID público do cliente OAuth, respectivamente na API e no frontend. |

No Atlas, autorize os endereços de saída da API e conceda ao usuário da aplicação acesso de leitura e escrita ao banco escolhido. Mantenha senhas e chaves fora do Git. As variáveis `NEXT_PUBLIC_*` entram no build e ficam acessíveis ao navegador.

**Login Google.** Crie um cliente OAuth do tipo **Aplicativo da Web**. Em **Origens JavaScript autorizadas**, adicione a origem exata do frontend e `http://localhost:3000` se for testar localmente. Configure o público do aplicativo e as contas de teste conforme o ambiente. No Compose, basta definir `GOOGLE_CLIENT_ID` no `.env` e reconstruir com `docker compose up --build -d`. Na Vercel, faça um novo deploy após alterar uma variável pública. [Configuração no Google Identity Services](https://developers.google.com/identity/gsi/web/guides/get-google-api-clientid)

**Conferir a publicação.** Compare o commit da `main` com o deploy em **Vercel → Deployments → Production** e em **Render → tempo-certo-api → Events**. Para alterações na API, aguarde o status **Live**. Abrir `GET /cliente/atendimentos` diretamente, sem sessão, deve retornar `401`. Um `404` nessa rota é motivo para conferir a URL e o commit publicado no backend.

## Referência da API

| Método e rota | Credencial | Operação |
| --- | --- | --- |
| `POST /auth/clientes/cadastro` · `POST /auth/clientes/login` | Pública | Cadastro com nome, e-mail e senha de 8 a 128 caracteres, ou login com e-mail e senha. Retorna nome e token. |
| `POST /auth/clientes/google` · `POST /auth/google` | Credencial Google no JSON | O primeiro abre a sessão de cliente. O segundo autentica um profissional cadastrado. |
| `GET /profissionais` · `GET /profissionais/{id}/foto` | Pública | Lista os perfis públicos ou entrega a foto do profissional. |
| `GET /profissionais/{id}/horarios?data=AAAA-MM-DD` | Pública | Informa se existe expediente configurado e quais horários estão livres. |
| `POST /profissionais` · `PUT /profissionais/{id}/chave` | `X-Admin-Key` | Cadastra o profissional ou renova sua chave. |
| `PUT /profissionais/{id}/categoria` · `PUT /profissionais/{id}/foto` | `X-Agenda-Key` | Atualiza a categoria em JSON ou envia o campo `foto` via multipart. |
| `GET /profissionais/{id}/disponibilidade` · `PUT /profissionais/{id}/disponibilidade` | `X-Agenda-Key` | Lê ou salva expediente semanal e folgas. |
| `GET /agendamentos/profissional/{id}` | `X-Agenda-Key` | Lista as reservas da agenda em ordem cronológica. |
| `POST /agendamentos` | `Authorization: Bearer <token>` | Recebe `profissionalId` e `dataHora` em ISO local, por exemplo `2030-04-16T14:30`. |
| `GET /cliente/atendimentos` | Bearer de cliente | Lista os atendimentos vinculados à conta autenticada. |
| `GET /cliente/perfil` · `PUT /cliente/perfil` | Bearer de cliente | Consulta o perfil ou envia `nome` e os campos opcionais `telefone`, `cidade` e `foto` via multipart. |

## Confirmação e lembretes por e-mail

1. Defina `N8N_ENCRYPTION_KEY` e `N8N_WEBHOOK_KEY` com valores aleatórios distintos. Mantenha a chave de criptografia estável enquanto usar o volume do n8n.
2. Rode `docker compose --profile automacao up -d n8n mailpit`.
3. Abra o [n8n](http://localhost:5678), crie o usuário local e importe `infra/n8n/confirmacao.json`. No nó **Reserva criada**, configure Header Auth com nome `X-Webhook-Key` e valor igual a `N8N_WEBHOOK_KEY`.
4. No nó **Enviar confirmação**, configure a credencial SMTP. Para teste use host `mailpit`, porta `1025`, sem usuário, senha ou TLS. Publique o workflow.
5. Preencha `N8N_WEBHOOK_URL=http://n8n:5678/webhook/confirmar-agendamento` no `.env` e aplique com `docker compose up -d api`.
6. Reserve um horário novo e abra [Mailpit](http://localhost:8030) para conferir a mensagem. Para entrega externa, configure as credenciais SMTP e o remetente autorizado no nó de e-mail.

Se o workflow antigo já estiver publicado, atualize o nó **Enviar confirmação** com as expressões de assunto e mensagem do arquivo atualizado para distinguir confirmações de lembretes.

A reserva e as entregas ficam no mesmo documento do MongoDB. A API agenda a confirmação para entrega imediata e, para reservas com mais de 24 horas de antecedência, agenda um lembrete 24 horas antes. Com o webhook configurado, a API tenta novamente após falhas, com intervalos crescentes até uma hora, enquanto o atendimento estiver no futuro. Cada entrega mantém o mesmo `Idempotency-Key`. O SMTP pode duplicar a mensagem se aceitá-la e o webhook perder a resposta. Reservas anteriores a esta versão só ganham entregas mediante um novo agendamento.

## Observabilidade

- Rode `docker compose --profile observabilidade up -d` para subir Prometheus e Grafana. Defina `GRAFANA_ADMIN_PASSWORD` no primeiro uso. Abra o [dashboard TempoCerto](http://localhost:3301/d/tempocerto-api).
- Actuator expõe saúde e métricas na porta interna `8081`, usada pelo Prometheus. A porta pública da API atende as rotas de negócio.
- Para Sentry, defina `SENTRY_API_DSN` e `SENTRY_WEB_DSN` para execução, e `NEXT_PUBLIC_SENTRY_DSN` para o build do navegador. Execute novamente `docker compose up --build -d` ao mudar uma variável pública. A API envia eventos de erro sem incluir mensagens do driver ou dados das reservas.

## Testes e desenvolvimento

- **API:** em `api/`, com Java 21, rode `.\mvnw.cmd verify` no Windows ou `bash mvnw verify` no Linux. Para incluir a suíte HTTP com persistência real, defina `RUN_MONGO_TESTS=true` e aponte `MONGODB_URI` para um banco exclusivo de testes. A suíte cria registros nesse banco.
- **Frontend:** em `web/`, com Node.js 24, execute `npm ci`, `npm run typecheck` e `npm run build`. Para desenvolvimento com recarga automática, use `npm run dev` com a API disponível.
- **CI:** a cada push e pull request, o GitHub Actions executa o Maven com MongoDB e verifica a tipagem e o build do Next.js.

## Limites da demonstração

O cadastro em um banco novo começa vazio. O login de cliente por e-mail e o login por Google usam identidades separadas. Para reencontrar reservas anteriores, entre com o mesmo método e a mesma conta. E-mails de confirmação e lembrete dependem de um webhook ativo, um servidor SMTP e da API em execução. Uma API suspensa pode atrasar as entregas. A integração automática com WhatsApp está pendente.
