# fiap-soat-mecanica-auth

**Lambda Authorizer + API Gateway** da **Fase 4** da Oficina Mecânica do Braia (Tech Challenge FIAP SOAT). O API Gateway (HTTP API) é a porta de entrada pública. O authorizer valida o JWT RS256 emitido pelo serviço Identity antes de encaminhar a requisição ao NLB do EKS.

> 🚧 Em construção (fase F2). Authorizer, API Gateway e CD prontos; o primeiro deploy real é na janela de integração da F2 (D7).

## Estrutura

| Caminho | Conteúdo |
|---|---|
| `src/main/java/.../auth/adapters/in/JwtAuthorizerHandler.java` | Handler do authorizer (payload 2.0, resposta simples `isAuthorized`): extrai o token, decide e loga a negação |
| `src/main/java/.../auth/application/AutorizadorJwt.java` | Validação do token (mesma regra do `JwtTokenValidator` dos serviços) |
| `src/main/java/.../auth/domain/` | `Principal`, `TipoPrincipal` e `TokenInvalidoException` (com o motivo da negação) |
| `src/main/java/.../auth/infrastructure/` | Configuração pelo ambiente, leitura do PEM e log estruturado em JSON |
| `infra/` | Terraform da Lambda `mecanica-authorizer`, da HTTP API `mecanica-api` (stage `$default`, authorizer `mecanica-jwt`, integrações e rotas) e do SSM `/mecanica/auth/api-url`. Testes em `infra/tests/` com o provider mockado |
| `.github/workflows/cd.yml` | CD próprio (o `servico-cd` do `-k8s` é específico de Kubernetes) |
| `docs/adr/` | Decisões de arquitetura deste repositório |

## Regra de autorização

O token vem do header `authorization` (`Bearer `, sem diferenciar maiúsculas) e segue o contrato `docs/contratos/jwt.md` do `-k8s`:

- `alg` exatamente `RS256` (RS384, RS512, HS256 e `none` são negados);
- assinatura com a chave pública do Identity;
- `iss = fiap-soat-mecanica-identity`, `aud` contém `fiap-soat-mecanica`, `exp` com tolerância de 30 s;
- `sub` UUID; `principal_type` `CLIENTE` ou `FUNCIONARIO`; `FUNCIONARIO` exige `cargo` em `ATENDENTE`, `MECANICO` ou `ALMOXARIFE`.

Aceito: `isAuthorized=true` com o contexto `{sub, principalType, cargo}` (`cargo` vazio para cliente), que o API Gateway repassa como `x-user-*`. Negado: `isAuthorized=false` (o API Gateway responde **403**) e uma linha de log `autorizacao_negada` com `motivo`, `routeKey` e `correlationId` (o `requestId` do API Gateway), nunca o token. Sem token, o API Gateway responde **401** sem chamar a Lambda.

| Variável | Conteúdo |
|---|---|
| `JWT_PUBLIC_KEY` | Chave pública em PEM (aceita `\n` escapado). Ausente ou inválida: a Lambda sobe, loga `chave_publica_ausente` e nega tudo |
| `JWT_ISSUER` / `JWT_AUDIENCE` | Padrão: os valores do contrato |

## API Gateway

HTTP API pública → NLB do `-k8s` (HTTP) → Traefik. O DNS do NLB e a chave pública do Identity vêm do SSM (`/mecanica/k8s/nlb-dns` e `/mecanica/identity/jwt-public-key`); a Lambda fica fora da VPC, com a `LabRole`.

| Rotas | Authorizer | Integração |
|---|---|---|
| `POST /auth/login`, `POST /auth/cpf`, `GET /ordem-servicos/veiculo/placa/{placa}`, `POST /webhooks/mercadopago` e, para cada serviço, `GET /<svc>/actuator/health[/...]`, `/swagger-ui.html`, `/swagger-ui/...`, `/v3/api-docs[/...]` (28 rotas) | não | `publica`: remove os `x-user-*` vindos do cliente |
| `ANY /{proxy+}` (todo o resto) | `mecanica-jwt` (cache de 300 s por header `Authorization`) | `protegida`: preenche `x-user-sub`, `x-user-principal-type` e `x-user-cargo` com o contexto do authorizer |

As duas integrações repassam o caminho e mandam `x-correlation-id = $context.requestId`. Sem token: **401**; token negado: **403**.

## CD

`cd.yml`: depois do CI verde na `main` (ou por `workflow_dispatch`). O porteiro termina verde e **pula** a borda se faltar `/mecanica/k8s/nlb-dns` ou `/mecanica/identity/jwt-public-key` no SSM (EKS desligado ou Identity sem infra). Com o ambiente ligado: `./mvnw package` → `terraform apply` (key `fiap-soat-mecanica-auth/terraform.tfstate`) → smoke `GET <api>/health-echo` sem token = 401. O `scripts/subir-ambiente.sh` do `-k8s` dispara este CD depois do Identity.

## Comandos

```bash
./mvnw verify          # testes + JaCoCo (mínimo 80% de linhas) + jar shade em target/auth-lambda.jar
terraform -chdir=infra init -backend=false && terraform -chdir=infra test   # provider mockado, sem credencial
```

Requisitos: Java 21 e Maven Wrapper (incluído). Sem Spring: a Lambda usa só `aws-lambda-java-core`/`-events` e `jjwt`.

## CI

O `ci.yml` chama o reusable `servico-ci.yml@v1` do [`fiap-soat-mecanica-k8s`](https://github.com/fiap-postech-soat-lf/fiap-soat-mecanica-k8s) (check `ci / build`): testes, cobertura e análise no SonarCloud (`fiap-postech-soat-lf_fiap-soat-mecanica-auth`) com espera do Quality Gate.

## Origem

Adaptado do repositório `-auth` da Fase 3 do grupo (usado só para consulta).
