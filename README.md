# fiap-soat-mecanica-auth

**Lambda Authorizer + API Gateway** da **Fase 4** da Oficina Mecânica do Braia (Tech Challenge FIAP SOAT). O API Gateway (HTTP API) é a porta de entrada pública. O authorizer valida o JWT RS256 emitido pelo serviço Identity antes de encaminhar a requisição ao NLB do EKS.

> 🚧 Em construção (fase F1). Na F1 o `JwtAuthorizerHandler` é um stub que **nega toda requisição**. A validação do JWT e o `infra/` (API Gateway, Lambda) entram na F2.

## Estrutura

| Caminho | Conteúdo |
|---|---|
| `src/main/java/br/com/fiap/soat/mecanica/auth/adapters/in/JwtAuthorizerHandler.java` | Handler do authorizer (payload 2.0, resposta simples `isAuthorized`) |
| `infra/` | *TODO (F2)* Terraform do API Gateway, da Lambda e das rotas |
| `docs/adr/` | Decisões de arquitetura deste repositório |

## Comandos

```bash
./mvnw verify          # testes + JaCoCo (mínimo 80% de linhas) + jar shade em target/auth-lambda.jar
```

Requisitos: Java 21 e Maven Wrapper (incluído). Sem Spring: a Lambda usa só `aws-lambda-java-core`/`-events` e `jjwt`.

## CI

O `ci.yml` chama o reusable `servico-ci.yml@v1` do [`fiap-soat-mecanica-k8s`](https://github.com/fiap-postech-soat-lf/fiap-soat-mecanica-k8s) (check `ci / build`): testes, cobertura e análise no SonarCloud (`fiap-postech-soat-lf_fiap-soat-mecanica-auth`) com espera do Quality Gate.

## Origem

Adaptado do repositório `-auth` da Fase 3 do grupo (usado só para consulta).
