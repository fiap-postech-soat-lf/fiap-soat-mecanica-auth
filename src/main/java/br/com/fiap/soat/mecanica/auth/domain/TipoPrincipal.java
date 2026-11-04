package br.com.fiap.soat.mecanica.auth.domain;

/** Valores aceitos no claim {@code principal_type} (contrato jwt.md do -k8s). */
public enum TipoPrincipal {
    CLIENTE,
    FUNCIONARIO
}
