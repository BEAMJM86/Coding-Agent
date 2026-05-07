package com.learnclaudecode.agents;

import com.learnclaudecode.background.BackgroundManager;
import com.learnclaudecode.common.AnthropicClient;
import com.learnclaudecode.common.EnvConfig;
import com.learnclaudecode.common.WorkspacePaths;
import com.learnclaudecode.context.CompressionService;
import com.learnclaudecode.tasks.TaskManager;
import com.learnclaudecode.tasks.WorktreeManager;
import com.learnclaudecode.team.MessageBus;
import com.learnclaudecode.team.TeammateManager;
import com.learnclaudecode.tools.*;
import com.learnclaudecode.tools.hooks.AuditHook;
import com.learnclaudecode.tools.hooks.OutputTruncator;
import com.learnclaudecode.tools.hooks.PermissionHook;
import com.learnclaudecode.tools.registry.ToolExecutor;
import com.learnclaudecode.tools.registry.ToolRegistry;

import java.util.List;
import java.util.Map;

/**
 * 应用装配器，创建所有共享服务并装配成运行时。
 *
 * @author BEAM
 */
public final class AppContext {
    private final AgentRuntime runtime;

    public AppContext() {
        // 基础环境
        EnvConfig env = new EnvConfig();
        WorkspacePaths paths = new WorkspacePaths(env.getWorkdir());
        AnthropicClient client = new AnthropicClient(env);

        // 内部服务层（Manager）
        CommandTools commandTools = new CommandTools(paths);
        TodoManager todoManager = new TodoManager();
        SkillLoader skillLoader = new SkillLoader(paths);
        CompressionService compressionService = new CompressionService(paths, client, 50000, 3);
        TaskManager taskManager = new TaskManager(paths);
        BackgroundManager backgroundManager = new BackgroundManager(paths);
        MessageBus messageBus = new MessageBus(paths);
        TeammateManager teammateManager = new TeammateManager(paths, client, commandTools, messageBus, taskManager);
        WorktreeManager worktreeManager = new WorktreeManager(paths, taskManager);

        // 薄包装层（Tools，暴露给 LLM）
        TaskTools taskTools = new TaskTools(taskManager);
        WorktreeTools worktreeTools = new WorktreeTools(worktreeManager);
        BackgroundTools backgroundTools = new BackgroundTools(backgroundManager);
        TeammateTools teammateTools = new TeammateTools(teammateManager);
        MessageBusTools messageBusTools = new MessageBusTools(messageBus, teammateManager);
        // 工具注册
        ToolRegistry registry = new ToolRegistry();
        registry.scan(commandTools);      // bash, read_file, write_file, edit_file
        registry.scan(todoManager);       // todo
        registry.scan(skillLoader);       // load_skill
        registry.scan(taskTools);         // task_create, task_get, task_update, task_list, claim_task
        registry.scan(worktreeTools);     // worktree_create, worktree_list, worktree_remove, worktree_events
        registry.scan(backgroundTools);   // background_run, check_background
        registry.scan(teammateTools);     // spawn_teammate, list_teammates, shutdown_request, plan_approval
        registry.scan(messageBusTools);   // send_message, read_inbox, broadcast


        // hook 链配置
        ToolExecutor toolExecutor = new ToolExecutor(registry,
                List.of(new AuditHook(), new OutputTruncator(50000)),
                Map.of("bash", List.of(new PermissionHook()))
        );

        // 运行时装配
        this.runtime = new AgentRuntime(client, paths, toolExecutor, registry,
                compressionService, todoManager, skillLoader,
                backgroundManager, messageBus);
    }

    public AgentRuntime runtime() {
        return runtime;
    }
}
