package com.learnclaudecode.agents;

import com.learnclaudecode.skills.SkillLoader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行时能力配置。
 *
 * StageConfig 决定 Agent 运行时暴露哪些工具和能力开关。
 * Agent 的能力并不是写死在代码里的，而是由运行时配置决定的。
 *
 * 一个 StageConfig 主要回答 3 个问题：
 * 1. 给模型什么 system prompt；
 * 2. 允许模型使用哪些工具；
 * 3. 是否打开 Todo、压缩、后台、团队、自主认领等高级机制。
 */
public record StageConfig(
        String prompt,
        boolean enableTodoNag,
        boolean enableCompression,
        boolean enableBackground,
        boolean enableInbox,
        boolean subagentWritable,
        boolean autonomousTeammates,
        List<Map<String, Object>> tools,
        String systemTemplate
) {
    /**
     * 根据当前工作区与可用技能生成实际生效的 system prompt。
     *
     * @param skillLoader 技能加载器
     * @param workdir 当前工作区路径
     * @return 展开占位符后的 system prompt
     */
    public String systemPrompt(SkillLoader skillLoader, Path workdir) {
        return systemTemplate
                .replace("${WORKDIR}", workdir.toString())
                .replace("${SKILLS}", skillLoader.getDescriptions());
    }

    /**
     * 返回最基础的文件与命令工具集合。
     *
     * @return 基础工具定义列表
     */
    public static List<Map<String, Object>> baseTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(tool("bash", "Run a shell command.", Map.of("type", "object", "properties", Map.of("command", Map.of("type", "string")), "required", List.of("command"))));
        tools.add(tool("read_file", "Read file contents.", Map.of("type", "object", "properties", Map.of("path", Map.of("type", "string"), "limit", Map.of("type", "integer")), "required", List.of("path"))));
        tools.add(tool("write_file", "Write content to file.", Map.of("type", "object", "properties", Map.of("path", Map.of("type", "string"), "content", Map.of("type", "string")), "required", List.of("path", "content"))));
        tools.add(tool("edit_file", "Replace exact text in file.", Map.of("type", "object", "properties", Map.of("path", Map.of("type", "string"), "old_text", Map.of("type", "string"), "new_text", Map.of("type", "string")), "required", List.of("path", "old_text", "new_text"))));
        return tools;
    }

    /**
     * 构建完整能力版本的配置。
     *
     * @return 完整能力配置
     */
    public static StageConfig sFull() {
        List<Map<String, Object>> tools = new ArrayList<>(baseTools());

        // Todo 规划
        tools.add(tool("todo", "Update task list. Track progress on multi-step tasks.", Map.of("type", "object", "properties", Map.of("items", Map.of("type", "array", "items", Map.of("type", "object"))), "required", List.of("items"))));

        // 子代理
        tools.add(tool("task", "Spawn a subagent with fresh context.", Map.of("type", "object", "properties", Map.of("prompt", Map.of("type", "string"), "description", Map.of("type", "string")), "required", List.of("prompt"))));

        // 技能加载
        tools.add(tool("load_skill", "Load specialized knowledge by name.", Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string")), "required", List.of("name"))));

        // 上下文压缩
        tools.add(tool("compact", "Trigger manual conversation compression.", Map.of("type", "object", "properties", Map.of("focus", Map.of("type", "string")))));

        // 任务系统
        tools.add(tool("task_create", "Create a new task.", Map.of("type", "object", "properties", Map.of("subject", Map.of("type", "string"), "description", Map.of("type", "string")), "required", List.of("subject"))));
        tools.add(tool("task_update", "Update task status or dependencies.", Map.of("type", "object", "properties", Map.of("task_id", Map.of("type", "integer"), "status", Map.of("type", "string"), "addBlockedBy", Map.of("type", "array"), "addBlocks", Map.of("type", "array")), "required", List.of("task_id"))));
        tools.add(tool("task_list", "List all tasks.", Map.of("type", "object", "properties", Map.of())));
        tools.add(tool("task_get", "Get task details.", Map.of("type", "object", "properties", Map.of("task_id", Map.of("type", "integer")), "required", List.of("task_id"))));

        // 后台任务
        tools.add(tool("background_run", "Run command in background thread.", Map.of("type", "object", "properties", Map.of("command", Map.of("type", "string"), "timeout", Map.of("type", "integer")), "required", List.of("command"))));
        tools.add(tool("check_background", "Check background task status.", Map.of("type", "object", "properties", Map.of("task_id", Map.of("type", "string")))));

        // 多 Agent 协作
        tools.add(tool("spawn_teammate", "Spawn a persistent teammate.", Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string"), "role", Map.of("type", "string"), "prompt", Map.of("type", "string")), "required", List.of("name", "role", "prompt"))));
        tools.add(tool("list_teammates", "List all teammates.", Map.of("type", "object", "properties", Map.of())));
        tools.add(tool("send_message", "Send a message to a teammate.", Map.of("type", "object", "properties", Map.of("to", Map.of("type", "string"), "content", Map.of("type", "string"), "msg_type", Map.of("type", "string")), "required", List.of("to", "content"))));
        tools.add(tool("read_inbox", "Read and drain the lead inbox.", Map.of("type", "object", "properties", Map.of())));
        tools.add(tool("broadcast", "Send message to all teammates.", Map.of("type", "object", "properties", Map.of("content", Map.of("type", "string")), "required", List.of("content"))));

        // 团队协议
        tools.add(tool("shutdown_request", "Request teammate shutdown.", Map.of("type", "object", "properties", Map.of("teammate", Map.of("type", "string")), "required", List.of("teammate"))));
        tools.add(tool("plan_approval", "Approve or reject a teammate plan.", Map.of("type", "object", "properties", Map.of("request_id", Map.of("type", "string"), "approve", Map.of("type", "boolean"), "feedback", Map.of("type", "string")), "required", List.of("request_id", "approve"))));

        // 自治队友
        tools.add(tool("claim_task", "Claim a task from the board.", Map.of("type", "object", "properties", Map.of("task_id", Map.of("type", "integer")), "required", List.of("task_id"))));
        tools.add(tool("idle", "Enter idle state.", Map.of("type", "object", "properties", Map.of())));

        // Worktree 隔离
        tools.add(tool("worktree_create", "Create a task-bound worktree lane.", Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string"), "task_id", Map.of("type", "integer")), "required", List.of("name", "task_id"))));
        tools.add(tool("worktree_list", "List all worktrees.", Map.of("type", "object", "properties", Map.of())));
        tools.add(tool("worktree_remove", "Remove a worktree.", Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string"), "keep", Map.of("type", "boolean")), "required", List.of("name"))));
        tools.add(tool("worktree_events", "List recent worktree lifecycle events.", Map.of("type", "object", "properties", Map.of("limit", Map.of("type", "integer")))));

        return new StageConfig("s_full", true, true, true, true, true, true,
                dedupe(tools),
                "You are a coding agent at ${WORKDIR}. Use tools to solve tasks. Prefer task_create/task_update/task_list for multi-step work. Use TodoWrite for short checklists. Use task for subagent delegation. Use load_skill for specialized knowledge. Skills: ${SKILLS}");
    }

    /**
     * 按 messages API 约定构造单个工具定义。
     *
     * @param name 工具名
     * @param description 工具描述
     * @param schema 输入 schema
     * @return 工具定义映射
     */
    private static Map<String, Object> tool(String name, String description, Map<String, Object> schema) {
        Map<String, Object> tool = new HashMap<>();
        tool.put("name", name);
        tool.put("description", description);
        tool.put("input_schema", schema);
        return tool;
    }

    /**
     * 对工具列表按名字去重。
     *
     * @param tools 原始工具列表
     * @return 去重后的工具列表
     */
    private static List<Map<String, Object>> dedupe(List<Map<String, Object>> tools) {
        Map<String, Map<String, Object>> unique = new HashMap<>();
        for (Map<String, Object> tool : tools) {
            unique.put(String.valueOf(tool.get("name")), tool);
        }
        return new ArrayList<>(unique.values());
    }
}
