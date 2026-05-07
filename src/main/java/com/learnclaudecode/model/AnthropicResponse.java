package com.learnclaudecode.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * Claude messages API 响应的轻量映射。
 *
 * @author BEAM
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnthropicResponse(String stop_reason, List<Map<String, Object>> content) {
}
