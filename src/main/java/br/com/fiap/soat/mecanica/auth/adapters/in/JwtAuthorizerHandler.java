package br.com.fiap.soat.mecanica.auth.adapters.in;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2CustomAuthorizerEvent;
import com.amazonaws.services.lambda.runtime.events.SimpleIAMPolicyResponse;

/**
 * Lambda Authorizer do API Gateway HTTP API (payload 2.0, resposta simples).
 * Stub da F1: nega toda requisição. A validação do JWT RS256 emitido pelo Identity entra na F2.
 */
public final class JwtAuthorizerHandler
        implements RequestHandler<APIGatewayV2CustomAuthorizerEvent, SimpleIAMPolicyResponse> {

    @Override
    public SimpleIAMPolicyResponse handleRequest(APIGatewayV2CustomAuthorizerEvent event, Context context) {
        return SimpleIAMPolicyResponse.builder()
                .withIsAuthorized(false)
                .build();
    }
}
