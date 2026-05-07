package com.learnclaudecode.model;

/**
 * 对话消息，content 保持为 Object 以兼容文本或结构化 tool_result 列表。
 *
 * @author BEAM
 */
public record ChatMessage(String role, Object content) {
}
