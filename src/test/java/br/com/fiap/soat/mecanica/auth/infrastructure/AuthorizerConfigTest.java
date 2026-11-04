package br.com.fiap.soat.mecanica.auth.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthorizerConfigTest {

    @Test
    void leAsVariaveisDeAmbiente() {
        var config = AuthorizerConfig.doAmbiente(Map.of(
                "JWT_PUBLIC_KEY", "pem", "JWT_ISSUER", "emissor", "JWT_AUDIENCE", "api"));

        assertEquals(new AuthorizerConfig("pem", "emissor", "api"), config);
    }

    @Test
    void usaOsValoresDoContratoQuandoAusentesOuVazios() {
        var config = AuthorizerConfig.doAmbiente(Map.of("JWT_ISSUER", " "));

        assertEquals(new AuthorizerConfig("", "fiap-soat-mecanica-identity", "fiap-soat-mecanica"), config);
    }
}
