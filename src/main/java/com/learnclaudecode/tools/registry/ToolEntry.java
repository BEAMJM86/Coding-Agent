package com.learnclaudecode.tools.registry;

import com.learnclaudecode.common.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单个工具的运行时条目，持有方法引用，通过反射调用。
 *
 * @author BEAM
 */
@Slf4j
public class ToolEntry {

    private final String name;
    private final String description;
    private final String[] required;
    private final String[] paramNames;
    private final String[] paramDescriptions;
    private final Class<?>[] paramTypes;
    private final Object instance;
    private final Method method;

    public ToolEntry(Method method, Object instance) {
        AgentTool ann = method.getAnnotation(AgentTool.class);
        this.name = ann.name().isEmpty() ? method.getName() : ann.name();
        this.description = ann.description();
        this.instance = instance;
        this.method = method;

        Parameter[] parameters = method.getParameters();
        this.paramNames = new String[parameters.length];
        this.paramTypes = new Class<?>[parameters.length];
        this.paramDescriptions = new String[parameters.length];
        List<String> requiredList = new ArrayList<>(List.of(ann.required()));
        for (int i = 0; i < parameters.length; i++) {
            this.paramNames[i] = parameters[i].getName();
            this.paramTypes[i] = parameters[i].getType();
            AgentToolParam paramAnn = parameters[i].getAnnotation(AgentToolParam.class);
            if (paramAnn != null) {
                this.paramDescriptions[i] = paramAnn.description();
                if (paramAnn.required() && !requiredList.contains(this.paramNames[i])) {
                    requiredList.add(this.paramNames[i]);
                }
            } else {
                this.paramDescriptions[i] = "";
            }
        }
        this.required = requiredList.toArray(new String[0]);
    }

    public String name() {
        return name;
    }

    public Map<String, Object> toDefinition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (int i = 0; i < paramNames.length; i++) {
            Map<String, Object> prop = new LinkedHashMap<>();
            Object schema = javaTypeToSchema(paramTypes[i]);
            if (schema instanceof Map<?, ?> map) {
                map.forEach((k, v) -> prop.put(String.valueOf(k), v));
            } else {
                prop.put("type", schema);
            }
            if (!paramDescriptions[i].isEmpty()) {
                prop.put("description", paramDescriptions[i]);
            }
            properties.put(paramNames[i], prop);
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (required.length > 0) {
            schema.put("required", List.of(required));
        }

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", name);
        tool.put("description", description);
        tool.put("input_schema", schema);
        return tool;
    }

    public String invoke(Map<String, Object> input) {
        Object[] args = new Object[paramNames.length];
        for (int i = 0; i < paramNames.length; i++) {
            args[i] = convert(input.get(paramNames[i]), paramTypes[i]);
        }
        try {
            Object result = method.invoke(instance, args);
            return result == null ? "" : String.valueOf(result);
        } catch (Exception e) {
            log.error("Tool {} invoke failed: {}", name, e.getMessage(), e);
            Throwable cause = e.getCause();
            return "Error: " + (cause != null ? cause.getMessage() : e.getMessage());
        }
    }

    private Object javaTypeToSchema(Class<?> type) {
        if (type == String.class) return "string";
        if (type == int.class || type == Integer.class || type == long.class || type == Long.class) return "integer";
        if (type == boolean.class || type == Boolean.class) return "boolean";
        if (type == double.class || type == Double.class || type == float.class || type == Float.class) return "number";
        if (List.class.isAssignableFrom(type)) {
            return Map.of("type", "array", "items", Map.of("type", "object"));
        }
        return "string";
    }

    private Object convert(Object value, Class<?> target) {
        if (value == null) {
            if (target == int.class || target == Integer.class) return 0;
            if (target == long.class || target == Long.class) return 0L;
            if (target == boolean.class || target == Boolean.class) return false;
            if (List.class.isAssignableFrom(target)) return List.of();
            return "";
        }
        if (target == String.class) return String.valueOf(value);
        if (target == int.class || target == Integer.class) {
            if (value instanceof Number number) return number.intValue();
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        if (target == boolean.class || target == Boolean.class) {
            if (value instanceof Boolean b) return b;
            return Boolean.parseBoolean(String.valueOf(value));
        }
        if (target == long.class || target == Long.class) {
            if (value instanceof Number number) return number.longValue();
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        if (List.class.isAssignableFrom(target)) {
            if (value instanceof List<?> list) {
                if (!list.isEmpty() && !(list.get(0) instanceof Map)) {
                    if (list.get(0) instanceof String) {
                        List<Map<String, Object>> parsed = tryParseJsonList(list);
                        if (parsed != null) return parsed;
                    }
                    return list.stream()
                            .map(e -> (Object) Map.of("text", String.valueOf(e), "status", "pending"))
                            .toList();
                }
                return list;
            }
            if (value instanceof String str && !str.isBlank()) {
                List<Map<String, Object>> parsed = tryParseJsonList(str);
                if (parsed != null) return parsed;
            }
            return List.of(Map.of("text", String.valueOf(value), "status", "pending"));
        }
        return String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> tryParseJsonList(Object source) {
        try {
            String json;
            if (source instanceof String str) {
                json = str.trim();
            } else if (source instanceof List<?> list) {
                json = "[" + String.join(",", list.stream().map(String::valueOf).toList()) + "]";
            } else {
                return null;
            }
            if (json.startsWith("[")) {
                return JsonUtils.fromJson(json, new TypeReference<List<Map<String, Object>>>() {});
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
