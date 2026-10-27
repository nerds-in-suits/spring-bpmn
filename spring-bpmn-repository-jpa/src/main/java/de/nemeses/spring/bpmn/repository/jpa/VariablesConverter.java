package de.nemeses.spring.bpmn.repository.jpa;

import java.util.Map;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stores process variables as a JSON object.
 */
@Converter
class VariablesConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(final Map<String, Object> variables) {
        try {
            return VariablesConverter.MAPPER.writeValueAsString(variables == null ? Map.of() : variables);
        } catch (final JacksonException e) {
            throw new IllegalArgumentException("Cannot serialize process variables.", e);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(final String json) {
        try {
            return json == null ? Map.of() : VariablesConverter.MAPPER.readValue(json, VariablesConverter.TYPE);
        } catch (final JacksonException e) {
            throw new IllegalArgumentException("Cannot deserialize process variables.", e);
        }
    }
}
