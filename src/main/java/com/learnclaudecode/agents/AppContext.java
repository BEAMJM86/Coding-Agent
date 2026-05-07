package com.learnclaudecode.agents;

import com.learnclaudecode.background.BackgroundManager;
import com.learnclaudecode.common.AnthropicClient;
import com.learnclaudecode.common.EnvConfig;
import com.learnclaudecode.common.WorkspacePaths;
import com.learnclaudecode.context.CompressionService;
import com.learnclaudecode.skills.SkillLoader;
import com.learnclaudecode.tasks.TaskManager;
import com.learnclaudecode.tasks.WorktreeManager;
import com.learnclaudecode.team.MessageBus;
import com.learnclaudecode.team.TeammateManager;
import com.learnclaudecode.tools.CommandTools;
import com.learnclaudecode.tools.TodoManager;

/**
 * 应用装配器，集中创建并连接所有共享服务。
 *
 * 负责按”基础能力 -> 扩展机制 -> 运行时”的顺序装配整个 Agent 系统：
 * 1. 读取环境配置（模型、API 地址、工作目录）；
 * 2. 创建基础能力（命令执行、文件读写、路径管理）；
 * 3. 创建扩展机制（Todo、上下文压缩、任务系统、后台任务、团队通信、worktree）；
 * 4. 将所有服务交给 AgentRuntime 统一驱动。
 */
public final class AppContext {
    private final AgentRuntime runtime;

    /**
     * 创建完整的应用上下文并装配所有共享服务。
     */
    public AppContext() {
        // 先装配基础环境与工作区信息，后续所有服务都会依赖这两项。
        EnvConfig env = new EnvConfig();
        WorkspacePaths paths = new WorkspacePaths(env.getWorkdir());

        // 按”基础能力 -> 扩展机制 -> 运行时”的顺序创建共享服务。
        AnthropicClient client = new AnthropicClient(env);
        CommandTools commandTools = new CommandTools(paths);
        TodoManager todoManager = new TodoManager();
        SkillLoader skillLoader = new SkillLoader(paths);
        CompressionService compressionService = new CompressionService(paths, client, 50000, 3);
        TaskManager taskManager = new TaskManager(paths);
        BackgroundManager backgroundManager = new BackgroundManager(paths);
        MessageBus messageBus = new MessageBus(paths);
        TeammateManager teammateManager = new TeammateManager(paths, client, commandTools, messageBus, taskManager);
        WorktreeManager worktreeManager = new WorktreeManager(paths, taskManager);

        // AgentRuntime 是最终的统一执行器。
        // 真正的“用户输入 -> 调模型 -> 模型发起工具调用 -> 本地执行工具 -> 再回给模型”
        // 这条主链路，全部都发生在 AgentRuntime 中。
        this.runtime = new AgentRuntime(client, paths, commandTools, todoManager, skillLoader, compressionService, taskManager, backgroundManager, messageBus, teammateManager, worktreeManager);
    }

    /**
     * 返回已经装配完成的统一 Agent 运行时。
     *
     * @return 共享的 AgentRuntime 实例
     */
    public AgentRuntime runtime() {
        return runtime;
    }
}
