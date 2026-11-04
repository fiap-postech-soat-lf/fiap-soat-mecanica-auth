package br.com.fiap.soat.mecanica.auth.adapters.in;

import br.com.fiap.soat.mecanica.auth.application.AutorizadorJwt;
import br.com.fiap.soat.mecanica.auth.domain.Principal;
import br.com.fiap.soat.mecanica.auth.domain.TokenInvalidoException;
import br.com.fiap.soat.mecanica.auth.infrastructure.AuthorizerConfig;
import br.com.fiap.soat.mecanica.auth.infrastructure.ChavePublicaPem;
import br.com.fiap.soat.mecanica.auth.infrastructure.StructuredLogger;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2CustomAuthorizerEvent;
import com.amazonaws.services.lambda.runtime.events.SimpleIAMPolicyResponse;
import java.security.interfaces.RSAPublicKey;
import java.util.Locale;
import java.util.Map;

/**
 * Lambda Authorizer do API Gateway HTTP API (payload 2.0, resposta simples).
 * Aceito: {@code isAuthorized=true} com o contexto {@code {sub, principalType, cargo}}, que o
 * API Gateway repassa ao backend como {@code x-user-*}. Negado: {@code isAuthorized=false} (403)
 * e log {@code autorizacao_negada} com o motivo, nunca o token.
 */
public final class JwtAuthorizerHandler
        implements RequestHandler<APIGatewayV2CustomAuthorizerEvent, SimpleIAMPolicyResponse> {

    private static final String PREFIXO_BEARER = "bearer ";

    private final AutorizadorJwt autorizador;
    private final StructuredLogger logger;

    /** Usado pelo runtime da Lambda: lê o ambiente uma vez por container. */
    public JwtAuthorizerHandler() {
        this(System.getenv(), new StructuredLogger());
    }

    JwtAuthorizerHandler(Map<String, String> env, StructuredLogger logger) {
        this.logger = logger;
        AuthorizerConfig config = AuthorizerConfig.doAmbiente(env);
        this.autorizador = new AutorizadorJwt(carregarChave(config.chavePublicaPem()),
                config.issuer(), config.audience());
    }

    @Override
    public SimpleIAMPolicyResponse handleRequest(APIGatewayV2CustomAuthorizerEvent event, Context context) {
        try {
            Principal principal = autorizador.autorizar(extrairToken(event.getHeaders()));
            return SimpleIAMPolicyResponse.builder()
                    .withIsAuthorized(true)
                    .withContext(Map.of(
                            "sub", principal.sub().toString(),
                            "principalType", principal.tipo().name(),
                            // O contexto não aceita null: CLIENTE vai com cargo vazio.
                            "cargo", principal.cargo() == null ? "" : principal.cargo()))
                    .build();
        } catch (TokenInvalidoException e) {
            logger.info("autorizacao_negada", requestId(event), Map.of(
                    "motivo", e.motivo(),
                    "routeKey", valorOuVazio(event.getRouteKey())));
            return SimpleIAMPolicyResponse.builder().withIsAuthorized(false).build();
        }
    }

    private RSAPublicKey carregarChave(String pem) {
        try {
            RSAPublicKey chave = ChavePublicaPem.carregar(pem);
            if (chave == null) {
                logger.warn("chave_publica_ausente", "", Map.of("detalhe", "JWT_PUBLIC_KEY vazio"));
            }
            return chave;
        } catch (IllegalArgumentException e) {
            logger.warn("chave_publica_ausente", "", Map.of("detalhe", "JWT_PUBLIC_KEY inválido"));
            return null;
        }
    }

    /** O HTTP API entrega os headers em minúsculas; o prefixo Bearer não diferencia maiúsculas. */
    private static String extrairToken(Map<String, String> headers) {
        String valor = headers == null ? null : headers.get("authorization");
        if (valor == null || valor.isBlank()) {
            throw new TokenInvalidoException("token_ausente");
        }
        String semEspacos = valor.strip();
        if (!semEspacos.toLowerCase(Locale.ROOT).startsWith(PREFIXO_BEARER)) {
            throw new TokenInvalidoException("header_malformado");
        }
        String token = semEspacos.substring(PREFIXO_BEARER.length()).strip();
        if (token.isEmpty()) {
            throw new TokenInvalidoException("header_malformado");
        }
        return token;
    }

    private static String requestId(APIGatewayV2CustomAuthorizerEvent event) {
        return event.getRequestContext() == null ? "" : valorOuVazio(event.getRequestContext().getRequestId());
    }

    private static String valorOuVazio(String valor) {
        return valor == null ? "" : valor;
    }
}
