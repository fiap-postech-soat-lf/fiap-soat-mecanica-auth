package br.com.fiap.soat.mecanica.auth.application;

import br.com.fiap.soat.mecanica.auth.domain.Principal;
import br.com.fiap.soat.mecanica.auth.domain.TipoPrincipal;
import br.com.fiap.soat.mecanica.auth.domain.TokenInvalidoException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MissingClaimException;
import io.jsonwebtoken.RequiredTypeException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import java.security.interfaces.RSAPublicKey;
import java.util.Set;
import java.util.UUID;

/**
 * Valida o JWT RS256 emitido pelo Identity com a mesma regra do {@code JwtTokenValidator} dos
 * serviços (jwt.md do -k8s), sem Spring: {@code alg} exatamente RS256, assinatura, {@code iss},
 * {@code aud}, {@code exp} com tolerância de 30 s e os claims {@code principal_type}/{@code cargo}.
 */
public final class AutorizadorJwt {

    static final long TOLERANCIA_SEGUNDOS = 30;
    static final Set<String> CARGOS = Set.of("ATENDENTE", "MECANICO", "ALMOXARIFE");

    private final JwtParser parser;

    /**
     * @param chavePublica chave pública do Identity; {@code null} faz todo token ser negado
     *                     com o motivo {@code chave_publica_ausente}
     */
    public AutorizadorJwt(RSAPublicKey chavePublica, String issuer, String audience) {
        this.parser = chavePublica == null ? null : Jwts.parser()
                // O alg é conferido antes da assinatura: RS384/RS512 com a mesma chave também
                // seriam válidos para o jjwt, e o contrato só aceita RS256.
                .keyLocator(header -> {
                    if (!"RS256".equals(header.getAlgorithm())) {
                        throw new TokenInvalidoException("algoritmo_invalido");
                    }
                    return chavePublica;
                })
                .requireIssuer(issuer)
                .requireAudience(audience)
                .clockSkewSeconds(TOLERANCIA_SEGUNDOS)
                .build();
    }

    public Principal autorizar(String token) {
        if (parser == null) {
            throw new TokenInvalidoException("chave_publica_ausente");
        }
        return montarPrincipal(verificar(token));
    }

    private Claims verificar(String token) {
        try {
            return parser.parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            throw new TokenInvalidoException("expirado");
        } catch (SignatureException e) {
            throw new TokenInvalidoException("assinatura_invalida");
        } catch (MissingClaimException | IncorrectClaimException e) {
            throw new TokenInvalidoException(e.getClaimName() + "_invalido");
        } catch (UnsupportedJwtException e) {
            // Token sem assinatura (alg "none") ou que não é um JWS.
            throw new TokenInvalidoException("algoritmo_invalido");
        } catch (JwtException | IllegalArgumentException e) {
            throw new TokenInvalidoException("malformado");
        }
    }

    private static Principal montarPrincipal(Claims claims) {
        UUID sub;
        try {
            sub = UUID.fromString(claims.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new TokenInvalidoException("sub_invalido");
        }

        TipoPrincipal tipo;
        try {
            tipo = TipoPrincipal.valueOf(claims.get("principal_type", String.class));
        } catch (IllegalArgumentException | NullPointerException | RequiredTypeException e) {
            throw new TokenInvalidoException("principal_type_invalido");
        }

        if (tipo == TipoPrincipal.CLIENTE) {
            return new Principal(sub, tipo, null);
        }
        Object cargo = claims.get("cargo");
        if (!(cargo instanceof String c) || !CARGOS.contains(c)) {
            throw new TokenInvalidoException("cargo_invalido");
        }
        return new Principal(sub, tipo, c);
    }
}
