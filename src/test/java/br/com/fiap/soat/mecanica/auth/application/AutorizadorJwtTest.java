package br.com.fiap.soat.mecanica.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.fiap.soat.mecanica.auth.domain.Principal;
import br.com.fiap.soat.mecanica.auth.domain.TipoPrincipal;
import br.com.fiap.soat.mecanica.auth.domain.TokenInvalidoException;
import br.com.fiap.soat.mecanica.auth.support.TokensDeTeste;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AutorizadorJwtTest {

    private final AutorizadorJwt autorizador =
            new AutorizadorJwt(TokensDeTeste.chavePublica(), TokensDeTeste.ISSUER, TokensDeTeste.AUDIENCE);

    @Test
    void aceitaClienteSemCargo() {
        UUID sub = UUID.randomUUID();

        Principal principal = autorizador.autorizar(TokensDeTeste.token().sub(sub.toString()).gerar());

        assertEquals(new Principal(sub, TipoPrincipal.CLIENTE, null), principal);
    }

    @Test
    void ignoraCargoDeCliente() {
        Principal principal = autorizador.autorizar(TokensDeTeste.token().cargo("MECANICO").gerar());

        assertNull(principal.cargo());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ATENDENTE", "MECANICO", "ALMOXARIFE"})
    void aceitaFuncionarioComCargoValido(String cargo) {
        UUID sub = UUID.randomUUID();

        Principal principal = autorizador.autorizar(TokensDeTeste.token()
                .sub(sub.toString()).principalType("FUNCIONARIO").cargo(cargo).gerar());

        assertEquals(new Principal(sub, TipoPrincipal.FUNCIONARIO, cargo), principal);
    }

    @Test
    void aceitaTokenVencidoDentroDaTolerancia() {
        String token = TokensDeTeste.token().expiraEm(Instant.now().minusSeconds(10)).gerar();

        assertEquals(TipoPrincipal.CLIENTE, autorizador.autorizar(token).tipo());
    }

    @Test
    void negaTokenVencidoAlemDaTolerancia() {
        String token = TokensDeTeste.token().expiraEm(Instant.now().minusSeconds(60)).gerar();

        assertMotivo("expirado", token);
    }

    @Test
    void negaIssuerErrado() {
        assertMotivo("iss_invalido", TokensDeTeste.token().issuer("outro-emissor").gerar());
    }

    @Test
    void negaAudienceErrada() {
        assertMotivo("aud_invalido", TokensDeTeste.token().audience("outra-api").gerar());
    }

    @Test
    void negaAssinaturaDeOutraChave() {
        assertMotivo("assinatura_invalida", TokensDeTeste.token().assinadoComOutraChave().gerar());
    }

    @Test
    void negaRs512MesmoComAChaveCerta() {
        assertMotivo("algoritmo_invalido", TokensDeTeste.token().rs512().gerar());
    }

    @Test
    void negaHs256() {
        assertMotivo("algoritmo_invalido", TokensDeTeste.token().hs256().gerar());
    }

    @Test
    void negaAlgNone() {
        assertMotivo("algoritmo_invalido", TokensDeTeste.token().semAssinatura().gerar());
    }

    @ParameterizedTest
    @ValueSource(strings = {"nao-e-um-jwt", "a.b.c", "   "})
    void negaTokenMalformado(String token) {
        assertMotivo("malformado", token);
    }

    @Test
    void negaSubQueNaoEUuid() {
        assertMotivo("sub_invalido", TokensDeTeste.token().sub("12345").gerar());
    }

    @Test
    void negaPrincipalTypeAusente() {
        assertMotivo("principal_type_invalido", TokensDeTeste.token().principalType(null).gerar());
    }

    @Test
    void negaPrincipalTypeDesconhecido() {
        assertMotivo("principal_type_invalido", TokensDeTeste.token().principalType("ADMIN").gerar());
    }

    @Test
    void negaFuncionarioSemCargo() {
        assertMotivo("cargo_invalido", TokensDeTeste.token().principalType("FUNCIONARIO").gerar());
    }

    @Test
    void negaFuncionarioComCargoInvalido() {
        assertMotivo("cargo_invalido",
                TokensDeTeste.token().principalType("FUNCIONARIO").cargo("GERENTE").gerar());
    }

    @Test
    void negaFuncionarioComCargoQueNaoETexto() {
        assertMotivo("cargo_invalido",
                TokensDeTeste.token().principalType("FUNCIONARIO").cargo(42).gerar());
    }

    @Test
    void semChaveNegaTudo() {
        var semChave = new AutorizadorJwt(null, TokensDeTeste.ISSUER, TokensDeTeste.AUDIENCE);

        var erro = assertThrows(TokenInvalidoException.class,
                () -> semChave.autorizar(TokensDeTeste.token().gerar()));
        assertEquals("chave_publica_ausente", erro.motivo());
    }

    private void assertMotivo(String motivo, String token) {
        var erro = assertThrows(TokenInvalidoException.class, () -> autorizador.autorizar(token));
        assertEquals(motivo, erro.motivo());
    }
}
