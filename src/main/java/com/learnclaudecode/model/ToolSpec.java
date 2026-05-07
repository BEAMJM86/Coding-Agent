package com.learnclaudecode.model;

import java.util.Map;

/**
 * 工具定义，对齐 Claude messages API 的工具描述结构。
 *
 * @author BEAM
 */
public record ToolSpec(String name, String description, Map<String, Object> input_schema) {
}
