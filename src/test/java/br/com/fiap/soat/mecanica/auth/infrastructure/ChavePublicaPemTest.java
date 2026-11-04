package br.com.fiap.soat.mecanica.auth.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.fiap.soat.mecanica.auth.support.TokensDeTeste;
import org.junit.jupiter.api.Test;

class ChavePublicaPemTest {

    @Test
    void carregaPemComQuebrasDeLinha() {
        assertEquals(TokensDeTeste.chavePublica(), ChavePublicaPem.carregar(TokensDeTeste.chavePublicaPem()));
    }

    @Test
    void carregaPemComBarraNEscapado() {
        String escapado = TokensDeTeste.chavePublicaPem().replace("\n", "\\n");

        assertEquals(TokensDeTeste.chavePublica(), ChavePublicaPem.carregar(escapado));
    }

    @Test
    void vazioViraNull() {
        assertNull(ChavePublicaPem.carregar(""));
        assertNull(ChavePublicaPem.carregar(null));
    }

    @Test
    void rejeitaValorQueNaoEChave() {
        assertThrows(IllegalArgumentException.class,
                () -> ChavePublicaPem.carregar("-----BEGIN PUBLIC KEY-----\nAAAA\n-----END PUBLIC KEY-----"));
        assertThrows(IllegalArgumentException.class, () -> ChavePublicaPem.carregar("isto nao e base64!"));
    }
}
