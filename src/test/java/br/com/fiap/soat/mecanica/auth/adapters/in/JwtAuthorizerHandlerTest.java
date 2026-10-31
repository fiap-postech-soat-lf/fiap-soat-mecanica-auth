package br.com.fiap.soat.mecanica.auth.adapters.in;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2CustomAuthorizerEvent;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JwtAuthorizerHandlerTest {

    private final JwtAuthorizerHandler handler = new JwtAuthorizerHandler();

    @Test
    void negaRequisicaoSemToken() {
        var event = APIGatewayV2CustomAuthorizerEvent.builder().build();

        assertFalse(handler.handleRequest(event, null).getIsAuthorized());
    }

    @Test
    void negaRequisicaoComTokenEnquantoAValidacaoNaoExiste() {
        var event = APIGatewayV2CustomAuthorizerEvent.builder()
                .withHeaders(Map.of("authorization", "Bearer qualquer.coisa.aqui"))
                .build();

        assertFalse(handler.handleRequest(event, null).getIsAuthorized());
    }
}
