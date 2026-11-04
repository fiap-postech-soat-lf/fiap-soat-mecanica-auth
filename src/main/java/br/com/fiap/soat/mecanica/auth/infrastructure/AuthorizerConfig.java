package br.com.fiap.soat.mecanica.auth.infrastructure;

import java.util.Map;

/**
 * Configuração do authorizer lida das variáveis de ambiente da Lambda (definidas pelo infra/).
 *
 * @param chavePublicaPem {@code JWT_PUBLIC_KEY}: PEM ou PEM com \n escapado; vazio se ausente
 * @param issuer          {@code JWT_ISSUER} (padrão: o iss do contrato)
 * @param audience        {@code JWT_AUDIENCE} (padrão: o aud do contrato)
 */
public record AuthorizerConfig(String chavePublicaPem, String issuer, String audience) {

    static final String ISSUER_PADRAO = "fiap-soat-mecanica-identity";
    static final String AUDIENCE_PADRAO = "fiap-soat-mecanica";

    public static AuthorizerConfig doAmbiente(Map<String, String> env) {
        return new AuthorizerConfig(
                env.getOrDefault("JWT_PUBLIC_KEY", ""),
                valorOuPadrao(env.get("JWT_ISSUER"), ISSUER_PADRAO),
                valorOuPadrao(env.get("JWT_AUDIENCE"), AUDIENCE_PADRAO));
    }

    private static String valorOuPadrao(String valor, String padrao) {
        return valor == null || valor.isBlank() ? padrao : valor;
    }
}
