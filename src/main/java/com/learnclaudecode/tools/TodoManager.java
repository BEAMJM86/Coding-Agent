package com.learnclaudecode.tools;

import com.learnclaudecode.model.TodoItem;
import com.learnclaudecode.tools.registry.AgentTool;
import com.learnclaudecode.tools.registry.AgentToolParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Todo 管理器，支持不同字段风格。
 *
 * @author BEAM
 */
public class TodoManager {
    private List<Map<String, Object>> items = new ArrayList<>();

    @AgentTool(name = "todo", description = "Track YOUR OWN work as a personal checklist. Use when you (the lead agent) are doing the work yourself. Each item: text (description), status (pending/in_progress/completed). For delegating work to teammates, use task_create to post tasks on the shared board instead.", required = {"todos"})
    public String update(
            @AgentToolParam(description = "Array of todo objects. Each object must have: text (task description), status (pending/in_progress/completed). Optional: id (auto-assigned if omitted).") List<Map<String, Object>> todos) {
        if (todos.size() > 20) {
            throw new IllegalArgumentException("Max 20 todos allowed");
        }
        int inProgressCount = 0;
        List<Map<String, Object>> validated = new ArrayList<>();
        for (int i = 0; i < todos.size(); i++) {
            Map<String, Object> item = todos.get(i);
            String status = String.valueOf(item.getOrDefault("status", "pending")).toLowerCase();
            String text = String.valueOf(item.getOrDefault("text", item.getOrDefault("content", ""))).trim();
            String id = String.valueOf(item.getOrDefault("id", i + 1));
            String activeForm = String.valueOf(item.getOrDefault("activeForm", text)).trim();
            if (text.isBlank()) {
                throw new IllegalArgumentException("Todo text/content required");
            }
            if (!List.of("pending", "in_progress", "completed").contains(status)) {
                throw new IllegalArgumentException("Invalid status: " + status);
            }
            if ("in_progress".equals(status)) {
                inProgressCount++;
            }
            validated.add(Map.of(
                    "id", id,
                    "text", text,
                    "status", status,
                    "activeForm", activeForm,
                    "content", text
            ));
        }
        if (inProgressCount > 1) {
            throw new IllegalArgumentException("Only one task can be in_progress at a time");
        }
        this.items = validated;
        return render();
    }

    /**
     * 判断是否仍有未完成的 Todo。
     *
     * @return 存在未完成项时返回 true
     */
    public boolean hasOpenItems() {
        return items.stream().anyMatch(item -> !"completed".equals(item.get("status")));
    }

    /**
     * 将 Todo 列表渲染为文本看板。
     *
     * @return Todo 文本表示
     */
    public String render() {
        if (items.isEmpty()) {
            return "No todos.";
        }
        List<String> lines = new ArrayList<>();
        int done = 0;
        for (Map<String, Object> item : items) {
            String status = String.valueOf(item.get("status"));
            String marker = switch (status) {
                case "completed" -> "[√]";
                case "in_progress" -> "[>]";
                default -> "[ ]";
            };
            if ("completed".equals(status)) {
                done++;
            }
            lines.add(marker + " #" + item.get("id") + ": " + item.get("text"));
        }
        lines.add("\n(" + done + "/" + items.size() + " completed)");
        return String.join("\n", lines);
    }
}
