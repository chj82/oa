package com.oa.service.system.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

/** 统一处理 Redis 缓存对象与 JSON 字符串之间的转换。 */
@Component
public class RedisCacheJsonCodec {
  private final ObjectMapper objectMapper;

  public RedisCacheJsonCodec(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper.copy();
    this.objectMapper.registerModule(new JavaTimeModule());
    this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  /** 将缓存对象序列化为 JSON 字符串。 */
  public String write(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException | RuntimeException exception) {
      throw new RedisCacheJsonException("Redis缓存JSON序列化失败", exception);
    }
  }

  /** 将 JSON 字符串反序列化为指定缓存类型。 */
  public <T> T read(String json, Class<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (JsonProcessingException | RuntimeException exception) {
      throw new RedisCacheJsonException("Redis缓存JSON反序列化失败", exception);
    }
  }

  /** 将 JSON 字符串解析为树节点。 */
  public JsonNode readTree(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (JsonProcessingException | RuntimeException exception) {
      throw new RedisCacheJsonException("Redis缓存JSON解析失败", exception);
    }
  }

  /** 将 JSON 树节点转换为指定缓存类型。 */
  public <T> T treeToValue(JsonNode node, Class<T> type) {
    try {
      return objectMapper.treeToValue(node, type);
    } catch (JsonProcessingException | RuntimeException exception) {
      throw new RedisCacheJsonException("Redis缓存JSON转换失败", exception);
    }
  }
}
