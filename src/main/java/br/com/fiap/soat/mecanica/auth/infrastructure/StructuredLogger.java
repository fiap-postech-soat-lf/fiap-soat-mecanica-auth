package br.com.fiap.soat.mecanica.auth.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.PrintStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Log em JSON numa linha por evento (copiado do -auth do grupo, api/StructuredLogger.java). */
public final class StructuredLogger {
    private final ObjectMapper objectMapper;
    private final PrintStream saida;

    public StructuredLogger() {
        this(System.out);
    }

    public StructuredLogger(PrintStream saida) {
        this(new ObjectMapper(), saida);
    }

    StructuredLogger(ObjectMapper objectMapper, PrintStream saida) {
        this.objectMapper = objectMapper;
        this.saida = saida;
    }

    public void info(String event, String correlationId, Map<String, Object> fields) {
        log("INFO", event, correlationId, fields);
    }

    public void warn(String event, String correlationId, Map<String, Object> fields) {
        log("WARN", event, correlationId, fields);
    }

    private void log(String level, String event, String correlationId, Map<String, Object> fields) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("timestamp", Instant.now().toString());
        message.put("level", level);
        message.put("event", event);
        message.put("correlationId", correlationId);
        message.putAll(fields);
        try {
            saida.println(objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException exception) {
            saida.println("{\"level\":\"ERROR\",\"event\":\"log_serialization_failure\"}");
        }
    }
}
