package com.fampay.scheduler.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Converter
public class JsonToMapConverter implements AttributeConverter<Map, String> {

  ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public Map convertToEntityAttribute(String attribute) {
    if (attribute == null) {
      return new LinkedHashMap<>();
    }
    try {
      return objectMapper.readValue(attribute, LinkedHashMap.class);
    } catch (Exception e) {
      log.error("Convert error while trying to convert string(JSON) to map data structure.", e);
    }
    return new LinkedHashMap<>();
  }

  @Override
  public String convertToDatabaseColumn(Map dbData) {
    if (dbData == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(dbData);
    } catch (JsonProcessingException e) {
      log.error("Could not convert map to json string.", e);
      return null;
    }
  }
}
