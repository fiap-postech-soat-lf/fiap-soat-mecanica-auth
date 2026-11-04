package br.com.fiap.soat.mecanica.auth.infrastructure;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Converte a chave pública do Identity (PEM X.509, com quebras reais ou \n escapado) em RSAPublicKey. */
public final class ChavePublicaPem {

    private ChavePublicaPem() {
    }

    /**
     * @return a chave, ou {@code null} se o valor estiver vazio
     * @throws IllegalArgumentException se o valor não for uma chave pública RSA em PEM
     */
    public static RSAPublicKey carregar(String pem) {
        if (pem == null || pem.isBlank()) {
            return null;
        }
        // A variável de ambiente chega com o PEM escapado (replace do Terraform).
        String base64 = pem.replace("\\n", "\n")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
            throw new IllegalArgumentException("JWT_PUBLIC_KEY não é uma chave pública RSA em PEM", e);
        }
    }
}
