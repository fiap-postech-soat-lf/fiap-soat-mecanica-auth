package br.com.fiap.soat.mecanica.auth.domain;

/**
 * Token rejeitado. O {@link #motivo()} (ex.: assinatura_invalida, expirado, iss_invalido) vai
 * para o log; o token, nunca.
 */
public class TokenInvalidoException extends RuntimeException {

    private final String motivo;

    public TokenInvalidoException(String motivo) {
        super(motivo);
        this.motivo = motivo;
    }

    public String motivo() {
        return motivo;
    }
}
