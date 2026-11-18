# TempoCerto

O TempoCerto recebe reservas públicas e oferece uma agenda privada por profissional. O frontend usa Next.js, TypeScript e Tailwind. A API usa Java 21, Spring Boot e MongoDB.

## Categorias e acesso com Google

O profissional pode editar sua categoria após entrar na agenda. O cadastro administrativo aceita o campo opcional `categoria`. Na página de reserva, o cliente pode filtrar os profissionais por categoria. Perfis antigos aparecem em **Outros** até receberem uma categoria.

Para ativar **Entrar com Google**, configure a tela de consentimento no Google Auth Platform e crie um cliente OAuth do tipo **Aplicativo da Web**. Em **Origens JavaScript autorizadas**, adicione `http://localhost:3000` e, quando publicar, a origem HTTPS exata do frontend. Para um aplicativo em teste, inclua a conta usada para validar o acesso entre os usuários de teste.

No Compose, coloque o ID público do cliente em `GOOGLE_CLIENT_ID` no arquivo `.env` e execute `docker compose up --build -d`. Na Vercel, configure `NEXT_PUBLIC_GOOGLE_CLIENT_ID` antes do build. Na API hospedada, configure `GOOGLE_CLIENT_ID` com o mesmo valor. O fluxo usa o botão Google Identity Services e exige apenas o ID público. [Configuração do cliente Google](https://developers.google.com/identity/gsi/web/guides/get-google-api-clientid)

O e-mail da conta deve corresponder a um único profissional cadastrado. O primeiro vínculo aceita contas Gmail ou Google Workspace verificadas. Depois, a API também confere o identificador Google da conta. Cada login cria uma sessão válida até o vencimento do token Google e substitui a sessão Google anterior desse profissional. A chave de agenda continua disponível para acesso manual.

## O que havia no projeto

O Compose inicial subia MongoDB com volume persistente. A API já definia os modelos, repositórios e controllers de profissionais e agendamentos. O mapeamento de `POST /agendamentos` já estava no pacote da aplicação. A configuração do URI usava a propriedade do Spring Boot 3, embora o projeto usasse Spring Boot 4.

## Como rodar

Requisitos para o ambiente completo: Docker Desktop e Docker Compose.

1. Copie `.env.example` para `.env`. Defina `ADMIN_KEY` com uma chave aleatória de pelo menos 32 bytes. Por exemplo, gere uma com `node -e "console.log(require('node:crypto').randomBytes(32).toString('base64url'))"`.
2. Rode `docker compose up --build -d`.
3. Cadastre um profissional com a chave administrativa. No PowerShell, com a chave definida também em `$env:ADMIN_KEY`:

   ```powershell
   $profissional = Invoke-RestMethod http://localhost:8080/profissionais `
     -Method Post `
     -Headers @{'X-Admin-Key' = $env:ADMIN_KEY} `
     -ContentType 'application/json' `
     -Body '{"nome":"Ana Souza","email":"ana@example.com"}'
   $profissional | Select-Object id, nome, chaveAgenda
   ```

4. Em [Agendamento](http://localhost:3000), selecione o profissional e reserve um horário futuro de Brasília. Em [Agenda profissional](http://localhost:3000/agenda), use o `id` e a `chaveAgenda` retornados no cadastro.

O cadastro retorna a chave da agenda uma vez. A API armazena seu hash. Para emitir uma nova chave e invalidar a anterior, use `PUT /profissionais/{id}/chave` com `X-Admin-Key`. Esse endpoint também habilita o acesso dos profissionais cadastrados na versão anterior.

Depois de consultar a agenda, salve o expediente semanal e as folgas na seção **Disponibilidade**. A página pública passa a exibir horários livres em blocos de 30 minutos, no horário de Brasília, para os próximos 365 dias. O cadastro antigo aceita o horário digitado até o profissional salvar o primeiro expediente. Salvar dias vazios fecha a agenda para novas reservas. As reservas antigas continuam na agenda.

O Compose local usa o banco `test`. A versão inicial do Spring Boot gravou os registros existentes nesse banco. Para uma instalação nova, `MONGO_DATABASE` permite escolher outro nome. Se alterar a porta da API ou do frontend, ajuste também `NEXT_PUBLIC_API_URL` ou `FRONTEND_ORIGIN`, respectivamente.

### API

| Método e rota | Resultado |
| --- | --- |
| `GET /profissionais` | Lista identificadores e nomes públicos. |
| `POST /profissionais` | Cria um perfil com a chave administrativa e retorna a chave da agenda. |
| `POST /agendamentos` | Persiste a reserva e retorna HTTP 200. Recebe `profissionalId`, `nomeCliente`, `emailCliente` e `dataHora` em ISO local, como `2030-04-16T14:30`. |
| `GET /agendamentos/profissional/{id}` | Lista reservas em ordem cronológica com a credencial `X-Agenda-Key` daquele profissional. |
| `GET /profissionais/{id}/horarios?data=AAAA-MM-DD` | Retorna se há expediente configurado e quais horários estão livres na data. |
| `GET /profissionais/{id}/disponibilidade` | Consulta o expediente e as folgas com `X-Agenda-Key`. |
| `PUT /profissionais/{id}/disponibilidade` | Salva o expediente e as folgas com `X-Agenda-Key`. |

Um índice único no MongoDB protege cada par de profissional e horário mesmo sob concorrência. Horários duplicados retornam 409, dados inválidos retornam 400 e profissionais inexistentes na reserva retornam 404. A versão atual usa horários escolhidos pelo cliente, com precisão de minutos. Caso um volume antigo já contenha reservas duplicadas, faça backup e resolva as duplicatas antes de iniciar a nova API. O MongoDB recusa a criação do índice enquanto houver duplicatas.

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

## Testes e produção

- Em `api/`, rode `./mvnw.cmd verify` no Windows ou `bash mvnw verify` em Linux. Para executar também o teste HTTP com MongoDB, configure `RUN_MONGO_TESTS=true` e `MONGODB_URI` apontando para um banco de testes isolado. O GitHub Actions executa esse cenário e valida o frontend a cada push e pull request.
- Em `web/`, rode `npm ci`, `npm run typecheck` e `npm run build`. Use `npm run dev` para desenvolvimento fora do container.
- O Compose padrão é local. Para produção, execute a imagem da API com `SPRING_PROFILES_ACTIVE=prod`, `ADMIN_KEY`, `FRONTEND_ORIGIN` e um `MONGODB_URI` autenticado. Provisione um usuário de aplicação com papel `readWrite` apenas no banco `tempo-certo`. Use TLS no frontend, na API e na conexão com MongoDB. Exemplo de formato: `mongodb://app:<senha-codificada>@<host>/tempo-certo?authSource=tempo-certo&tls=true`.
- O bundle web precisa de `NEXT_PUBLIC_API_URL` apontando para a URL pública HTTPS da API no build. O navegador acessa a API via CORS com a origem configurada.

### Demonstração pública em planos gratuitos

1. Envie o código para um repositório pessoal no GitHub para ativar o Actions e conectar os serviços.
2. Crie um cluster **Free** e um usuário de aplicação no MongoDB Atlas. Use um banco novo e habilite acesso apenas a partir dos endereços de saída do serviço da API.
3. Importe o repositório como Blueprint no Render e confirme que o serviço usa o plano **Free**. Preencha `MONGODB_URI`, `ADMIN_KEY` e `FRONTEND_ORIGIN`. Pode usar `https://example.invalid` temporariamente até obter a URL da Vercel.
4. Importe o mesmo repositório na Vercel, selecione **Hobby**, configure **Root Directory** como `web` e defina `NEXT_PUBLIC_API_URL` com a URL HTTPS do Render. Defina `FRONTEND_ORIGIN` no Render com a URL publicada pela Vercel, sem barra final.
5. Cadastre dados fictícios para validar a demonstração. Para testar os e-mails e lembretes, use o Compose local. O Render Free suspende a API por inatividade, portanto as entregas podem atrasar enquanto o serviço estiver suspenso.

O plano Hobby da Vercel é para uso pessoal e não comercial. WhatsApp automático está pendente. A cobrança da plataforma depende da categoria e do contexto de entrega, incluindo lembretes enviados fora da janela de atendimento. [Vercel Hobby](https://vercel.com/docs/plans/hobby) · [Render Free](https://render.com/docs/free) · [Atlas Free](https://www.mongodb.com/docs/atlas/reference/free-shared-limitations/) · [Preços do WhatsApp](https://business.whatsapp.com/products/platform-pricing)

Para criar o usuário da aplicação em uma instância MongoDB com autenticação habilitada, execute no `mongosh` com uma conta administrativa:

```javascript
db.getSiblingDB("tempo-certo").createUser({
  user: "app",
  pwd: passwordPrompt(),
  roles: [{ role: "readWrite", db: "tempo-certo" }],
})
```
