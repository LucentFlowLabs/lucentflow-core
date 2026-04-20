package com.lucentflow.common.entity.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JPA converter for persisting risk reason maps as JSON payloads.
 *
 * @author ArchLucent
 * @since 1.0
 */
@Slf4j
@Converter(autoApply = false)
public class RiskReasonsJsonConverter implements AttributeConverter<Map<String, Integer>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Integer>> MAP_TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, Integer> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "{}";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.warn("[RISK-JSON] Failed to serialize risk reasons: {}", e.getMessage());
            return "{}";
        }
    }

    @Override
    public Map<String, Integer> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return OBJECT_MAPPER.readValue(dbData, MAP_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("[RISK-JSON] Failed to deserialize risk reasons: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }
}
