package com.learnclaudecode.permissions;

import com.learnclaudecode.common.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 规则引擎：从 JSON 配置文件加载 deny/ask/allow 规则并匹配。
 * 优先级：项目级 deny > 全局 deny > 项目级 ask > 全局 ask。
 *
 * @author BEAM
 */
public class PermissionRules {

    private final List<Rule> denyRules = new ArrayList<>();
    private final List<Rule> askRules = new ArrayList<>();

    /**
     * 一条权限规则。
     */
    public record Rule(String behavior, String toolName, String content) {
        public boolean matches(String tool, String permissionContent) {
            if (!this.toolName.equals(tool)) return false;
            if (content == null || content.equals("*")) return true;
            String value = permissionContent != null ? permissionContent : "";
            if (content.endsWith(":*")) {
                return value.startsWith(content.substring(0, content.length() - 2));
            }
            if (content.endsWith(" *") || content.endsWith("*")) {
                return value.startsWith(content.substring(0, content.length() - 1).trim());
            }
            return content.equals(value);
        }
    }

    public PermissionRules(Path projectConfig, Path globalConfig) {
        load(projectConfig);
        load(globalConfig);
    }

    @SuppressWarnings("unchecked")
    private void load(Path path) {
        if (!Files.exists(path)) return;
        try {
            String json = Files.readString(path);
            Map<String, Object> config = JsonUtils.fromJson(json,
                    new TypeReference<Map<String, Object>>() {});
            Map<String, Object> permissions = (Map<String, Object>) config.getOrDefault("permissions", Map.of());
            List<String> deny = (List<String>) permissions.getOrDefault("deny", List.of());
            List<String> ask = (List<String>) permissions.getOrDefault("ask", List.of());
            for (String entry : deny) {
                var r = parse(entry, "deny");
                if (r != null) denyRules.add(r);
            }
            for (String entry : ask) {
                var r = parse(entry, "ask");
                if (r != null) askRules.add(r);
            }
        } catch (IOException ignored) {
        }
    }

    private Rule parse(String entry, String behavior) {
        int parenIdx = entry.indexOf('(');
        if (parenIdx < 0 || !entry.endsWith(")")) return null;
        String toolName = entry.substring(0, parenIdx);
        String content = entry.substring(parenIdx + 1, entry.length() - 1);
        return new Rule(behavior, toolName, content);
    }

    /**
     * 获取匹配工具和参数的 deny 规则。
     */
    public Rule getDenyRule(String toolName, String permissionContent) {
        for (Rule rule : denyRules) {
            if (rule.matches(toolName, permissionContent)) return rule;
        }
        return null;
    }

    /**
     * 获取匹配工具和参数的 ask 规则。
     */
    public Rule getAskRule(String toolName, String permissionContent) {
        for (Rule rule : askRules) {
            if (rule.matches(toolName, permissionContent)) return rule;
        }
        return null;
    }
}
