package com.learnclaudecode.tools.registry;

import java.util.Map;

/**
 * 一次工具调用，包含调用 id、工具名和模型传入的输入参数。
 *
 * @author BEAM
 */
public record ToolCall(String id, String name, Map<String, Object> input) {

    public String input(String key) {
        return String.valueOf(input.getOrDefault(key, ""));
    }

    public int inputInt(String key, int defaultValue) {
        Object value = input.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
