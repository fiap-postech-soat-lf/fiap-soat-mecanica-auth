package br.com.fiap.soat.mecanica.auth.support;

import io.jsonwebtoken.Jwts;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;

/**
 * Tokens JWT para os testes, assinados com pares RSA gerados em memória (nenhuma chave
 * versionada). Os padrões seguem o contrato jwt.md do -k8s.
 */
public final class TokensDeTeste {

    public static final String ISSUER = "fiap-soat-mecanica-identity";
    public static final String AUDIENCE = "fiap-soat-mecanica";

    private static final KeyPair PAR = gerarPar();
    private static final KeyPair OUTRO_PAR = gerarPar();

    private TokensDeTeste() {
    }

    public static RSAPublicKey chavePublica() {
        return (RSAPublicKey) PAR.getPublic();
    }

    public static String chavePublicaPem() {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(PAR.getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----\n";
    }

    public static Builder token() {
        return new Builder();
    }

    public static final class Builder {

        private String sub = UUID.randomUUID().toString();
        private String principalType = "CLIENTE";
        private Object cargo;
        private String issuer = ISSUER;
        private String audience = AUDIENCE;
        private Instant expiraEm = Instant.now().plus(Duration.ofHours(1));
        private Key chave = PAR.getPrivate();
        private boolean rs512;
        private boolean semAssinatura;

        public Builder sub(String sub) {
            this.sub = sub;
            return this;
        }

        public Builder principalType(String principalType) {
            this.principalType = principalType;
            return this;
        }

        public Builder cargo(Object cargo) {
            this.cargo = cargo;
            return this;
        }

        public Builder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        public Builder audience(String audience) {
            this.audience = audience;
            return this;
        }

        public Builder expiraEm(Instant expiraEm) {
            this.expiraEm = expiraEm;
            return this;
        }

        public Builder assinadoComOutraChave() {
            this.chave = OUTRO_PAR.getPrivate();
            return this;
        }

        /** Mesma chave RSA, algoritmo RS512 (válido para o jjwt, proibido pelo contrato). */
        public Builder rs512() {
            this.rs512 = true;
            return this;
        }

        /** Chave HMAC de 256 bits: o jjwt assina com HS256. */
        public Builder hs256() {
            this.chave = new SecretKeySpec(new byte[32], "HmacSHA256");
            return this;
        }

        public Builder semAssinatura() {
            this.semAssinatura = true;
            return this;
        }

        public String gerar() {
            var builder = Jwts.builder()
                    .header().keyId("mecanica-identity-v1").and()
                    .issuer(issuer)
                    .audience().add(audience).and()
                    .subject(sub)
                    .id(UUID.randomUUID().toString())
                    .issuedAt(Date.from(expiraEm.minus(Duration.ofHours(1))))
                    .expiration(Date.from(expiraEm));
            if (principalType != null) {
                builder.claim("principal_type", principalType);
            }
            if (cargo != null) {
                builder.claim("cargo", cargo);
            }
            if (semAssinatura) {
                return builder.compact();
            }
            if (rs512) {
                return builder.signWith((PrivateKey) chave, Jwts.SIG.RS512).compact();
            }
            // RSA 2048 -> RS256; HMAC de 256 bits -> HS256.
            return builder.signWith(chave).compact();
        }
    }

    private static KeyPair gerarPar() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
