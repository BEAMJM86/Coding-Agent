package com.learnclaudecode.permissions;

import com.learnclaudecode.common.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 信任记忆：持久化用户「总是放行」选择到 settings.json。
 * 两层：内存 Map（会话级 O(1) 匹配）+ 磁盘 JSON（跨会话持久化）。
 *
 * @author BEAM
 */
public class TrustStore {

    private final Path projectConfigPath;
    private final Path globalConfigPath;
    private final Map<String, String> sessionAllow = new ConcurrentHashMap<>();

    public TrustStore(Path workspaceRoot) {
        this.projectConfigPath = workspaceRoot.resolve(".coding-agent").resolve("settings.json");
        String userHome = System.getProperty("user.home");
        this.globalConfigPath = Path.of(userHome, ".coding-agent", "settings.json");
    }

    /**
     * 加载磁盘上的 allow 规则到内存。
     */
    public void load() {
        loadFrom(projectConfigPath);
        loadFrom(globalConfigPath);
    }

    @SuppressWarnings("unchecked")
    private void loadFrom(Path path) {
        if (!Files.exists(path)) return;
        try {
            String json = Files.readString(path);
            Map<String, Object> config = JsonUtils.fromJson(json,
                    new TypeReference<Map<String, Object>>() {});
            Map<String, Object> permissions = (Map<String, Object>) config.getOrDefault("permissions", Map.of());
            List<String> allow = (List<String>) permissions.getOrDefault("allow", List.of());
            for (String rule : allow) {
                sessionAllow.put(rule, "allow");
            }
        } catch (IOException e) {
            // 文件不存在或格式错误，跳过
        }
    }

    /**
     * 检查是否命中 allow 规则（包括交互式选择的「总是放行」）。
     */
    public boolean isAllowed(String toolName, String permissionContent) {
        String key = toolName + "(" + (permissionContent != null ? permissionContent : "*") + ")";
        if (sessionAllow.containsKey(key)) return true;
        for (var entry : sessionAllow.entrySet()) {
            if (matches(entry.getKey(), toolName, permissionContent)) return true;
        }
        return false;
    }

    /**
     * 将「总是放行」写入项目级内存和磁盘。
     * 对 bash 工具只存基命令前缀（如 Get-ChildItem:*），
     * 避免不同参数组合反复触发确认。
     */
    public void addAllow(String toolName, String permissionContent) {
        String normalized = permissionContent;
        if ("bash".equals(toolName) && permissionContent != null && !permissionContent.isEmpty()) {
            String baseCmd = permissionContent.trim().split("\\s+")[0];
            normalized = baseCmd + ":*";
        }
        String key = toolName + "(" + (normalized != null ? normalized : "*") + ")";
        sessionAllow.put(key, "allow");
        writeToDisk(projectConfigPath, toolName, normalized);
    }

    @SuppressWarnings("unchecked")
    private void writeToDisk(Path path, String toolName, String permissionContent) {
        try {
            Files.createDirectories(path.getParent());
            Map<String, Object> config = new LinkedHashMap<>();
            if (Files.exists(path)) {
                String existing = Files.readString(path);
                config = JsonUtils.fromJson(existing, new TypeReference<Map<String, Object>>() {});
            }
            Map<String, Object> permissions = (Map<String, Object>) config.computeIfAbsent("permissions", k -> new LinkedHashMap<>());
            List<String> allow = (List<String>) permissions.computeIfAbsent("allow", k -> new ArrayList<>());
            String entry;
            if (permissionContent != null && !permissionContent.isEmpty()) {
                entry = toolName + "(" + permissionContent + ")";
            } else {
                entry = toolName + "(*)";
            }
            if (!allow.contains(entry)) {
                allow.add(entry);
            }
            Files.writeString(path, JsonUtils.toPrettyJson(config));
        } catch (IOException ignored) {
        }
    }

    private boolean matches(String rule, String toolName, String permissionContent) {
        String content = permissionContent != null ? permissionContent : "";
        if (!rule.startsWith(toolName + "(")) return false;
        String pattern = rule.substring(toolName.length() + 1, rule.length() - 1);
        if (pattern.endsWith(":*")) {
            return content.startsWith(pattern.substring(0, pattern.length() - 2));
        }
        if (pattern.endsWith(" *") || pattern.endsWith("*")) {
            return content.startsWith(pattern.substring(0, pattern.length() - 1).trim());
        }
        return content.equals(pattern);
    }
}
