package br.com.fiap.soat.mecanica.auth.adapters.in;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.fiap.soat.mecanica.auth.infrastructure.StructuredLogger;
import br.com.fiap.soat.mecanica.auth.support.TokensDeTeste;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2CustomAuthorizerEvent;
import com.amazonaws.services.lambda.runtime.events.SimpleIAMPolicyResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JwtAuthorizerHandlerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ByteArrayOutputStream saida = new ByteArrayOutputStream();
    private final StructuredLogger logger =
            new StructuredLogger(new PrintStream(saida, true, StandardCharsets.UTF_8));

    @Test
    void autorizaClienteComContextoECargoVazio() {
        UUID sub = UUID.randomUUID();

        var resposta = handlerComChave().handleRequest(
                evento("Bearer " + TokensDeTeste.token().sub(sub.toString()).gerar()), null);

        assertTrue(resposta.getIsAuthorized());
        assertEquals(Map.of("sub", sub.toString(), "principalType", "CLIENTE", "cargo", ""), resposta.getContext());
    }

    @Test
    void autorizaFuncionarioComCargoNoContexto() {
        UUID sub = UUID.randomUUID();
        String token = TokensDeTeste.token().sub(sub.toString()).principalType("FUNCIONARIO").cargo("MECANICO").gerar();

        var resposta = handlerComChave().handleRequest(evento("Bearer " + token), null);

        assertTrue(resposta.getIsAuthorized());
        assertEquals(Map.of("sub", sub.toString(), "principalType", "FUNCIONARIO", "cargo", "MECANICO"),
                resposta.getContext());
    }

    @ParameterizedTest
    @ValueSource(strings = {"bearer ", "BEARER ", "  Bearer   "})
    void prefixoBearerNaoDiferenciaMaiusculas(String prefixo) {
        var resposta = handlerComChave().handleRequest(evento(prefixo + TokensDeTeste.token().gerar()), null);

        assertTrue(resposta.getIsAuthorized());
    }

    @Test
    void semHeaderNega() {
        var evento = APIGatewayV2CustomAuthorizerEvent.builder().withRouteKey("ANY /{proxy+}").build();

        assertNegadoComMotivo(handlerComChave().handleRequest(evento, null), "token_ausente");
    }

    @Test
    void headerVazioNega() {
        assertNegadoComMotivo(handlerComChave().handleRequest(evento("  "), null), "token_ausente");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Basic dXNlcjpzZW5oYQ==", "Bearer", "Bearer    ", "Token abc"})
    void headerSemBearerNega(String valor) {
        assertNegadoComMotivo(handlerComChave().handleRequest(evento(valor), null), "header_malformado");
    }

    @Test
    void tokenMalformadoNega() {
        assertNegadoComMotivo(handlerComChave().handleRequest(evento("Bearer qualquer.coisa.aqui"), null),
                "malformado");
    }

    @Test
    void tokenExpiradoNega() {
        String token = TokensDeTeste.token().expiraEm(Instant.now().minusSeconds(120)).gerar();

        assertNegadoComMotivo(handlerComChave().handleRequest(evento("Bearer " + token), null), "expirado");
    }

    @Test
    void logDaNegacaoTemMotivoRotaECorrelationIdESemToken() throws Exception {
        String token = TokensDeTeste.token().assinadoComOutraChave().gerar();

        handlerComChave().handleRequest(evento("Bearer " + token), null);

        JsonNode log = ultimaLinhaDeLog();
        assertEquals("autorizacao_negada", log.get("event").asText());
        assertEquals("assinatura_invalida", log.get("motivo").asText());
        assertEquals("GET /health-echo", log.get("routeKey").asText());
        assertEquals("req-123", log.get("correlationId").asText());
        assertFalse(saida.toString(StandardCharsets.UTF_8).contains(token), "o token nunca vai para o log");
    }

    @Test
    void eventoSemRequestContextLogaCorrelationIdVazio() throws Exception {
        var evento = APIGatewayV2CustomAuthorizerEvent.builder().build();

        handlerComChave().handleRequest(evento, null);

        JsonNode log = ultimaLinhaDeLog();
        assertEquals("", log.get("correlationId").asText());
        assertEquals("", log.get("routeKey").asText());
    }

    @Test
    void semChaveInicializaENegaTudo() throws Exception {
        var handler = new JwtAuthorizerHandler(Map.of(), logger);

        assertEquals("chave_publica_ausente", primeiraLinhaDeLog().get("event").asText());
        assertEquals("JWT_PUBLIC_KEY vazio", primeiraLinhaDeLog().get("detalhe").asText());
        assertNegadoComMotivo(handler.handleRequest(evento("Bearer " + TokensDeTeste.token().gerar()), null),
                "chave_publica_ausente");
    }

    @Test
    void chaveInvalidaInicializaENegaTudo() throws Exception {
        var handler = new JwtAuthorizerHandler(Map.of("JWT_PUBLIC_KEY", "nao-e-pem"), logger);

        assertEquals("JWT_PUBLIC_KEY inválido", primeiraLinhaDeLog().get("detalhe").asText());
        assertNegadoComMotivo(handler.handleRequest(evento("Bearer " + TokensDeTeste.token().gerar()), null),
                "chave_publica_ausente");
    }

    @Test
    void aceitaPemEscapadoNaVariavelDeAmbiente() {
        var env = Map.of("JWT_PUBLIC_KEY", TokensDeTeste.chavePublicaPem().replace("\n", "\\n"));
        var handler = new JwtAuthorizerHandler(env, logger);

        assertTrue(handler.handleRequest(evento("Bearer " + TokensDeTeste.token().gerar()), null).getIsAuthorized());
    }

    @Test
    void construtorPublicoLeOAmbienteDoProcesso() throws Exception {
        Constructor<JwtAuthorizerHandler> construtor = JwtAuthorizerHandler.class.getConstructor();

        // Sem JWT_PUBLIC_KEY no ambiente dos testes: inicializa e nega.
        assertFalse(construtor.newInstance().handleRequest(evento("Bearer x.y.z"), null).getIsAuthorized());
    }

    private JwtAuthorizerHandler handlerComChave() {
        Map<String, String> env = new HashMap<>();
        env.put("JWT_PUBLIC_KEY", TokensDeTeste.chavePublicaPem());
        env.put("JWT_ISSUER", TokensDeTeste.ISSUER);
        env.put("JWT_AUDIENCE", TokensDeTeste.AUDIENCE);
        return new JwtAuthorizerHandler(env, logger);
    }

    private static APIGatewayV2CustomAuthorizerEvent evento(String authorization) {
        return APIGatewayV2CustomAuthorizerEvent.builder()
                .withRouteKey("GET /health-echo")
                .withHeaders(Map.of("authorization", authorization))
                .withRequestContext(APIGatewayV2CustomAuthorizerEvent.RequestContext.builder()
                        .withRequestId("req-123")
                        .build())
                .build();
    }

    private void assertNegadoComMotivo(SimpleIAMPolicyResponse resposta, String motivo) {
        assertFalse(resposta.getIsAuthorized());
        assertNull(resposta.getContext());
        try {
            assertEquals(motivo, ultimaLinhaDeLog().get("motivo").asText());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private List<String> linhas() {
        return saida.toString(StandardCharsets.UTF_8).lines().toList();
    }

    private JsonNode ultimaLinhaDeLog() throws Exception {
        List<String> linhas = linhas();
        return JSON.readTree(linhas.get(linhas.size() - 1));
    }

    private JsonNode primeiraLinhaDeLog() throws Exception {
        return JSON.readTree(linhas().get(0));
    }
}
