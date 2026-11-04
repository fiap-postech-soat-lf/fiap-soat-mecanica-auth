package br.com.fiap.soat.mecanica.auth.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StructuredLoggerTest {

    private final ByteArrayOutputStream saida = new ByteArrayOutputStream();

    @Test
    void escreveUmaLinhaJsonPorEvento() throws Exception {
        var logger = new StructuredLogger(new ObjectMapper(), new PrintStream(saida, true, StandardCharsets.UTF_8));

        logger.warn("evento_teste", "req-1", Map.of("motivo", "x"));

        var linha = new ObjectMapper().readTree(saida.toString(StandardCharsets.UTF_8));
        assertEquals("WARN", linha.get("level").asText());
        assertEquals("evento_teste", linha.get("event").asText());
        assertEquals("req-1", linha.get("correlationId").asText());
        assertEquals("x", linha.get("motivo").asText());
    }

    @Test
    void falhaDeSerializacaoViraLogDeErro() {
        var mapperQuebrado = new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws JsonProcessingException {
                throw JsonMappingException.fromUnexpectedIOE(new java.io.IOException("falha"));
            }
        };
        var logger = new StructuredLogger(mapperQuebrado, new PrintStream(saida, true, StandardCharsets.UTF_8));

        logger.info("evento_teste", "req-1", Map.of());

        assertEquals("{\"level\":\"ERROR\",\"event\":\"log_serialization_failure\"}",
                saida.toString(StandardCharsets.UTF_8).strip());
    }
}
