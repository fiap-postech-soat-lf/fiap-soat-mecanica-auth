package br.com.fiap.soat.mecanica.auth.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Quem fez a requisição, extraído de um token válido.
 *
 * @param sub   UUID do cliente ou do funcionário
 * @param tipo  CLIENTE ou FUNCIONARIO
 * @param cargo cargo do funcionário; {@code null} para CLIENTE
 */
public record Principal(UUID sub, TipoPrincipal tipo, String cargo) {

    public Principal {
        Objects.requireNonNull(sub, "sub");
        Objects.requireNonNull(tipo, "tipo");
    }
}
