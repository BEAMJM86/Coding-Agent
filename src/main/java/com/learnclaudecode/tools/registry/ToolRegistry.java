package com.learnclaudecode.tools.registry;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具注册中心，扫描 @AgentTool 注解并管理工具生命周期。
 *
 * @author BEAM
 */
public class ToolRegistry {

    private final Map<String, ToolEntry> entries = new LinkedHashMap<>();

    /**
     * 扫描对象的所有 @AgentTool 方法并注册。
     */
    public void scan(Object toolInstance) {
        for (Method method : toolInstance.getClass().getDeclaredMethods()) {
            if (!method.isAnnotationPresent(AgentTool.class)) continue;
            ToolEntry entry = new ToolEntry(method, toolInstance);
            entries.put(entry.name(), entry);
        }
    }

    /**
     * 按名字获取工具。
     */
    public ToolEntry get(String name) {
        return entries.get(name);
    }

    /**
     * 生成给 Anthropic API 的 tools 数组。
     */
    public List<Map<String, Object>> toToolDefinitions() {
        return entries.values().stream()
                .map(ToolEntry::toDefinition)
                .toList();
    }

    /**
     * 创建子注册表，仅包含指定名称的工具。
     */
    public ToolRegistry subset(Set<String> allowedNames) {
        ToolRegistry sub = new ToolRegistry();
        entries.forEach((name, entry) -> {
            if (allowedNames.contains(name)) {
                sub.entries.put(name, entry);
            }
        });
        return sub;
    }

    /**
     * 创建子注册表，排除指定名称的工具。
     */
    public ToolRegistry excluding(Set<String> excludedNames) {
        ToolRegistry sub = new ToolRegistry();
        entries.forEach((name, entry) -> {
            if (!excludedNames.contains(name)) {
                sub.entries.put(name, entry);
            }
        });
        return sub;
    }

    /**
     * 只读工具集（排除写文件和危险命令）。
     */
    public ToolRegistry readOnly() {
        return excluding(Set.of(
                "bash", "write_file", "edit_file",
                "task_create", "task_update", "background_run",
                "spawn_teammate", "send_message", "broadcast",
                "shutdown_request", "plan_approval", "claim_task",
                "worktree_create", "worktree_remove"
        ));
    }

    /**
     * 返回所有已注册的工具名。
     */
    public Set<String> names() {
        return entries.keySet();
    }
}
