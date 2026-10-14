package com.shoplab.api.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Valida respuestas JSON contra los contratos de docs/contracts (JSON Schema draft-04),
 * los mismos que usan las pruebas de API con RestAssured.
 */
public final class ContractAssertions {

    // Gradle ejecuta los tests desde la raíz del proyecto
    private static final Path CONTRACTS_DIR = Path.of("docs", "contracts");
    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V4);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ContractAssertions() {
    }

    /**
     * Falla con AssertionError si el JSON no cumple docs/contracts/{schemaName}.schema.json.
     */
    public static void assertMatchesSchema(String json, String schemaName) {
        Set<ValidationMessage> errors = loadSchema(schemaName).validate(readJson(json));
        if (!errors.isEmpty()) {
            String detail = errors.stream().map(ValidationMessage::getMessage).sorted().collect(Collectors.joining("\n  "));
            throw new AssertionError("JSON does not match " + schemaName + ".schema.json:\n  " + detail + "\nJSON: " + json);
        }
    }

    private static JsonSchema loadSchema(String schemaName) {
        Path file = CONTRACTS_DIR.resolve(schemaName + ".schema.json");
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("Schema not found: " + file.toAbsolutePath());
        }
        try (InputStream in = Files.newInputStream(file)) {
            return FACTORY.getSchema(in);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read schema " + file, e);
        }
    }

    private static JsonNode readJson(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (IOException e) {
            throw new AssertionError("Response is not valid JSON: " + json, e);
        }
    }
}
